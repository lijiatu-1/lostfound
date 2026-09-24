# 校园失物招领

微信小程序 + Spring Boot 3.2 / Java 17 + MyBatis-Plus + MySQL 8 的校园失物招领项目。当前业务主线是：发布物品 → 提交认领或线索申请 → 发布者批准 → 双方私信 → 结案；发布者也能把处理中的物品重新开放。

## 目前的使用边界

- 发现和搜索只显示 `active` 且未过期的物品。`processing` 详情仍可读，但不能再申请；`resolved` 私信历史可读但不能再发送。
- API 不接收或返回电话和精确经纬度。旧数据库列暂保留，等待备份后另行清理。
- 发布和编辑只能引用本人上传的物品图片资产 ID；校园卡图片存入私有目录，只能由本人或管理员通过授权接口下载。
- AI 入口仅接受本人物品图片资产 ID，旧任意 URL 下载入口已移除。配置 `AI_ENABLED=true` 和轮换后的 `ZHIPU_API_KEY` 后，可在本人主动点击时调用智谱识图；默认每人每天 5 次，未启用时返回 503。
- 开发演示用户和 mock 登录仅存在于 `dev` profile。生产默认使用 `prod`，缺少配置时拒绝启动。

## 本地开发

需要 Java 17、Maven 3.8+、MySQL 8、微信开发者工具。先创建 `example_db`，再在本机环境设置 `DB_USERNAME`、`DB_PASSWORD` 和随机 `JWT_SECRET`（至少 32 字符）；开发配置不内置弱凭据：

```powershell
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

打开微信开发者工具前，先复制 `utils/runtime-config.example.js` 为被 Git 忽略的 `utils/runtime-config.local.js`；这个文件是小程序构建所需的本地配置。开发版默认请求 `http://localhost:8080/api`。构建体验版或正式版前，必须将其中对应的示例地址替换为已备案并在微信后台配置的真实 HTTPS 合法域名。

## 文档

- [API 契约](API.md)
- [部署与凭据处理](DEPLOYMENT.md)
- [人工验收清单](QA.md)
- [后端构建说明](backend/README.md)
- [开发日志](DEVLOG.md)

`project_spec.md` 是早期 CloudBase / MongoDB 方案的归档，不代表当前实现。
