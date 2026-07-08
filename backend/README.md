# 校园失物招领平台 - 后端服务

## 技术栈

- Spring Boot 3.2.0
- MyBatis-Plus 3.5.5
- MySQL 8.0+
- JWT 0.12.3
- Java 17

## 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+

## 快速开始

### 1. 配置数据库

创建数据库：

```sql
CREATE DATABASE example_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 修改配置

编辑 `src/main/resources/application.yml`，配置数据库连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/example_db?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: your_username
    password: your_password
```

### 3. 运行项目

```bash
cd backend
mvn spring-boot:run
```

服务将在 http://localhost:8080 启动。

## API 接口

### 用户认证

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/auth/login` | POST | 用户登录 |
| `/api/auth/user` | GET | 获取用户信息 |
| `/api/auth/certification` | POST | 提交认证申请 |
| `/api/auth/certification/{id}/review` | POST | 审核认证 |
| `/api/auth/profile` | POST | 更新用户资料 |

### 物品管理

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/items` | GET | 获取物品列表 |
| `/api/items/{id}` | GET | 获取物品详情 |
| `/api/items/my` | GET | 获取我的发布 |
| `/api/items` | POST | 发布物品 |
| `/api/items/{id}` | PUT | 编辑物品 |
| `/api/items/{id}` | DELETE | 删除物品 |
| `/api/items/{id}/resolve` | POST | 标记已解决 |

### 帮助与认领

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/applications` | POST | 发起帮助/认领申请 |
| `/api/applications/item/{itemId}` | GET | 获取物品的申请列表 |
| `/api/applications/my` | GET | 获取我的申请 |
| `/api/applications/{id}/handle` | POST | 处理申请 |

### 消息通知

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/messages` | GET | 获取消息列表 |
| `/api/messages/count` | GET | 获取未读消息数 |
| `/api/messages/{id}/read` | POST | 标记消息已读 |
| `/api/messages/read-all` | POST | 标记全部已读 |

## 项目结构

```
backend/
├── src/main/java/com/example/lostfound/
│   ├── LostFoundApplication.java    # 启动类
│   ├── controller/                  # 控制层
│   ├── service/                     # 服务层
│   ├── mapper/                      # 数据访问层
│   ├── entity/                      # 实体类
│   ├── config/                      # 配置类
│   └── util/                        # 工具类
├── src/main/resources/
│   ├── application.yml              # 应用配置
│   └── schema.sql                   # 数据库初始化脚本
└── pom.xml                          # Maven配置
```