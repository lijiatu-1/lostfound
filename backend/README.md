# 后端

使用 Spring Boot 3.2、Java 17、MyBatis-Plus、MySQL 8、Flyway。接口定义见根目录 [API.md](../API.md)，部署配置见 [DEPLOYMENT.md](../DEPLOYMENT.md)。

开发环境显式选择 `dev` profile，并在本机设置 `DB_USERNAME`、`DB_PASSWORD` 与至少 32 字符的随机 `JWT_SECRET`；配置文件不提供默认凭据：

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

测试与打包：

```powershell
mvn verify
```

集成测试使用 MySQL Testcontainers。Docker 不可用时本地会跳过容器测试；CI 会检查测试确实执行且没有跳过。Flyway 在启动时执行 `V1`、`V2`、`V3` 迁移并校验历史。已有开发库可在备份后使用 `dev` profile 的 baseline 设置；生产旧库需先备份、核对结构，再一次性设置 `FLYWAY_BASELINE_ON_MIGRATE=true` 迁移。
