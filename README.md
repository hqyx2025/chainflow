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

| 模块 | 默认端口 | 作用 |
| --- | ---: | --- |
| `chainflow-gateway` | 8080 | 统一入口、路由、JWT 校验 |
| `chainflow-auth` | 9001 | 演示登录和 JWT 签发 |
| `chainflow-purchase` | 9003 | 创建和查询采购单 |
| `chainflow-inventory` | 9004 | 查询库存、库存预占 |
| `chainflow-order` | 9005 | 创建订单、支付、调用库存预占 |
| `chainflow-fulfillment` | 9006 | 创建履约单、推进履约状态 |
| `chainflow-common` | - | 统一响应、异常、JWT、Trace ID |

## 快速启动

1. 启动基础设施：

   ```bash
   docker compose up -d mysql redis
   ```

   需要 Nacos、RocketMQ、Elasticsearch 时执行：

   ```bash
   docker compose up -d
   ```

2. 编译全部模块：

   ```bash
   mvn clean verify
   mvn -DskipTests install
   ```

3. 分别启动服务（也可以在 IDE 中运行各模块的 `*Application`）：

   ```bash
   mvn -f chainflow-auth/pom.xml spring-boot:run
   mvn -f chainflow-inventory/pom.xml spring-boot:run
   mvn -f chainflow-order/pom.xml spring-boot:run
   mvn -f chainflow-gateway/pom.xml spring-boot:run
   ```

   采购和履约服务分别使用 `chainflow-purchase`、`chainflow-fulfillment` 启动。应用默认连接 Compose 映射端口：MySQL `13306`、Redis `16379`、Nacos `18848`。

## 接口演示

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
```

完整模块边界、状态机、数据约束见 [`docs/architecture.md`](docs/architecture.md)；基础设施、端口、环境变量和故障排查见 [`docs/deployment.md`](docs/deployment.md)。

## 当前边界

本阶段重点是可运行的微服务骨架和核心链路。订单服务已预留 Seata `@GlobalTransactional` 边界和 RocketMQ `chainflow-order-paid` 事件，默认关闭以便本地只启动 MySQL/Redis；设置 `SEATA_ENABLED=true`、`ROCKETMQ_ENABLED=true` 并启动对应基础设施即可启用。Elasticsearch 已在 Compose 中提供，后续接入检索读模型。生产环境还需要更换 JWT 密钥、数据库密码、镜像凭据，并补充 HTTPS、密钥管理和完整鉴权。
