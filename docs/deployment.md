# ChainFlow 本地基础设施部署

本文档说明如何使用 Docker Compose 启动 ChainFlow 开发环境所需的中间件：MySQL 8、Redis、Nacos、RocketMQ 和 Elasticsearch。RocketMQ Dashboard 与 Seata Server 默认不启动，需要时分别启用 `console` 和 `seata` profile。

## 环境要求

- Docker Engine 24+ 和 Docker Compose v2（使用 `docker compose` 命令）
- 至少 6 GB 可用内存；Elasticsearch 和 Nacos 会占用较多内存
- Windows/macOS 使用 Docker Desktop 时，将 Docker Desktop 内存分配调整到至少 6 GB
- Linux 主机需要允许 Elasticsearch 使用 `vm.max_map_count=262144`：

  ```bash
  sudo sysctl -w vm.max_map_count=262144
  ```

Compose 文件位于仓库根目录，RocketMQ Broker 配置位于 `infra/rocketmq/broker.conf`。首次启动时会创建持久化卷，删除容器不会删除数据。

## 启动与停止

在仓库根目录执行：

```bash
# 启动核心中间件（后台运行）
docker compose up -d

# 同时启动 RocketMQ Dashboard
docker compose --profile console up -d

# 启动可选 Seata Server（文件存储模式）
docker compose --profile seata up -d seata-server

# 同时启动 Dashboard 和 Seata
docker compose --profile console --profile seata up -d

# 查看容器状态和日志
docker compose ps
docker compose logs -f rocketmq-broker

# 停止容器，保留数据卷
docker compose down

# 清理容器和所有开发数据（不可恢复）
docker compose down -v
```

首次启动镜像下载和 Elasticsearch 初始化可能需要几分钟。使用 `docker compose ps` 确认容器进入 `running`，再启动 ChainFlow Java 服务。

## 服务地址与连接参数

宿主机访问使用下表端口；容器之间访问时使用 Compose 服务名和容器端口，例如 `mysql:3306`、`rocketmq-namesrv:9876`。

| 组件 | 宿主机地址 | 默认凭据/参数 |
| --- | --- | --- |
| MySQL 8 | `127.0.0.1:13306` | 库 `chainflow`，用户 `chainflow`，密码 `chainflow_dev`；root 密码 `chainflow_root` |
| Redis | `127.0.0.1:16379` | 密码 `chainflow_redis` |
| Nacos | [http://127.0.0.1:18848/nacos](http://127.0.0.1:18848/nacos) | 本地开发已关闭鉴权；客户端地址 `127.0.0.1:18848` |
| RocketMQ NameServer | `127.0.0.1:19876` | NameServer 地址 `127.0.0.1:19876` |
| RocketMQ Broker | `127.0.0.1:20911` | 客户端通过 NameServer 发现 Broker |
| RocketMQ Dashboard | [http://127.0.0.1:18080](http://127.0.0.1:18080) | 仅使用 `--profile console` 时可用 |
| Seata Server | `127.0.0.1:8091`（事务服务）；[http://127.0.0.1:17091](http://127.0.0.1:17091)（HTTP 管理端口） | 仅使用 `--profile seata` 时启动，文件存储模式 |
| Elasticsearch | [http://127.0.0.1:19200](http://127.0.0.1:19200) | 开发环境关闭安全认证 |

常用 Spring Boot 配置示例：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:13306/chainflow?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: chainflow
    password: chainflow_dev
  data:
    redis:
      host: 127.0.0.1
      port: 16379
      password: chainflow_redis
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:18848
      config:
        server-addr: 127.0.0.1:18848
rocketmq:
  name-server: 127.0.0.1:19876
seata:
  enabled: ${SEATA_ENABLED:false}
  tx-service-group: chainflow_tx_group
```

订单服务当前通过 `SEATA_ENABLED` 控制 Seata 客户端，默认值为 `false`。启用本地 Seata 时，先启动 profile，再在启动订单服务的同一 shell 中设置环境变量：

```bash
docker compose --profile seata up -d seata-server

# Linux/macOS
export SEATA_ENABLED=true
mvn -f chainflow-order/pom.xml spring-boot:run

# Windows PowerShell
$env:SEATA_ENABLED = "true"
mvn -f chainflow-order/pom.xml spring-boot:run
```

关闭 Seata 或恢复默认轻量开发模式时，将 `SEATA_ENABLED` 设为 `false`（或取消该环境变量），并执行 `docker compose --profile seata stop seata-server`。当前客户端使用 `chainflow_tx_group`，默认连接本机 `8091`；Compose 已将 Seata 事务端口保持为 `8091:8091`，因此无需额外覆盖客户端地址。Seata 使用 AT 模式时，各业务库还需要对应的 `undo_log` 表，正式环境应改用数据库存储、认证和独立凭据。

## 修改默认密码和端口

Compose 支持通过仓库根目录的 `.env` 文件或当前 shell 环境覆盖密码：

```dotenv
MYSQL_ROOT_PASSWORD=change-me-root
MYSQL_PASSWORD=change-me-app
REDIS_PASSWORD=change-me-redis
```

端口直接编辑 `docker-compose.yml` 左侧宿主机端口，例如将 `13306:3306` 改成 `23306:3306`。修改 MySQL 或 Redis 密码后，已有数据卷中的账号密码不会自动重置；开发环境可以执行 `docker compose down -v` 后重新启动，生产环境应通过数据库和 Redis 的正式凭据轮换流程处理。

## 就绪检查与故障排查

```bash
# MySQL
docker compose exec mysql mysqladmin ping -u root -p

# Redis
docker compose exec redis sh -c 'redis-cli -a "$REDIS_PASSWORD" ping'

# Nacos
curl http://127.0.0.1:18848/nacos/v1/console/health/readiness

# RocketMQ（查看 NameServer/Broker 日志）
docker compose logs --tail=100 rocketmq-namesrv rocketmq-broker

# Seata（仅启用 seata profile 后可用）
docker compose logs --tail=100 seata-server

# Elasticsearch
curl http://127.0.0.1:19200/_cluster/health
```

常见问题：

- 端口已被占用：修改 Compose 左侧宿主机端口，并同步修改 Java 服务配置。
- Elasticsearch 启动后反复退出：Linux 检查 `vm.max_map_count`；Docker Desktop 增加内存分配。
- Broker 无法注册：确认 `rocketmq-namesrv` 已启动，并检查 `docker compose logs rocketmq-broker`；应用在宿主机连接时使用 `127.0.0.1:19876`。当前开发配置让 NameServer 将 Broker 广播为 `host.docker.internal:20911`，宿主机 Java 服务和可选 Dashboard 都能通过已发布端口访问。Docker Desktop 会自动解析该地址；Linux 主机若无法解析，请将 `host.docker.internal` 指向 Docker 网关（或改用可达的宿主机地址后同步修改 `infra/rocketmq/broker.conf`）。
- Nacos 页面打不开：等待容器完成初始化后重试，或运行 `docker compose logs nacos` 查看启动日志。

## 开发环境边界

Compose 中 Nacos 关闭了鉴权，Elasticsearch 关闭了安全认证，RocketMQ 开启了自动创建 Topic，这些设置只适用于本地开发。生产部署应启用 Nacos/Elasticsearch 认证和 TLS、关闭自动建 Topic、使用独立凭据和外部密钥管理，并将数据卷、备份、资源限制和监控纳入正式运维方案。
