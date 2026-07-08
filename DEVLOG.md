# 开发日志 (DEVLOG)

## 2026-05-26

### 1. WXML 编译错误：wx:else 找不到配对的 wx:if

**现象**：`Bad attr wx:else with message: wx:if not found`

**原因**：`wx:else` 和 `wx:for` 写在了同一个标签上。微信小程序编译器先展开 `wx:for`，导致 `wx:else` 找不到前面相邻的 `wx:if`。

**修复**：用 `<block wx:if>` 和 `<block wx:else>` 做条件分支，把 `wx:for` 放在 `<block>` 内部的子元素上。

**文件**：`pages/admin-cert/admin-cert.wxml`

---

### 2. 业务逻辑重构：审批制 → 直接联系 + 评论

**改动**：
- 取消"申请认领/帮助 → 发布者审批"流程
- 招领帖（found）：浏览者点"联系TA"复制手机号去微信联系
- 寻物帖（lost）：路人可评论提供线索 + 捡到的人点"联系TA"
- 新增 `comments` 表、`Comment` 全套后端、前端评论区
- `items` 表新增 `phone` 字段存储联系方式

**文件**：新建5个、修改10个

---

### 3. 个人中心"消息"入口重复

**现象**：统计栏的"消息"和菜单栏的"消息通知"都跳转到同一页面。

**修复**：去掉菜单栏的"消息通知"，只保留统计栏入口；统计栏新增"已解决"计数列；菜单分为"认证与管理"和"账号与设置"两组。

**文件**：`pages/mine/mine.wxml`, `mine.wxss`, `mine.js`

---

### 4. 边界问题修复（7项）

| # | 问题 | 修复 |
|---|------|------|
| 1 | 手机号在公开接口暴露 | 详情/列表接口隐藏 phone，新增 `GET /items/{id}/contact` 需登录获取 |
| 2 | 过期帖子还能联系 | 非发布者 + 过期/已解决 → 不显示底部联系按钮 |
| 3 | 过期帖子还能评论 | 后端 `CommentController.createComment` 加 status != active 拒绝 |
| 4 | 评论不显示用户名 | `getCommentsByItem` 返回附带 nickname |
| 5 | 发布者评论自己的帖子 | 后端拒绝 + 前端发布者不显示评论输入框 |
| 6 | 5分钟内重复评论 | `CommentMapper.countRecentDuplicate` 加5分钟窗口检测 |
| 7 | 发布后不能修改 | 详情页加"编辑"按钮，发布页支持编辑模式（调 update 接口） |

---

### 5. 物品分类筛选功能

新增 `items.category` 字段，6个预设分类：证件卡片、电子产品、服饰配件、学习用品、生活用品、其他物品。

**文件**：修改 `Item.java`, `ItemController.java`, `ItemService/Impl`, `DataInitializer.java`, `api.js`, `publish.js/wxml`, `index.js/wxml/wxss`

---

### 6. 真实微信登录

**改动**：
- `POST /api/auth/login` 从接收 `openid` 改为接收 `code`
- 后端调用微信 `jscode2session` 接口换取真实 openid
- 开发模式兼容：secret 未配置时自动用 `mock_openid_demo`，不影响开发

**文件**：`application.yml`, `AuthController.java`, `api.js`, `app.js`

---

### 7. AI 智能识图填表（智譜 GLM-4.6V-Flash）

**功能**：发布页上传图片后点击"AI智能识别"，自动填入标题、描述、分类。

**文件**：新建 `AiController.java`，修改 `api.js`, `publish.wxml/js/wxss`

---

### 8. 编译错误：List<Certification> 无法转换为 Certification

**原因**：`CertificationService.findByUserId()` 返回 `List<Certification>`，但 AuthController 里用成了单个对象。

**修复**：改为 `existingList.get(0)` 取第一个元素。

**文件**：`AuthController.java:161`

---

### 9. 编译错误：CertificationService 缺少 findById/update 方法

**原因**：写 AuthController 时使用了 `certificationService.findById()` 和 `.update()`，但这些方法没有被定义。

**修复**：在 `CertificationService` 接口和 `CertificationServiceImpl` 中新增这两个方法。

**文件**：`CertificationService.java`, `CertificationServiceImpl.java`

---

### 10. 智譜 API JWT Token 生成失败：密钥长度不够

**现象**：`The specified key byte array is 128 bits which is not secure enough for any JWT HMAC-SHA algorithm`

**原因**：智譜 API Key 的 secret 部分只有 128 位（16字节），JJWT 要求 HS256 密钥 >= 256 位。`SecretKeySpec` 同样被 JJWT 拒绝。

**修复**：放弃 JJWT，改为手动构造 JWT —— Base64URL 编码 header/payload，用 `javax.crypto.Mac`（HmacSHA256）直接计算签名，手动拼接成完整 JWT。

**文件**：`AiController.java — generateZhipuToken()`

---

### 11. 微信开发者工具预览报错：代码包超过 2MB

**现象**：`source size 2199KB exceed max limit 2MB`

**原因**：`uploads/images/` 目录下有大文件（3张 649KB 的 JPG），被一起打包到了小程序代码包。

**修复**：
1. 删掉测试图片（~2MB）
2. 在 `project.config.json` 的 `packOptions.ignore` 中配置忽略 `backend/`、`uploads/`、`*.md`、`*.ps1`
3. 这样以后上传新图片也不会超出限制

**文件**：`project.config.json`

---

### 12. AI 识别失败：图片URL无法被智譜访问

**现象**：`"error":{"code":"1210","message":"图片输入格式/解析错误"}`

**原因**：上传后的图片URL是 `http://localhost:8080/images/xxx.jpg`，智譜服务器无法访问 localhost。

**修复**：新增 `convertToBase64DataUrl` 方法 —— 检测到 localhost URL 时，读取本地文件转为 base64 data URL 再发给智譜。

**文件**：`AiController.java — convertToBase64DataUrl()`

---

### 13. 本地图片路径映射错误

**现象**：图片转 base64 时报 `\images\xxx.jpg` 找不到。

**原因**：上传接口返回的 URL 路径是 `/images/xxx.jpg`，但实际文件存储在 `{user.dir}/uploads/images/xxx.jpg`。路径不匹配。

**修复**：在 `convertToBase64DataUrl` 中将 `/images/` 替换为 `/uploads/images/`。

**文件**：`AiController.java — convertToBase64DataUrl()`

---

## 当前项目状态

### 已完成的功能模块

| 模块 | 状态 |
|------|------|
| 用户认证（微信登录 + 校园卡认证）| 完成 |
| 物品发布（寻物/招领 + 分类）| 完成 |
| 首页列表（类型筛选 + 分类筛选 + 搜索）| 完成 |
| 物品详情（联系TA + 评论区）| 完成 |
| 消息通知中心 | 完成 |
| 个人中心 | 完成 |
| 管理员认证审核 | 完成 |
| 物品编辑 | 完成 |
| 延期/标记已解决/删除 | 完成 |
| AI 智能识图填表 | 完成 |
| 多模态搜索（CLIP） | 未开始 |

### 待办

- [ ] 获取微信 AppSecret 完成真实登录
- [ ] 多图上传
- [ ] 首页 UI 优化
