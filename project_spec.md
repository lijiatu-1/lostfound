# 校园失物招领平台 PRD & EDD

## PRD 产品需求
目标用户是丢失物品的学生，能解决学生丢失物品后难以找回，找回步骤麻烦繁琐的问题。搭建一个容易发布、操作简单化的平台，提高失物找回的概率。

## EDD 工程需求
技术栈：Spring Boot，微信小程序原生（WXML/WXSS/JS），MyBatis-Plus/JPA，MySQL 8.0，JWT，Maven。

---

## 一、系统架构概览

本项目采用 **微信小程序 + 云开发（CloudBase）** 架构，便于快速开发与运维。
- **前端**：微信小程序（WXML + WXSS + JS/TS），使用 WeUI 或自定义组件还原三页面 UI。
- **后端**：云函数（Node.js） + 云数据库（MongoDB） + 云存储（图片、头像等）。
- **第三方服务**（强化版）：
  - 图像识别模型 API（如 GPT-4V）用于一键识图；
  - 向量数据库（如 Milvus 或云开发扩展） + CLIP 模型实现跨模态搜索；
  - 腾讯地图 API 用于逆地址解析与定位。

**架构分层**：[小程序客户端]
↓ 调用
[云函数层]：auth / item / interact / message / admin / ai
↓ 读写
[云数据库]：users, items, item_vectors (强化版), applications, messages, certifications
[云存储]：item_images, avatars, certification_photos

text

---

## 二、数据库设计

### 2.1 用户集合 `users`
| 字段 | 类型 | 说明 |
|------|------|------|
| `\\\_id` | ObjectId | 主键 |
| `openid` | string | 微信 openid，唯一 |
| `nickname` | string | 用户昵称 |
| `avatar\\\_url` | string | 头像云存储地址 |
| `status` | string | `"unauthorized"`（未认证）/ `"pending"`（审核中）/ `"authorized"`（已认证）/ `"rejected"`（已拒绝） |
| `real\\\_name` | string | 真实姓名（认证后填写） |
| `student\\\_id` | string | 学号（认证后填写） |
| `card\\\_photo` | string | 校园卡照片云存储地址（认证时提交） |
| `created\\\_at` | date | 注册时间 |
| `updated\\\_at` | date | 信息更新时间 |

### 2.2 物品信息集合 `items`
| 字段 | 类型 | 说明 |
|------|------|------|
| `\\\_id` | ObjectId | 主键 |
| `publisher\\\_id` | string | 发布者 user._id |
| `type` | string | `"lost"`（寻物）或 `"found"`（招领） |
| `title` | string | 标题（如“图书馆丢失蓝色耳机”） |
| `description` | string | 详细描述 |
| `location\\\_name` | string | 文字地点（如“图书馆3楼阅览室”） |
| `location\\\_coord` | object | 坐标 `{ lat: number, lng: number }`（强化版精准定位） |
| `images` | array | 图片云存储 URL 列表（目前仅需一张） |
| `tags` | array | 自动/手动标签，如 `\\\["蓝色", "耳机", "图书馆"]`（用于搜索） |
| `status` | string | `"active"`（公示中）/ `"hidden"`（到期隐藏）/ `"resolved"`（已解决） |
| `created\\\_at` | date | 发布时间 |
| `expire\\\_at` | date | 自动隐藏时间（`created\\\_at + 7天`） |
| `updated\\\_at` | date | 编辑时间 |

**索引设计**：
- `type` + `status` + `created\\\_at` 复合索引（首页列表查询）
- `publisher\\\_id` + `status`（我的发布查询）
- `title`、`description`、`location\\\_name`、`tags` 建立全文索引（文本搜索）

### 2.3 帮助/认领记录集合 `applications`
| 字段 | 类型 | 说明 |
|------|------|------|
| `\\\_id` | ObjectId | 主键 |
| `item\\\_id` | string | 关联物品 items._id |
| `applicant\\\_id` | string | 申请人（帮助者/认领者）user._id |
| `type` | string | `"help"`（帮助）或 `"claim"`（认领） |
| `content` | string | 申请说明（证明信息/线索描述） |
| `images` | array | 申请人上传的辅助图片（可选） |
| `status` | string | `"pending"` / `"accepted"` / `"rejected"` |
| `created\\\_at` | date | 申请时间 |

### 2.4 消息通知集合 `messages`
| 字段 | 类型 | 说明 |
|------|------|------|
| `\\\_id` | ObjectId | 主键 |
| `receiver\\\_id` | string | 接收者 user._id |
| `type` | string | `"claim\\\_apply"` / `"help\\\_offer"` / `"system\\\_notice"` / `"area\\\_update"` |
| `title` | string | 消息标题（如“张三申请认领你的校园卡”） |
| `content` | string | 详细内容 |
| `related\\\_item\\\_id` | string | 关联物品 items._id（可选） |
| `is\\\_read` | boolean | 是否已读，默认 false |
| `created\\\_at` | date | 通知时间 |

### 2.5 校园卡认证留底 `certifications`（可选，用于审核追踪）
| 字段 | 类型 | 说明 |
|------|------|------|
| `\\\_id` | ObjectId | 主键 |
| `user\\\_id` | string | 申请人 user._id |
| `real\\\_name` | string | 姓名 |
| `student\\\_id` | string | 学号 |
| `card\\\_photo` | string | 图片地址 |
| `status` | string | `"pending"` / `"approved"` / `"rejected"` |
| `reviewer\\\_id` | string | 审核管理员 user._id |
| `review\\\_msg` | string | 审核备注 |
| `created\\\_at` | date | 提交时间 |

### 2.6 物品向量集合 `item\\\_vectors`（强化版·跨模态搜索）
| 字段 | 类型 | 说明 |
|------|------|------|
| `\\\_id` | ObjectId | 主键 |
| `item\\\_id` | string | 关联 items._id |
| `image\\\_embedding` | array | 物品图片的向量（CLIP 生成） |
| `text\\\_embedding` | array | 标题+描述文本的向量（CLIP 生成） |

---

## 三、API 设计（云函数列表）

### 3.1 用户与认证
| 函数名 | 方法 | 描述 | 备注 |
|--------|------|------|------|
| `userLogin` | - | 静默登录，获取 openid 并存档 | 小程序前端调用 wx.login 换取 |
| `getUserInfo` | - | 返回当前用户完整信息 | 含昵称、头像、认证状态、未读消息数 |
| `updateUserProfile` | - | 修改昵称、头像 | MVP |
| `submitCertification` | - | 提交校园卡认证：姓名、学号、卡照片 | 状态变为 pending |
| `listCertifications` | - | 管理员查看待审核列表 | 后台管理 |
| `reviewCertification` | - | 管理员通过/拒绝认证 | 更新 users.status |

### 3.2 物品发布与管理
| 函数名 | 参数 | 描述 | 备注 |
|--------|------|------|------|
| `publishItem` | `{ type, title, description, location\\\_name, location\\\_coord, images }` | 发布寻物/招领 | 自动计算 expire_at；MVP 可不传坐标 |
| `editItem` | `{ item\\\_id, title, description, location\\\_name, ... }` | 编辑未隐藏的物品 | 仅发布者可操作 |
| `getItemList` | `{ type?, page, pageSize, keyword? }` | 首页列表：可按类型筛选、关键词搜索 | 仅返回 status=active 且未过期 |
| `getItemDetail` | `{ item\\\_id }` | 物品详情 | 含发布者简略信息 |
| `getMyItems` | `{ type?, page }` | 我的发布列表（含隐藏） | 本人所有物品 |

### 3.3 帮助与认领
| 函数名 | 参数 | 描述 | 备注 |
|--------|------|------|------|
| `applyAction` | `{ item\\\_id, type: "help"/"claim", content, images? }` | 发起帮助或申请认领 | 需已认证；向发布者推送消息 |
| `handleApplication` | `{ application\\\_id, action: "accept"/"reject" }` | 发布者处理申请 | 更新状态，并可标记物品为 resolved |

### 3.4 消息通知
| 函数名 | 参数 | 描述 | 备注 |
|--------|------|------|------|
| `getMessages` | `{ page, type? }` | 获取当前用户消息列表 | 按时间倒序 |
| `markMessageRead` | `{ message\\\_id }` | 将消息标为已读 | 更新 is_read |

### 3.5 强化版专用 API（云函数）
| 函数名 | 参数 | 描述 | 备注 |
|--------|------|------|------|
| `recognizeImage` | `{ image\\\_url }` | 调用多模态模型识图，返回 { color, type, features, description } | 前端拍照后上传云存储得 URL，再调此函数，结果填入描述 |
| `crossModalSearch` | `{ text\\\_query }` | 跨模态搜索：将文本转为向量，与 item_vectors 库相似度匹配，返回 item 列表 | 需预先离线建好向量库 |
| `getAddressByCoord` | `{ lat, lng }` | 逆向地理编码，返回详细地址 | 也可直接在前端调用腾讯地图 API |

---

## 四、数据流示例

### 4.1 发布流程（MVP）
1. 用户填写表单（类型、标题、地点、描述），选择图片上传至云存储，获得 fileID。
2. 调用 `publishItem` 云函数，传入信息及图片。
3. 云函数校验用户认证状态，创建 items 文档，`expire\\\_at = now + 7 days`，返回成功。
4. 首页列表刷新即可见新数据。

### 4.2 帮助/认领流程
1. 用户 A 在详情页点击“帮助”或“申请认领”，填写内容。
2. 调用 `applyAction`，创建 applications 文档，同时插入一条 `messages` 给发布者 B。
3. B 在个人中心看到消息，可点入处理，调用 `handleApplication` 同意或拒绝。

### 4.3 强化版一键识图
1. 拍照后前端调用 `wx.chooseMedia` 获取临时文件，上传至云存储。
2. 调用云函数 `recognizeImage`，传入文件 ID。
3. 云函数下载图片，转为 base64，请求多模态模型 API，得到 JSON 描述。
4. 返回给前端，自动填入描述、标签等字段。

### 4.4 强化版跨模态搜索
1. 用户输入“蓝色耳机”，调用 `crossModalSearch`。
2. 云函数将文本转为 CLIP 文本向量，在 `item\\\_vectors` 中检索相似图片向量，返回相关物品 ID 列表。
3. 根据 ID 查询 items，返回卡片信息。

---

## 五、安全与权限
- 所有操作需在云函数中校验 `openid` 身份的合法性。
- 发布、认领、帮助操作前校验 `user.status === "authorized"`。
- 编辑物品时校验 `publisher\\\_id === 当前用户`。
- 敏感数据（真实姓名、学号）仅管理员及本人可查看。
- 向量搜索、识图 API 调用需做频率限制和费用控制。

---

## 六、后续迭代建议
- 管理员后台页面（可简易搭建在云开发扩展能力中）。
- “关注的区域”个性化推荐（基于用户历史行为或定位）。
- 失物自动匹配：新发布时自动检索已有相反类型的物品并通知双方。
- 消息推送模板（微信服务通知）增强提醒。