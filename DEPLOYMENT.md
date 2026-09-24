# 部署与凭据

## 必做的外部动作

1. 在智谱平台轮换此前写入 Git 历史的 AI Key；在 GitHub 撤销此前嵌入本机 remote URL 的访问令牌。工作区源码已去除明文值，本机 remote URL 已改为无令牌形式。仓库旧提交仍可能包含旧值，因此必须撤销旧凭据。
2. 在微信小程序后台配置正式 HTTPS request / upload / download 合法域名。生产 API 基址由部署私有配置提供，不可为 localhost。
3. 备份生产数据库和上传目录。旧 `phone`、`location_lat`、`location_lng` 列及旧记录暂保留；彻底清理与公开 Git 历史重写需单独确认。

## 生产环境变量

`SPRING_PROFILES_ACTIVE=prod`，以及 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`JWT_SECRET`（至少 32 字符随机值）、`WECHAT_APPID`、`WECHAT_SECRET`、`PUBLIC_API_BASE_URL`（非 localhost 的 HTTPS `.../api`）、`MEDIA_UPLOAD_DIR`（持久化私有卷路径）。不应把这些值提交到 Git。

首次迁移全新库时 Flyway 自动执行 V1 至 V3。已有旧库需先备份和核对旧结构，再临时设置 `FLYWAY_BASELINE_ON_MIGRATE=true`；成功后恢复为 false。若旧 `applications` 存在同一物品、申请人和类型的重复行，V2 的唯一约束会拒绝迁移，需人工核查后处理，不能盲目删除数据。

首次管理员需由运维人员在核实本人微信账号后，通过受控数据库操作将对应 `users.role` 设为 `admin`；生产不会自动创建演示管理员。管理员账号应使用独立强凭据与审计流程维护。

小程序构建前必须复制 `utils/runtime-config.example.js` 为忽略提交的 `utils/runtime-config.local.js`，填体验版和正式版 API 域名；该文件可由私有构建配置注入，不要提交示例域名作为正式配置。正式版或体验版使用非 HTTPS / localhost 时客户端拒绝请求。

## AI 识图现状

旧版可提交任意图片 URL 的识图方式已移除。用户主动点击并确认识图时，新接口先检查 JWT、校园认证、本人图片资产用途与归属，以及文件大小和像素上限，再将这张物品图发送到固定的智谱 API 地址。设置 `AI_ENABLED=true` 和轮换后的 `ZHIPU_API_KEY` 才启用；默认每人每天 5 次，可通过 `AI_DAILY_LIMIT` 调低，但不会超过 5 次。未启用返回 503，次数用尽返回 429，调用超时或失败返回安全的 502 错误，用户可手动填写。小程序在发送前会向最终用户展示图片接收方与用途；上线前仍需在真机上用新密钥验收一次。

## 上线前验收

执行 `mvn verify`、密钥扫描和 [人工验收清单](QA.md)。MySQL Testcontainers 需要可用的 Docker；CI 会检查测试实际运行。图片目录的访问应由应用接口控制，不要在反向代理中把 `uploads/media` 直接映射为静态目录。
