# ChainFlow 供应链协同平台

ChainFlow 是一个面向采购、库存、订单与履约的 Java 微服务项目。当前仓库从空白初始提交开始搭建，第一阶段已经具备可编译的 Spring Cloud Alibaba 多模块骨架和一条可演示的业务闭环。

## 技术栈

- Java 17、Maven、Spring Boot 3.3、Spring Cloud 2023
- Spring Cloud Gateway、Nacos Discovery
- MyBatis 注解 Mapper、MySQL 8、Redis 7
- JWT 登录令牌、RocketMQ、Elasticsearch、Docker Compose
- 订单支付状态：`PENDING_PAYMENT → PAID`
- 库存预占：带库存条件的原子扣减，订单号唯一保证幂等

## 模块

| 模块 | 容器端口 | 作用 |
| --- | ---: | --- |
| `chainflow-gateway` | 8080 | 统一入口、路由、JWT 校验 |
| `chainflow-auth` | 9001 | 演示登录和 JWT 签发 |
| `chainflow-purchase` | 9003 | 创建和查询采购单 |
| `chainflow-inventory` | 9004 | 查询库存、库存预占 |
| `chainflow-order` | 9005 | 创建订单、支付、调用库存预占 |
| `chainflow-fulfillment` | 9006 | 创建履约单、推进履约状态 |
| `chainflow-common` | - | 统一响应、异常、JWT、Trace ID |

通过 `docker-compose.services.yml` 启动时，只有网关的 `8080` 映射到宿主机；认证、采购、库存、订单和履约服务仅在 `chainflow` Docker 网络中通过各自容器端口访问（例如 `http://auth:9001`），不会直接暴露宿主机端口。

## 快速启动

1. 启动基础设施：

   ```bash
   docker compose up -d mysql redis
   ```

   需要 Nacos、RocketMQ、Elasticsearch 时执行：

   ```bash
   docker compose up -d
   ```

2. 编译全部模块并生成可执行 JAR：

   ```bash
   mvn clean verify
   mvn -DskipTests install
   ```

   也可以只执行 `mvn -DskipTests package`，供 Docker 镜像构建使用。

3. 分别启动服务（也可以在 IDE 中运行各模块的 `*Application`）：

   ```bash
   mvn -f chainflow-auth/pom.xml spring-boot:run
   mvn -f chainflow-inventory/pom.xml spring-boot:run
   mvn -f chainflow-order/pom.xml spring-boot:run
   mvn -f chainflow-gateway/pom.xml spring-boot:run
   ```

   采购和履约服务分别使用 `chainflow-purchase`、`chainflow-fulfillment` 启动。应用默认连接 Compose 映射端口：MySQL `13306`、Redis `16379`、Nacos `18848`。

4. 使用 Docker 运行全部六个业务服务：

   ```bash
   # JAR 必须先由 Maven 生成
   mvn -DskipTests package
   docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --build mysql redis gateway auth purchase inventory order fulfillment
   docker compose -f docker-compose.yml -f docker-compose.services.yml ps
   ```

   业务容器通过 `chainflow` 网络使用 `mysql:3306`、`redis:6379` 和服务名互相访问。只有 `http://localhost:8080` 是宿主机入口，其他业务服务端口仅对 Docker 网络内可见；默认关闭 Nacos、Seata、RocketMQ 开关。合并配置可用以下命令校验：

   ```bash
   docker compose -f docker-compose.yml -f docker-compose.services.yml config --quiet
   ```

## 持续集成

仓库包含 GitHub Actions 工作流 [`.github/workflows/ci.yml`](.github/workflows/ci.yml)，在推送到 `main` 或创建/更新 Pull Request 时自动执行：

- 使用 Temurin Java 17 和 Maven 构建缓存运行 `mvn clean verify`，包括独立 MySQL 8 中的采购集成测试；
- 校验基础设施与业务服务的合并 Docker Compose 配置，不拉取镜像，也不启动服务。
- 使用 OpenAPI 规范验证器校验静态接口契约。
- 在临时 MySQL 数据库验证表约束、旧库升级、脏数据预检和重复迁移，不修改应用已有表。

## 接口演示

接口请求/响应、JWT 角色和错误码见 [`docs/api.md`](docs/api.md)；可将 [`docs/openapi.yaml`](docs/openapi.yaml) 导入 Postman、Apifox 或 Swagger Editor 查看完整 OpenAPI 3.0 契约。当前使用静态契约，应用不提供 Swagger UI 页面。

默认演示账号为 `admin/admin123`，可通过环境变量 `CHAINFLOW_DEMO_USERNAME`、`CHAINFLOW_DEMO_PASSWORD` 修改。

```bash
# 1. 登录拿 Token
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.data.accessToken')

# 2. 创建订单（初始为 PENDING_PAYMENT）
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-DEMO-001","quantity":2}'

# 3. 对返回的 orderId 发起演示支付；订单服务会调用库存服务预占库存
curl -X POST http://localhost:8080/api/v1/orders/{orderId}/pay \
  -H "Authorization: Bearer $TOKEN"

# 4. 采购入库（采购单创建后调用，库存会增加且重复调用幂等）
curl -X POST http://localhost:8080/api/v1/purchases/{purchaseId}/receive \
  -H "Authorization: Bearer $TOKEN"

# 5. 注销 Token；之后该 Token 通过网关访问业务接口会返回 401
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Authorization: Bearer $TOKEN"
```

完整模块边界、状态机、数据约束见 [`docs/architecture.md`](docs/architecture.md)；基础设施、端口、环境变量和故障排查见 [`docs/deployment.md`](docs/deployment.md)。

## 当前边界

当前业务通过 REST 调用与服务内事务实现演示闭环。订单服务包含 Seata 事务入口与 RocketMQ 发布代码，默认关闭；跨服务 XID 传播、`undo_log`、事务组映射和 Outbox 仍需实现与验证，单独打开环境变量尚不能保证分布式事务或消息一致性。Elasticsearch 目前提供基础设施，检索读模型待接入。生产部署还需替换演示账号、JWT 密钥与默认凭据。

真实容器回归可运行 `pwsh -File scripts/smoke.ps1`，步骤和数据副作用见 [`docs/verification.md`](docs/verification.md)。

支持网关与服务间 Trace ID 透传、依赖型 readiness、履约状态迁移校验；旧数据库约束升级见 [`V002 迁移脚本`](infra/mysql/migrations/V002__business_constraints.sql)。
