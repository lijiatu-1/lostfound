# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

校园失物招领平台 — 微信小程序前端 + Spring Boot 后端，帮助学生发布/查找失物。

## 开发命令

### 后端（`backend/`）

```bash
# 启动（开发模式，需要先启动 MySQL）
cd backend && mvn spring-boot:run

# 打包
mvn clean package -DskipTests
```

- 后端默认运行在 `http://localhost:8080`
- 数据库配置在 `backend/src/main/resources/application.yml`：MySQL `example_db`，用户 `root`，密码 `123456`
- 启动前需先执行 `backend/src/main/resources/schema.sql` 建表
- 首次启动时 `DataInitializer` 自动插入演示数据（用户、物品、申请、消息）
- 开发阶段使用固定 mock openid `mock_openid_demo`（`app.js:18`），与演示用户匹配
- 需要 JDK 17+ 和 Maven 3.8+

### 前端（小程序根目录）

- 用微信开发者工具打开项目根目录 `D:\LOSTP`
- AppID：`wx161d667f97b15ac1`
- 后端地址硬编码在 `utils/api.js:1`：`http://localhost:8080/api`，如需切换环境修改该常量

### 测试脚本（PowerShell）

根目录下有多个测试脚本（`test_*.ps1`），用于手动测试 API：

| 脚本 | 用途 |
|------|------|
| `test_login.ps1` | 登录测试 |
| `test_all_api.ps1` | 全量 API 测试 |
| `test_certification.ps1` | 认证流程测试 |
| `test_item_actions.ps1` | 物品操作测试 |
| `test_mock.ps1` | Mock 数据 |
| `test_api.ps1` | 基础 API 测试 |

## 架构

### 后端分层（Spring Boot + MyBatis-Plus）

```
controller/  → REST 接口，从 Header 取 JWT token 解析 userId
service/     → 业务逻辑，impl/ 是具体实现
mapper/      → MyBatis-Plus BaseMapper，直接对接 MySQL
entity/      → 实体类（User, Item, Application, Message, Certification）
config/      → WebConfig（CORS）、GlobalExceptionHandler、MybatisPlusConfig（分页插件）、DataInitializer（种子数据）、ScheduledTask（定时过期）
util/        → JwtUtil（生成/解析 JWT）
```

- **JWT 认证**：登录后返回 token，前端存 `wx.setStorageSync('token', token)`，每次请求带 `Authorization: Bearer <token>`
- **认证状态机**：`unauthorized → pending → authorized / rejected`，发布/认领需 `authorized` 状态，管理员审核需已认证用户
- **分页**：列表接口返回 `{items/messages, total, page, pageSize}`，默认 page=1, pageSize=20
- **定时任务**：每分钟扫描过期物品，将 `status` 从 `active` 更新为 `expired`
- **认证审计**：`certifications` 表独立记录每次提交和审核操作
- 全局异常处理：`IllegalArgumentException` → 400（含 JWT 无效），其他 → 500，统一返回 `{success: false, message: ...}`

### 前端页面结构

| 页面路径 | 功能 |
|----------|------|
| `pages/index/index` | 首页 Tab — 失物公示列表，支持类型筛选和关键词搜索 |
| `pages/publish/publish` | 发布 Tab — 发布寻物/招领表单 |
| `pages/mine/mine` | 个人中心 Tab — 用户信息、认证、我的发布/认领入口 |
| `pages/detail/detail` | 物品详情页 — 查看详情、发起帮助/认领 |
| `pages/auth/auth` | 校园卡认证页 |
| `pages/messages/messages` | 消息通知列表 |
| `pages/my-publish/my-publish` | 我发布的物品 |
| `pages/my-claim/my-claim` | 我的帮助/认领记录 |

- `utils/api.js` 封装了所有后端 API 调用（auth、item、application、message 四个模块），底层使用 `wx.request`
- `app.js` 全局入口：onLaunch 时调用 `wx.login` 获取 code，用 mock openid 登录换取 JWT token
- 当前使用 **mock openid**（`mock_openid_` + 时间戳），正式上线需替换为真实 `wx.login` 流程

### 数据库（MySQL）

5 张表：`users`、`items`、`applications`、`messages`、`certifications`。见 `schema.sql`。

- 物品 7 天自动过期（`expire_at = created_at + 7 days`）
- `items.tags` 和 `items.images` 用逗号分隔字符串存储（非正规化）
- 全文索引用于关键词搜索
- MyBatis-Plus 默认开启 `map-underscore-to-camel-case`，实体字段 `cardPhoto` ↔ 数据库列 `card_photo`

### 开发者背景与帮助方式

- 本科学生，目前只学了 Java 基础（集合等），正在边做边学
- 项目目标：通过实际开发学习技术，而非只写完功能
- 微信小程序 + Spring Boot 后端都是边学边做

**代码帮助规范（必须遵守）：**

当帮我写代码、改代码、排查问题时，请做到以下几点：

1. **指出具体问题** — 哪个文件、哪一行、什么错，不要含糊
2. **解释为什么错** — 用通俗语言讲清楚原因，避免直接丢术语（如要提到"JWT"，先简单解释是什么）
3. **给出正确代码** — 完整写出来，并逐行或逐段标注作用
4. **讲解涉及的知识点** — 遇到我不熟的概念（如 MyBatis、Promise、wx.request 回调等），额外用一两句话解释
5. **总结防错技巧** — 告诉我"以后遇到类似问题怎么排查"，帮我积累经验
6. **区分"修好"和"教会"** — 宁可多说几句，也别只甩一段代码让我自己猜

### 重要说明

- `project_spec.md` 描述的是云开发架构（云函数 + MongoDB），但实际实现为 Spring Boot + MySQL，两个文档有差异，以代码为准
- 后端 README 写需要 JDK 21，但 pom.xml 配置 `java.version=17`，实际 JDK 17 即可
