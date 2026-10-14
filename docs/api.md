# ChainFlow 接口契约

[`openapi.yaml`](openapi.yaml) 是以网关 `http://localhost:8080` 为入口的 OpenAPI 3.0 契约，覆盖登录、注销、采购、库存、订单与履约。可将文件导入支持 OpenAPI 的 IDE、Swagger Editor、Postman 或 Apifox 查看、生成请求或客户端代码。它是仓库中的静态文件，应用不提供 `/swagger-ui` 或 `/v3/api-docs` 页面。

## 认证与角色

`POST /api/v1/auth/login` 和 `GET /api/v1/auth/health` 可匿名访问；其他接口需要 `Authorization: Bearer <JWT>`。登录默认签发 `SUPPLY_CHAIN_OPERATOR` 角色。注销会将 Token 的 `jti` 存入 Redis，记录在剩余有效期结束后过期。

网关允许所有已认证用户读取业务数据，并按 JWT 的 `role` claim 限制业务写操作（POST、PUT、PATCH、DELETE）。角色会去除首尾空格并转为大写：

| 写操作 | 允许角色 |
| --- | --- |
| 创建采购单 | `PROCUREMENT`、`ADMIN`、`SUPPLY_CHAIN_OPERATOR` |
| 采购收货 | `WAREHOUSE`、`ADMIN`、`SUPPLY_CHAIN_OPERATOR` |
| 库存预占、入库 | `WAREHOUSE`、`ADMIN`、`SUPPLY_CHAIN_OPERATOR` |
| 创建订单、订单支付 | `SALES`、`ADMIN`、`SUPPLY_CHAIN_OPERATOR` |
| 创建履约单、更新履约状态 | `WAREHOUSE`、`ADMIN`、`SUPPLY_CHAIN_OPERATOR` |

缺失或未知角色可读取业务数据，业务写操作返回 HTTP 403。认证注销只要求有效 Token。该策略在网关执行，业务服务间请求通过 Compose 的内部网络调用。

## 响应与错误

业务服务采用统一响应：

```json
{"code":"0","message":"OK","data":{"orderId":"CF-1234567890ABCDEF"}}
```

`code` 和 `message` 必须检查：登录凭据错误返回 HTTP 200 与 `AUTH-001`。空 `data` 和空 `traceId` 会省略；例如查询不存在的订单或 SKU 仍返回 `{"code":"0","message":"OK"}`。业务服务返回 `X-Trace-Id` 响应头，业务异常响应还可能包含相同的 `traceId` 字段；调用方可传入此请求头关联当前请求。服务间调用暂未自动透传该标识。

网关拒绝请求时，HTTP 401（无效/过期/已注销 Token）、403（角色无权写入）、503（Redis 吊销检查不可用）的响应体为空。认证服务直接返回的 401/503 则采用统一 JSON 响应，因此客户端应同时检查 HTTP 状态与响应体。

| HTTP 状态 | code | 含义 |
| --- | --- | --- |
| 200 | `0` | 成功 |
| 200 | `AUTH-001` | 用户名或密码错误 |
| 400 | `400` | 请求参数校验失败 |
| 401 | `401`（认证服务）或空体（网关） | 缺失、无效、过期或已注销 JWT |
| 403 | 空体（网关） | 当前角色无权执行写操作 |
| 409 | `PUR-001`、`PUR-002` | 采购单创建失败、库存入库失败 |
| 409 | `PUR-404`、`PUR-409` | 采购单不存在、状态不允许入库或状态冲突 |
| 409 | `INV-001`、`INV-404` | 库存不足、入库 SKU 不存在 |
| 409 | `INV-409` | 同一幂等键的 SKU/数量不一致，或入库记录尚未就绪 |
| 409 | `ORD-404`、`ORD-002`、`ORD-003` | 订单不存在、库存预占失败、履约创建失败 |
| 409 | `FUL-001` | 履约单创建失败 |
| 500 | `500` | 未处理的服务异常 |
| 503 | `503`（认证服务）或空体（网关） | JWT 吊销服务不可用 |

## 幂等与业务流程

创建采购单或订单每次生成新记录；这两项操作暂未接受客户端幂等键。采购收货以 `purchaseId` 去重，库存预占以 `orderId` 去重，库存入库以 `purchaseId` 去重；同一库存幂等键必须使用首次请求的 SKU 和数量，变更参数会得到 `INV-409`。

订单创建后为 `PENDING_PAYMENT / CREATED`。`POST /api/v1/orders/{orderId}/pay` 是演示支付，预占库存并创建履约后返回 `PAID / FULFILLING`。中间状态 `PAID / READY_FOR_FULFILLMENT` 表示可以重试支付接口完成履约创建。重复成功支付会返回现有订单。履约创建按 `orderId` 去重；重复请求返回最初的履约单。

当前履约状态更新接口接收 `status` 查询参数并直接保存，尚未限制状态值或验证状态转换。契约的 `SHIPPED` 示例仅表示可传入的值，不代表已实现的状态机。
