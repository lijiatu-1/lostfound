# API 契约

基址为 `/api`。除了登录、公开物品列表/详情、公开评论和公开物品图片，其余接口都需要 `Authorization: Bearer <JWT>`。错误返回 `{ "success": false, "message": "..." }`，并使用 400、401、403、404、409、429 或 503 状态码。

## 登录和认证

| 方法和路径 | 请求 / 返回 |
| --- | --- |
| `POST /auth/login` | `{code}` → `{token,user,unreadMessageCount}`。只有 dev 允许 `mock_` code |
| `GET /auth/user` | 当前用户 |
| `POST /auth/certification` | `{realName,studentId,cardPhotoAssetId}` |
| `GET /auth/certifications/pending` | 管理员待审核列表 |
| `POST /auth/certification/{id}/review` | 管理员提交 `{action:"accept"|"reject",reviewMsg?}` |

## 物品和图片

| 方法和路径 | 请求 / 返回 |
| --- | --- |
| `GET /items?type=&category=&keyword=&page=&pageSize=` | `{items,total,page,pageSize}`；只含公示中且未过期的物品 |
| `GET /items/{id}` | 详情；不会出现电话和经纬度 |
| `GET /items/my` | 本人的发布 |
| `POST /items` | `{type,title,description,locationName,category?,tags?,imageAssetIds?}` |
| `PUT /items/{id}` | 相同可编辑字段；仅发布者、仅 active |
| `POST /items/{id}/resolve` | 发布者结案，关闭私信 |
| `POST /items/{id}/reopen` | 发布者将 processing 重新开放，关闭原会话 |
| `POST /items/{id}/renew` | active 临期或 expired 延期 |
| `DELETE /items/{id}` | 逻辑删除，保留历史申请/会话 |
| `POST /assets` | multipart `file` + `purpose=item|certification` → `{assetId,url,purpose}` |
| `GET /assets/{id}/content` | 物品图公开；校园卡图仅本人/管理员凭 JWT 下载 |

`images` 字段在物品响应中是资产 ID 的 JSON 数组字符串。客户端根据 ID 拼出 `/api/assets/{id}/content`。发布和编辑不接受任意图片 URL、电话或坐标，公开文本也拒绝填写手机号；旧帖公开文本中的手机号在详情和搜索/发现列表中遮盖。

## 申请、私信和通知

| 方法和路径 | 请求 / 返回 |
| --- | --- |
| `POST /applications` | 仅 `{itemId,content}`；found 自动 claim，lost 自动 help |
| `GET /applications/item/{itemId}` | 仅发布者可查看 |
| `GET /applications/my` | 当前用户的申请 |
| `POST /applications/{id}/handle` | `{action:"accept"|"reject"}`；批准返回 `conversationId` |
| `GET /conversations` | `{conversations:[...]}`，仅本人参与的会话 |
| `GET /conversations/{id}/messages?limit=50` | `{conversation,messages}` |
| `POST /conversations/{id}/messages` | `{content}`；仅 open 且物品 processing 可发 |
| `POST /conversations/{id}/read` | 标记已读 |
| `GET /messages` | 系统通知列表 |
| `GET /messages/count` | `{count,notificationCount,conversationCount}` |

`POST /ai/recognize` 仅接受 `{assetId}`，验证本人、已认证用户和物品图用途；启用后默认每人每天最多 5 次。未配置返回 503，超额返回 429，第三方失败返回 502；不接受 `imageUrl`。
