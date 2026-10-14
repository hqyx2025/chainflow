# 本地回归验证

`mvn clean verify` 执行单元测试与打包。采购 MySQL 集成测试需显式设置 `CHAINFLOW_PURCHASE_IT=true`，默认跳过，不会误连本机数据库。

```powershell
docker compose up -d --wait mysql redis
$env:CHAINFLOW_PURCHASE_IT = 'true'
mvn -pl chainflow-purchase -am '-Dtest=PurchaseServiceMySqlIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
Remove-Item Env:CHAINFLOW_PURCHASE_IT
```

业务容器启动并进入 healthy 后，使用 PowerShell 7 执行真实 HTTP 回归：

```powershell
mvn clean verify
docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --build --wait mysql redis gateway auth purchase inventory order fulfillment
pwsh -File scripts/smoke.ps1
```

脚本验证匿名请求 401、缺少数量 400、创建采购单返回生成 ID、采购入库、支付到履约、重复操作库存只变化一次、幂等参数冲突 409，以及注销后旧 Token 401、新 Token 可以访问。脚本会创建 `SMOKE-SUPPLIER` 采购单、销售订单与相关预占记录，保留演示数据；应在独立本地开发环境运行。默认账号为 `admin/admin123`，自定义账号可通过 `-Username` 与 `-Password` 参数传入。

网关角色测试覆盖 ADMIN、SUPPLY_CHAIN_OPERATOR、PROCUREMENT、WAREHOUSE、SALES 的读写权限及路径编码边界。Redis 查询在线程池运行，避免阻塞网关的 Netty 事件线程；Redis 连接与命令超时均为 2 秒。Redis 不可达时，保护请求返回 503。

完成验证后停止容器并保留数据卷：

```powershell
docker compose -f docker-compose.yml -f docker-compose.services.yml down
```

## 2026-10-14 验证记录

- Java 17 下 `mvn clean verify` 通过，70 个用例通过；单独启用的真实 MySQL 采购集成测试另有 1 个用例通过。
- 六个业务服务及 MySQL、Redis 全部进入 `healthy`，`scripts/smoke.ps1` 返回 `PASS`。
- 真实 HTTP 角色检查覆盖采购、销售、仓库、只读角色与编码路径，越权写请求返回 403。
- 独立网关实例将 Redis 端口指向不可达端口，保护接口返回 503；主业务 Redis 保持运行。
- OpenAPI 契约通过规范验证，包含 14 条路径和 15 个操作。

本轮没有打开 Seata 或 RocketMQ 业务开关，因此不作为分布式回滚或消息一致性的验证证据。
