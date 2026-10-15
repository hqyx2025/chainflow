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

脚本还验证履约逐步推进到 `COMPLETED`、重复完成的幂等性与非法回退，并检查每个响应的 `X-Trace-Id`。履约状态接口目前不联动扣减/释放预占库存，验证中的库存数量断言按这一边界计算。

MySQL 约束与迁移可独立验证：

```powershell
docker compose up -d --wait mysql
python scripts/verify_schema.py
```

该脚本创建随机命名的临时数据库，验证新表 11 个 CHECK、模拟旧库升级、脏数据中止且不改记录、迁移重跑；完成后只删除自己创建的临时数据库。自定义 root 密码时设置 `CHAINFLOW_TEST_DB_ROOT_PASSWORD`，可用 `--container` 指定 MySQL 容器。

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

## 2026-10-15 验证记录

- Java 17 下全仓 `mvn clean verify` 通过，109 个用例通过；单独运行的真实 MySQL 采购集成测试另有 1 个用例通过。
- MySQL 8.0.36 中验证新表 11 个 CHECK、旧库脏数据预检、迁移升级及重复执行，脚本返回 `PASS`，临时测试数据库已清理。
- 六服务容器全部 `healthy`，readiness 均返回 `UP`；扩展后的 smoke 脚本验证了履约完整状态路径、重复完成、非法回退和缺失记录。
- 回归 Trace ID `smoke-740bbc7ff6984e06bff9c1d80ea3a669` 在网关、采购、库存、订单与履约日志中保持一致，401/409 响应也回传相同 ID。
- 支付状态校验、履约故障后重试和事件重新发布机会由自动化用例覆盖。RocketMQ/Seata 默认仍关闭，本轮未声称消息投递或跨服务回滚保证。
