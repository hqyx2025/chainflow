# ChainFlow MVP 架构设计

## 1. 目标与边界

第一阶段交付一条可演示、可测试的微服务业务闭环：

`供应商/SKU 建档 → 创建采购单 → 审批 → 入库 → 库存可用 → 创建销售订单 → 库存预占 → 拣货/发货 → 完成`

仓库采用 Maven 多模块微服务部署：网关、认证、采购、库存、订单、履约分别为可独立启动的 Spring Boot 进程。每个业务服务共享 MySQL 8 实例中的独立表、Redis 和 Nacos 注册配置；服务之间通过 REST 调用，后续可把关键写操作切换到 RocketMQ/Seata 事务消息。这样既能直接演示微服务架构，也保留了逐步增强一致性的空间。

暂不纳入第一阶段：多租户、复杂计价/促销、跨仓调拨、供应商门户、Kubernetes 应用编排。RocketMQ、Seata、Elasticsearch 已纳入 Docker Compose 基础设施和配置边界，业务事件会在后续迭代替换当前同步调用。订单第一版包含 `PENDING_PAYMENT → PAID` 支付状态，支付网关本身使用演示接口。

## 2. 技术与运行约束

| 项目 | 约定 |
| --- | --- |
| 语言/运行时 | Java 17 |
| Web/依赖注入 | Spring Boot 3.x、Spring MVC、Bean Validation |
| 数据访问 | MyBatis 注解 Mapper，库存扣减使用带数量条件的原子 SQL |
| 数据库 | MySQL 8.0，所有业务表使用 InnoDB、`utf8mb4` |
| 缓存 | Redis；缓存只用于读优化和短期状态，MySQL 是库存与订单事实来源 |
| 认证 | JWT Access Token，过期时间建议 2 小时；密码使用 BCrypt |
| 接口 | REST JSON，路径统一 `/api/v1`，时间使用 ISO-8601/UTC 存储 |
| 服务发现/网关 | Spring Cloud Gateway + Nacos Discovery；本地默认关闭 Nacos，Compose 环境可通过 `NACOS_ENABLED=true` 开启 |
| 部署 | Docker Compose 提供 MySQL、Redis、Nacos、RocketMQ、Elasticsearch；应用服务支持本地 Maven 启动 |

建议包结构如下，`common` 只放跨模块的稳定基础能力：

```text
com.chainflow
├─ common        # 统一响应、异常、分页、审计、ID/时间工具
├─ auth          # 登录、JWT、用户/角色
├─ gateway       # 统一入口与服务路由
├─ masterdata    # 供应商、SKU、仓库（后续服务）
├─ purchase      # 采购单
├─ inventory     # 库存、库存流水、预占/释放
├─ order         # 销售订单、订单明细
└─ fulfillment   # 拣货、发货、履约状态
```

各服务内部采用 `controller → service → mapper` 分层。Controller 不直接拼接 SQL；状态迁移集中在 Service 中校验。库存预占使用 `available_quantity >= quantity` 条件更新，订单号在预约表上建立唯一约束，保证重复请求幂等。

## 3. 模块边界与责任

| 模块 | 负责 | 不负责 |
| --- | --- | --- |
| auth | 用户登录、JWT 签发、角色鉴权、当前用户 | 业务审批规则 |
| masterdata | 供应商、SKU、仓库及启停状态 | 库存数量、订单状态 |
| procurement | 采购单草稿/提交/审批、按收货数量生成入库动作 | 直接修改销售订单 |
| inventory | 可用/预占/在途数量、库存流水、库存并发控制 | 决定订单是否审批 |
| order | 销售订单及明细、创建/取消、库存预占编排 | 物流轨迹 |
| fulfillment | 拣货、发货、完成及履约单 | 修改库存可用量（通过 inventory 服务完成） |

跨模块交互只通过应用服务接口。例如采购入库调用 `InventoryApplicationService.receive()`，销售订单调用 `InventoryApplicationService.reserve()`；禁止 procurement 直接更新 `inventory_stock`。

## 4. 核心数据模型

所有表建议包含 `id BIGINT UNSIGNED`、`created_at DATETIME(3)`、`updated_at DATETIME(3)`、`created_by BIGINT`、`updated_by BIGINT`、`deleted TINYINT(1) DEFAULT 0`（库存流水和幂等表不做逻辑删除）。金额使用 `DECIMAL(18,2)`，数量使用 `DECIMAL(18,3)`；不要使用浮点数。

### 4.1 认证与主数据

| 表 | 关键字段与约束 |
| --- | --- |
| `sys_user` | `username` 唯一、`password_hash`、`display_name`、`status`（ACTIVE/LOCKED/DISABLED） |
| `sys_role` | `code` 唯一（ADMIN/PROCUREMENT/WAREHOUSE/SALES） |
| `sys_user_role` | `user_id + role_id` 唯一联合键 |
| `supplier` | `code` 唯一、`name`、联系人、`status`（ENABLED/DISABLED） |
| `sku` | `code` 唯一、`name`、`unit`、`purchase_price`、`sale_price`、`status` |
| `warehouse` | `code` 唯一、`name`、地址、`status` |

### 4.2 采购与库存

| 表 | 关键字段与约束 |
| --- | --- |
| `purchase_order` | `order_no` 唯一、`supplier_id`、`status`、`total_amount`、`remark` |
| `purchase_order_item` | `purchase_order_id`、`sku_id`、`ordered_qty`、`received_qty`、`unit_price`；同一采购单内 `sku_id` 唯一 |
| `inventory_stock` | `warehouse_id + sku_id` 唯一、`available_qty`、`reserved_qty`、`in_transit_qty`、`version`；数量均不得为负 |
| `inventory_transaction` | `transaction_no` 唯一、`warehouse_id`、`sku_id`、`type`（RECEIVE/RESERVE/RELEASE/SHIP/ADJUST）、`quantity`、`source_type`、`source_id`、`operator_id`；按 `sku_id, warehouse_id, created_at` 建索引 |

库存数量含义：`available_qty` 可被订单预占；`reserved_qty` 已为订单锁定但尚未发货；`in_transit_qty` 已下采购单但未入库。入库时 `in_transit_qty -= receive_qty`、`available_qty += receive_qty`；预占时 `available_qty -= qty`、`reserved_qty += qty`；发货时 `reserved_qty -= qty`。

### 4.3 销售与履约

| 表 | 关键字段与约束 |
| --- | --- |
| `sales_order` | `order_no` 唯一、`status`、`warehouse_id`、`total_amount`、`customer_name`、`shipping_address`、`idempotency_key` 唯一（按用户维度） |
| `sales_order_item` | `sales_order_id`、`sku_id`、`ordered_qty`、`reserved_qty`、`unit_price`；同一订单内 `sku_id` 唯一 |
| `fulfillment` | `fulfillment_no` 唯一、`sales_order_id` 唯一、`status`、`tracking_no`、`shipped_at`、`completed_at` |
| `idempotency_record` | `request_key` 唯一、`operator_id`、`operation`、`request_hash`、`response_body`、`status`、`expires_at`；用于创建订单、审批、收货等写接口 |
| `outbox_event` | `event_id` 唯一、`aggregate_type`、`aggregate_id`、`event_type`、`payload`、`status`、`retry_count`；MVP 只落库，后续接 RocketMQ |

建议索引：所有业务单号唯一；订单查询索引 `(status, created_at)`；库存唯一键 `(warehouse_id, sku_id)`；流水查询索引 `(source_type, source_id)`。外键可在开发环境启用，生产由应用和迁移脚本共同保证引用完整性。

## 5. 状态机

状态只能通过下表列出的迁移改变，非法迁移返回 `CF_ORDER_STATE_INVALID`（HTTP 409）。状态更新与业务数据更新必须在同一个数据库事务中完成，并写入审计/流水。

### 5.1 采购单

```text
DRAFT ──submit──> SUBMITTED ──approve──> APPROVED ──receive(partial)──> PARTIALLY_RECEIVED ──receive(all)──> RECEIVED
  │                   │                       │                                      │
  └─cancel────────────┴─reject──> REJECTED    └─cancel────────────────────────────────┘
```

- 只有 `DRAFT` 能编辑明细；`SUBMITTED` 由采购角色提交，`APPROVED` 由采购审批角色审批。
- 收货数量必须满足 `0 < receive_qty <= ordered_qty - received_qty`，全收后进入 `RECEIVED`。
- `REJECTED`、`RECEIVED` 为终态；取消只允许在 `DRAFT` 或 `APPROVED` 且未收货时执行。

### 5.2 销售订单

```text
CREATED ──reserve──> ALLOCATED ──startPick──> PICKING ──ship──> SHIPPED ──complete──> COMPLETED
   │                     │                                      │
   └─cancel──────────────┴─cancel(release)──────────────────────┘
```

- 创建订单只写入 `CREATED`；库存预占成功后才进入 `ALLOCATED`。
- 任一明细库存不足时整笔预占失败，订单保持 `CREATED`，不能出现部分预占。
- `ALLOCATED` 取消需要释放全部预占；`SHIPPED`、`COMPLETED` 不允许取消。

### 5.3 履约单

`CREATED → PICKING → PACKED → SHIPPED → COMPLETED`；`CREATED/PICKING/PACKED` 可取消并回滚未发货预占，`SHIPPED/COMPLETED` 为终态。MVP 不实现拆单，一个销售订单对应一个履约单和一个仓库。

## 6. REST API 清单

统一响应格式：

```json
{"code":"0","message":"OK","data":{},"traceId":"..."}
```

分页请求使用 `page`（从 1 开始）和 `pageSize`（默认 20，最大 100）。写操作支持 `Idempotency-Key` 请求头；同一操作键和请求体不变时返回第一次结果。

### 6.0 第一阶段已实现接口

下面的接口与仓库当前代码一致，均可通过网关访问。除登录和健康检查外，网关要求 `Authorization: Bearer <JWT>`。

| 方法 | 路径 | 服务 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/login` | auth | 使用演示账号签发 JWT |
| POST | `/api/v1/purchases` | purchase | 创建 `CREATED` 采购单 |
| GET | `/api/v1/purchases` | purchase | 查询采购单列表 |
| POST | `/api/v1/purchases/{id}/receive` | purchase | 调库存入库，采购单变为 `RECEIVED`，重复调用幂等 |
| GET | `/api/v1/inventory/{sku}` | inventory | 查询可用/预占库存 |
| POST | `/api/v1/inventory/receipts` | inventory | 按采购单入库，采购单号唯一幂等 |
| POST | `/api/v1/inventory/reservations` | inventory | 按订单号预占，库存不足返回 `INV-001` |
| POST | `/api/v1/orders` | order | 创建 `PENDING_PAYMENT` 订单 |
| POST | `/api/v1/orders/{id}/pay` | order | 预占库存、标记 `PAID`，幂等创建履约单并进入 `FULFILLING` |
| GET | `/api/v1/orders/{id}` | order | 查询订单状态 |
| POST | `/api/v1/fulfillments` | fulfillment | 按订单号幂等创建履约单 |
| PATCH | `/api/v1/fulfillments/{id}/status?status=...` | fulfillment | 推进履约状态 |

当前实现用单 SKU/单仓库模型演示闭环；下方各节描述的多 SKU、角色权限、分页和拆单能力属于后续迭代设计。

### 6.1 认证

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/login` | public | 用户名密码登录，返回 JWT |
| GET | `/api/v1/auth/me` | login | 当前用户与角色 |
| POST | `/api/v1/auth/logout` | login | 将 JWT `jti` 写入 Redis 黑名单直至过期 |

### 6.2 主数据

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET/POST | `/api/v1/suppliers` | login / PROCUREMENT | 查询/创建供应商 |
| GET/PATCH | `/api/v1/suppliers/{id}` | login / PROCUREMENT | 详情/更新启停状态与信息 |
| GET/POST | `/api/v1/skus` | login / ADMIN | 查询/创建 SKU |
| GET/PATCH | `/api/v1/skus/{id}` | login / ADMIN | 详情/更新 SKU |
| GET/POST | `/api/v1/warehouses` | login / WAREHOUSE | 查询/创建仓库 |

### 6.3 采购

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/v1/purchase-orders` | PROCUREMENT | 创建草稿 |
| GET | `/api/v1/purchase-orders` | login | 按状态/供应商/时间分页 |
| GET | `/api/v1/purchase-orders/{id}` | login | 详情及收货进度 |
| POST | `/api/v1/purchase-orders/{id}/submit` | PROCUREMENT | DRAFT → SUBMITTED |
| POST | `/api/v1/purchase-orders/{id}/approve` | PROCUREMENT/ADMIN | SUBMITTED → APPROVED |
| POST | `/api/v1/purchase-orders/{id}/reject` | PROCUREMENT/ADMIN | SUBMITTED → REJECTED |
| POST | `/api/v1/purchase-orders/{id}/receive` | WAREHOUSE | 部分/全部收货并增加库存 |
| POST | `/api/v1/purchase-orders/{id}/cancel` | PROCUREMENT/ADMIN | 允许状态下取消 |

收货请求体示例：`{"warehouseId":1,"items":[{"skuId":10,"quantity":5}]}`。服务端按采购明细校验数量，禁止客户端直接提交 `receivedQty` 总量。

### 6.4 库存

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/v1/inventory/stocks` | login | 按仓库/SKU 查询可用、预占、在途 |
| GET | `/api/v1/inventory/transactions` | WAREHOUSE/ADMIN | 查询库存流水 |
| POST | `/api/v1/inventory/adjustments` | WAREHOUSE/ADMIN | 盘盈盘亏，必须填写原因 |
| POST | `/api/v1/inventory/{warehouseId}/{skuId}/reserve` | internal | 订单模块预占 |
| POST | `/api/v1/inventory/{warehouseId}/{skuId}/release` | internal | 取消订单释放预占 |

预占和释放主要通过模块服务调用，若暂时暴露 HTTP 仅允许内网并要求服务身份；不接受前端直接调用。

### 6.5 销售订单与履约

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/v1/orders` | SALES/ADMIN | 创建订单并校验 SKU、仓库 |
| GET | `/api/v1/orders` | login | 分页查询订单 |
| GET | `/api/v1/orders/{id}` | login | 订单、明细、履约信息 |
| POST | `/api/v1/orders/{id}/allocate` | SALES/ADMIN | 调用库存预占，CREATED → ALLOCATED |
| POST | `/api/v1/orders/{id}/cancel` | SALES/ADMIN | 取消并释放预占 |
| POST | `/api/v1/fulfillments/{id}/start-pick` | WAREHOUSE | CREATED → PICKING |
| POST | `/api/v1/fulfillments/{id}/pack` | WAREHOUSE | PICKING → PACKED |
| POST | `/api/v1/fulfillments/{id}/ship` | WAREHOUSE | 扣减预占并记录运单 |
| POST | `/api/v1/fulfillments/{id}/complete` | WAREHOUSE/ADMIN | SHIPPED → COMPLETED |

## 7. 事务、并发与可靠性

1. **数据库事务**：当前采购收货、库存入库/预占、订单支付和履约创建分别在各自服务事务内完成；订单支付保留 `@GlobalTransactional` 边界，启用 Seata 后再扩展为跨服务事务。生产版本仍需补充 outbox 与补偿任务。
2. **库存并发**：当前库存更新使用 `available_quantity >= quantity` 条件原子 SQL；库存入库和预占都在事务内执行。高并发版本再评估版本号、Redis Lua 或分布式锁。
3. **幂等**：采购入库以 `purchase_id` 唯一键、库存预占以 `order_id` 唯一键、履约创建以 `order_id` 唯一键实现重复请求安全；通用 `Idempotency-Key` 表属于后续迭代。
4. **缓存**：SKU 和仓库启停信息可缓存 5 分钟；库存查询可短缓存 2 秒但写入后主动删除。任何库存决策必须读取事务内 MySQL 数据。
5. **消息**：订单服务可选发布 `chainflow-order-paid` RocketMQ 事件，默认关闭；后续用 outbox 保证数据库状态与消息发送的一致性。
6. **审计与追踪**：每个请求生成 `traceId`；状态变更记录操作人、旧状态、新状态、原因。日志不得打印密码、JWT、完整收货地址等敏感字段。

## 8. 错误码与鉴权

| 错误码 | HTTP | 场景 |
| --- | --- | --- |
| `AUTH_UNAUTHORIZED` | 401 | 缺少/过期 JWT |
| `AUTH_FORBIDDEN` | 403 | 角色无权操作 |
| `VALIDATION_ERROR` | 400 | 参数或数量格式错误 |
| `RESOURCE_NOT_FOUND` | 404 | 供应商、SKU、订单不存在 |
| `CF_ORDER_STATE_INVALID` | 409 | 状态机不允许迁移 |
| `CF_STOCK_INSUFFICIENT` | 409 | 可用库存不足 |
| `CF_CONCURRENT_MODIFICATION` | 409 | 库存版本冲突 |
| `CF_IDEMPOTENCY_REUSED` | 409 | 幂等键对应不同请求体 |
| `BUSINESS_RULE_VIOLATION` | 422 | 业务规则不满足 |

Spring Security 过滤器解析 `Authorization: Bearer <token>`，在 `SecurityContext` 放入用户 ID 与角色。方法级使用 `@PreAuthorize`；匿名只开放登录和健康检查。登出将 `jti` 写入 Redis，TTL 等于 JWT 剩余有效期。

## 9. MVP 验收顺序

1. 登录并按角色访问受保护接口；参数校验和统一错误响应可用。
2. 创建供应商、SKU、仓库和采购单，完成提交、审批、部分/全部收货。
3. 收货后库存数量、库存流水和采购状态一致；重复收货请求不会重复入库。
4. 创建销售订单，库存不足时整单不预占；库存足够时可预占、取消释放。
5. 完成拣货、打包、发货、完成，库存预占正确扣减。
6. 使用并发测试验证同一 SKU 的预占不会产生负库存；集成测试覆盖每个非法状态迁移。
7. Docker Compose 能启动应用、MySQL、Redis；README 给出初始化账号和最小演示脚本。

后续拆分微服务时，优先把 `inventory` 与 `order` 作为独立服务候选；拆分前必须先固定上述状态、错误码、幂等语义和 outbox 事件格式。
