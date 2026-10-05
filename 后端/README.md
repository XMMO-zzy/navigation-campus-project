# 通航校园 · 后端工程（campus-server）

> 本工程为「通航校园」小程序的**服务端**，严格遵循《11-系统概要设计文档》《12-数据库设计文档》
> 《13-API接口文档》实现。所有接口路径、字段、错误码**以文档为准**，未在文档中定义的写入接口
> 一律以「契约缺口登记」方式记录，不做臆造。

---

## 1. 技术栈

| 分类 | 选型 | 版本 |
|---|---|---|
| JDK | Java | 17 |
| 框架 | Spring Boot | 3.2.5 |
| ORM | MyBatis-Plus | 3.5.6 |
| 数据库 | MySQL | 8.0 |
| 缓存 | Redis | 7（可选，未接入/连接失败时自动熔断降级） |
| 迁移 | Flyway | 由 Spring Boot 3.2.5 托管（实测 9.22.3） |
| 鉴权 | jjwt | 0.12.5 |
| 接口文档 | Knife4j（OpenAPI 3） | 4.5.0 |
| 工具 | Hutool | 5.x |

> **Lombok 说明**：pom 中声明 Lombok 依赖，但**业务代码一律手写 getter/setter**，
> 以保证在不安装 Lombok 插件的 IDE / CI 环境也能正常编译阅读。

---

## 2. 模块划分（《11》§1.1 / §2.2）

```
campus-parent                    父 POM，统一依赖版本
├── campus-common                公共层：Result / PageResult / ErrorCode / Enums /
│                                JWT / UserContext / @RequireRole / 异常处理 /
│                                MyBatis-Plus 配置 / 幂等 / 通知契约
├── campus-account                M1 账号与认证：登录、学号认证、资料、隐私、黑名单
├── campus-content                M2 内容社区：动态、评论、话题、点赞收藏、首页流、搜索、公告
├── campus-message                M3 消息与协作：站内通知、未读角标、组队申请中心、队伍
├── campus-trade                  M4 二手交易：商品、订单状态机、求购、信用分
├── campus-event                  M5 活动与竞赛 + M6 找搭子：发布/报名/审核/日程/申请解锁
└── campus-server                 启动模块：聚合全部模块 + M7 平台支撑 + Flyway 脚本
```

### 模块依赖规则（《11》§2.1）

> **模块之间不得直接操作他域 Mapper**，跨模块访问一律通过 `api` 包门面接口。

| 门面接口 | 定义位置 | 实现位置 | 用途 |
|---|---|---|---|
| `UserFacade` | campus-account | campus-account | 用户展示信息（其它模块统一入口） |
| `CurrentUserProvider` | campus-common | campus-account | 实时读取 user_type/verify_status（防 JWT 快照过期） |
| `NotifySender` | campus-common | campus-message | 跨模块写站内通知 |
| `GoodsQueryFacade` | campus-content | campus-trade | 首页流「机会推荐」取在售商品 |
| `TeamQueryFacade` | campus-common | campus-message | 竞赛团队报名校验队伍与成员 |

> ⚠️ **契约接口一律定义在 campus-common**（如 `NotifySender`、`TeamQueryFacade`），
> 实现放在持有该数据的模块。这样使用方只依赖 common，避免任何模块间循环依赖。

### 模块依赖矩阵（**无环**）

| 模块 | 声明依赖 | 实际用到 |
|---|---|---|
| `campus-common` | — | — |
| `campus-account` | common | common |
| `campus-content` | common, account | common, account |
| `campus-message` | common, account, content | common, account |
| `campus-trade` | common, account, content | common, account, content |
| `campus-event` | common, account, content | common, account, content |
| `campus-server` | 全部业务模块 | 仅 modules/*（装配层） |

> 依赖方向为严格分层，**不存在任何双向依赖**。
> 说明：`campus-message` / `campus-trade` 对 `campus-content` 的依赖来自敏感词机审
> （`SensitiveWordChecker`）；若需进一步解耦，可将该检查器下沉到 common。

### 启动降级策略（P0 关键）

所有跨模块/外部依赖均通过 `ObjectProvider` **可选注入**，缺失时自动降级、不阻断启动：

| 依赖 | 缺失时行为 |
|---|---|
| Redis | 两级降级：①容器无 `StringRedisTemplate` Bean → 跳过；②Bean 在但连不上 → **熔断放行**（打一次 WARN，60s 后自动重试，恢复后自动切回）。仅影响「防重复提交」，不影响业务正确性 |
| `GoodsQueryFacade` | 首页流只返回动态，不含商品 |
| `TeamQueryFacade` | 竞赛团队报名明确报错（不伪造校验通过） |
| `NotifySender` | 落 `NoopNotifySender`，仅打调试日志 |

---

## 3. 目录结构

```
campus-server/src/main/java/com/tonghang/campus/
├── CampusApplication.java              启动类（scanBasePackages / @MapperScan / @EnableScheduling）
└── modules/m7/                         M7 平台支撑与治理（唯一直接放在启动模块的业务域，见《11》§1.1）
    ├── entity/    FileObject · Draft · Report · Feedback · SysConfig
    ├── mapper/    对应 Mapper
    ├── service/   FileService · DraftService · ReportService · FeedbackService
    ├── support/   JsonUtil
    └── controller/ FileController · DraftController · ReportController ·
                    FeedbackController · ConfigController

campus-server/src/main/resources/
├── application.yml                     主配置
└── db/migration/
    ├── V1__init_schema.sql             35 张表建表脚本（《12》§2 全量，已实测可执行）
    └── V2__init_dict.sql               字典初始化（sys_config / sensitive_word 等）
```

---

## 4. 接口清单（P0 已实现）

> Base URL：`http://<host>:8080/api/v1`
> 统一响应：`{ "code": 0, "msg": "ok", "data": {...} }`（失败 `code` 非 0，HTTP 状态码始终 200）
> 分页响应：`data = { list, total, page, size }`

### 4.1 M1 账号与认证（campus-account）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/auth/wx-login` | 微信登录（code2session） |
| POST | `/auth/phone-login` | 手机号登录（**短信通道未接入，返回 40007**） |
| POST | `/auth/refresh` | 刷新 accessToken |
| GET | `/users/me` | 我的资料 |
| PUT | `/users/me` | 更新资料（字段白名单） |
| GET/PUT | `/users/me/privacy` | 隐私设置 |
| GET/PUT | `/users/me/blacklist` | 黑名单 |
| POST | `/users/logout` | 退出登录 |
| POST | `/users/deactivate` | 注销账号（返回 15 天冷静期） |
| GET | `/users/{id}` | 他人主页（按隐私范围脱敏） |
| POST/DELETE | `/users/{id}/block` | 拉黑 / 解除 |
| POST | `/verify/apply` | 提交学号认证 |
| GET | `/verify/status` | 认证状态（四态 0/1/2/3） |

### 4.2 M2 内容社区（campus-content）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/feed?tab=` | 信息流（recommend / follow / activity / campus） |
| GET | `/home/feeds` | 首页推荐流（动态 + 商品） |
| GET | `/search` | 搜索 |
| GET | `/search/history` | 搜索历史（**契约缺口：返回空列表**） |
| POST | `/posts` | 发布动态（敏感词机审） |
| GET/PUT/DELETE | `/posts/{id}` | 详情 / 编辑 / 删除 |
| POST/DELETE | `/posts/{id}/like` | 点赞 / 取消 |
| POST/DELETE | `/posts/{id}/favorite` | 收藏 / 取消 |
| GET/POST | `/posts/{id}/comments` | 评论列表 / 发表（校验评论权限） |
| PUT | `/posts/{id}/dislike` | 不感兴趣 |
| GET | `/notices`、`/notices/{id}` | 官方公告 |

### 4.3 M3 消息与协作（campus-message）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/messages?box=&biz=` | 通知列表 |
| PUT | `/messages/read-all` | 全部已读 |
| PUT | `/messages/{id}/read` | 单条已读 |
| GET | `/messages/badge` | 未读角标（total/trade/team/interact/system/activity/competition/buddy） |
| GET/PUT | `/messages/settings` | 通知设置（**《12》无设置表，内存默认值**） |
| GET | `/team-applies?direction=` | 申请中心（received/sent/invited） |
| PUT | `/team-applies/{id}` | 处理申请（agree/reject/ask/withdraw） |
| POST | `/teams` | 创建队伍 |
| GET | `/teams/{id}` | 队伍详情 |
| GET | `/teams/my` | 我的队伍（**契约缺口：文档未定义**） |
| POST | `/teams/{id}/apply` | 申请加入队伍（**契约缺口：文档未定义**） |
| PUT | `/teams/{id}/members/{uid}` | 移除成员 / 转让队长 |
| GET | `/im/sign` | IM 签名（**通道未接入，返回 50001**） |
| GET | `/todos` | 待办聚合 |

### 4.4 M4 二手交易（campus-trade）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/goods` | 发布商品 |
| GET | `/goods` | 商品列表（category/cond/min/max/kw） |
| GET | `/goods/my?view=` | 我的商品 |
| GET/PUT/DELETE | `/goods/{id}` | 详情 / 编辑 / 下架 |
| POST | `/goods-wanted` | 发布求购 |
| POST | `/orders` | 下单 |
| GET | `/orders?role=` | 订单列表（buyer/seller） |
| POST | `/orders/{no}/seller-confirm` | 卖家确认 |
| POST | `/orders/{no}/face-confirm` | **面交双向确认** |
| POST | `/orders/{no}/cancel` | 取消订单 |
| GET | `/orders/{no}` | 订单详情 + 时间线 |
| POST | `/orders/{no}/review` | 互评（P2） |

**订单状态机**（《12》§2.4，保留 `20 待付款`）：

```
10 待卖家确认 ──卖家确认──→ 20 待付款 ──支付──→ 30 待面交
                     │                              │
                     └──(pay-channel-ready=false 时直接进入 30)──┘
                                                    ↓
                                        面交双方各确认一次
                                                    ↓
                              先确认 → 40 待对方确认 → 后确认 → 50 已完成
10/20/30 可取消 → 60 已取消；10 超 30 分钟 → 70 超时关闭
```

> P0 决策②：支付通道未接入，`campus.order.pay-channel-ready=false`，卖家确认后直接进 `30`；
> P1 接入后置 `true`，即回到文档定义的 `20 待付款`（30 分钟超时任务已就绪）。

### 4.5 M5 活动与竞赛（campus-event）

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/activities` | 认证用户 | 发布活动（普通用户 → 40003） |
| GET | `/activities?category=&status=` | 游客 | 活动列表 |
| GET | `/activities/{id}` | 游客 | 活动详情 |
| POST | `/activities/{id}/enroll` | 登录 | 报名（重复 40006、截止 40007） |
| PUT | `/enrolls/{id}` | 认证用户 | 组织者审核（approve/reject） |
| GET | `/activities/my-enrolls?view=` | 登录 | 我参与的活动（ongoing/history） |
| POST | `/competitions` | 认证用户 | 发布竞赛 |
| GET | `/competitions?level=&status=` | 游客 | 竞赛列表 |
| GET | `/competitions/calendar?month=` | 游客 | 竞赛日程日历 |
| GET | `/competitions/{id}` | 游客 | 竞赛详情 |
| POST | `/competitions/{id}/enroll` | 登录 | 报名（可关联 teamId） |
| PUT | `/competition-enrolls/{id}/result` | 登录 | 提交成果（P2） |
| GET | `/competitions/my-enrolls` | 登录 | 我的参赛记录 + 成果 |

### 4.6 M6 找搭子（campus-event）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/buddy-posts` | 发布搭子帖 |
| GET | `/buddy-posts?type=&tag=&femaleOnly=` | 搭子列表（女性模式） |
| GET | `/buddy-posts/my?view=` | 我的（published/favorite/matched） |
| GET | `/buddy-posts/{id}` | 详情（发布者可见申请列表） |
| POST | `/buddy-posts/{id}/apply` | 申请加入（重复 40006） |
| PUT | `/buddy-applies/{id}` | 处理申请（**契约缺口实现**，agree/reject/withdraw） |

> **联系方式解锁**（《10》FR-M6-04）：仅「发布者本人」或「我的申请已通过」返回明文，
> 否则 `contact = null` 且 `contactUnlocked = false`。

### 4.7 M7 平台支撑（campus-server）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/files/upload` | 文件登记（≤10MB，jpg/png/webp/gif 白名单，超限 40010） |
| POST | `/files/biz-types` | 文件业务类型字典 |
| GET/PUT/DELETE | `/drafts/{type}` | 草稿读取 / 保存 / 清理（type 1~5） |
| POST | `/reports` | 举报（响应含 `slaHours: 24`） |
| GET | `/reports/my` | 我的举报记录（含 `canAppeal`） |
| GET/POST | `/feedback` | 帮助与反馈 |
| GET | `/configs` | 前端字典（sys_config） |

---

## 5. 错误码（《13》§2，维护于 `common/api/ErrorCode`）

| code | 含义 |
|---|---|
| 0 | 成功 |
| 40001 | 未登录或登录已失效 |
| 40002 | 登录状态已过期 |
| 40003 | 权限不足，需完成学号认证 |
| 40004 | 参数校验失败 |
| 40005 | 资源不存在 |
| 40006 | 请勿重复操作 |
| 40007 | 当前状态不允许该操作 |
| 40008 | 内容含违规信息 |
| 40009 | 操作太频繁 |
| 40010 | 文件类型或大小不符合要求 |
| 41001 | 学号认证审核中 |
| 41002 | 学号认证未通过 |
| 42001 | 微信授权调用失败 |
| 50000 | 服务开小差了 |
| 50001 | 第三方服务异常 |

---

## 6. 认证与权限（三层判定，《11》§3.1）

1. **登录态**：`AuthInterceptor` 宽松解析 JWT（游客可读接口不拦截），写入 `UserContext`（ThreadLocal）。
2. **注解判定**：`@RequireRole(USER)` 登录即可；`@RequireRole(VERIFIED)` 需 `user_type = 2`。
3. **实时刷新**：`RequireRoleAspect` 在判定前通过 `CurrentUserProvider` **回读数据库**
   的最新 `user_type` / `verify_status`，避免 JWT 快照过期导致的越权。

> 认证口径（决策①）：采用《10》FR-M1-06 的**四态** `verify_status(0 未认证/1 认证中/2 已认证/3 未通过)`；
> `VERIFIED` 判定等价于 `user_type = 2`。组队等协作功能对**普通用户开放**（《10》§3.1 优先于《11》）。

---

## 7. 本地启动

```bash
# 1. 准备 MySQL 8.0 并创建库
mysql -uroot -p -e "CREATE DATABASE campus DEFAULT CHARACTER SET utf8mb4;"

# 2. （可选）启动 Redis；不启动也能跑（幂等自动降级）

# 3. 编译打包
mvn -T 1C clean package -DskipTests

# 4. 运行（Flyway 会自动建表 + 初始化字典）
java -jar campus-server/target/campus-server.jar
```

启动后访问：

- 接口文档（Knife4j）：http://localhost:8080/api/v1/doc.html
- OpenAPI JSON：http://localhost:8080/api/v1/v3/api-docs

### 可配置项（`application.yml` → `campus.*`）

| 配置键 | 默认 | 说明 |
|---|---|---|
| `campus.jwt.secret` | 开发占位值 | **生产必须用环境变量 `JWT_SECRET` 注入** |
| `campus.jwt.access-token-ttl` | 7200 | access token 有效期（秒） |
| `campus.wx.app-id` / `app-secret` | 空 | 为空时微信登录走 `dev_<code>` 占位，便于本地联调 |
| `campus.order.pay-timeout-minutes` | 30 | 待付款超时（分钟） |
| `campus.order.confirm-deadline-hours` | 72 | 待确认截止（小时） |
| `campus.order.pay-channel-ready` | false | P0=false（跳过 20 待付款）；P1 支付接入后置 true |
| `campus.file.cos-enabled` | false | false 时文件仅登记本地占位 URL |
| `campus.file.local-prefix` | `/static/upload/` | 本地占位 URL 前缀 |

环境变量覆盖：`DB_HOST` `DB_PORT` `DB_NAME` `DB_USER` `DB_PASSWORD`
`REDIS_HOST` `REDIS_PORT` `REDIS_DB` `REDIS_PASSWORD` `JWT_SECRET` `WX_APP_ID` `WX_APP_SECRET`。

---

## 8. 定时任务

| 任务 | 周期 | 说明 |
|---|---|---|
| `OrderTimeoutJob` | 每 5 分钟 | 关闭超时未付款订单（10 → 70） |

> 依赖 `@EnableScheduling`（已在 `CampusApplication` 开启）。
> 单实例部署即可；多实例部署时需引入分布式锁（P1）。

---

## 9. 契约缺口登记（交付说明）

以下条目《13-API接口文档》**未定义**，但业务闭环需要，已按**最小语义**实现并在此登记，
待双端（BE-D + FE-A）评审确认，**未新增任何数据库字段**：

| # | 缺口 | 处理方式 | 影响 |
|---|---|---|---|
| 1 | 「关注」相关接口（follow / unfollow / 关注流） | **不实现**。`/feed?tab=follow` 返回空分页；`GET /users/{id}` 的 `followed` 固定 false；评论 `FOLLOWERS` 权限拒绝并附「待契约评审」说明 | 关注功能不可用 |
| 2 | 组队「申请加入队伍」写入接口 | 实现 `POST /teams/{id}/apply`，仅用 `team_apply` 现有列 | 低 |
| 3 | 「我的队伍」查询接口 | 实现 `GET /teams/my` | 低 |
| 4 | 搭子申请**处理**接口（FR-M6-04 需审批入口） | 实现 `PUT /buddy-applies/{id}`（agree/reject/withdraw） | 低 |
| 5 | 通知设置持久化（《12》无 settings 表） | `GET/PUT /messages/settings` 返回内存默认值，**未建表** | 设置重启丢失 |
| 6 | IM 私信签名接口 | `GET /im/sign` 抛 50001「IM 私信通道尚未接入」 | 私信不可用 |
| 7 | 手机号登录（短信通道） | `POST /auth/phone-login` 校验后抛 40007「短信通道尚未接入」 | 仅微信登录可用 |
| 8 | 文件真实上传（multipart → COS） | P0 仅做「登记 + 本地占位 URL」；对象存储接入后补二进制上传 | 需前端直传或暂用占位 |
| 9 | 收藏列表跨域聚合（`/goods/my?view=favorite`） | 返回空列表（收藏由 content 域 `favorite` 多态表维护，聚合待装配层实现） | 收藏视图暂空 |
| 10 | 管理端 `/admin/**` 全部接口 | 不在 P0 范围（《13》§3.8 明示另行维护） | 审核走数据库直改 |

---

## 10. 交付验收须知（**重要**）

> ⚠️ **当前开发环境未安装 JDK / Maven**，无法在本机执行 `mvn compile` 做编译验证。
> 已完成的验证方式为：**结构自检**（包名与目录一致性、括号配对、模块依赖方向、跨模块引用完整性）。
> 请按以下步骤完成编译验收：

```bash
# 前置：JDK 17+、Maven 3.8+
java -version && mvn -v

# 编译（首次会下载依赖，耗时较长）
mvn -T 1C clean compile

# 若通过，继续打包与启动
mvn -T 1C clean package -DskipTests
java -jar campus-server/target/campus-server.jar
```

**已知需要人工确认的点**：

1. Flyway `V1__init_schema.sql` 含 35 张表，首次执行前请确认库为空或已 `baseline-on-migrate`。
   （已在临时库实测：35 张表全部建成、V2 字典写入 8 条 sys_config，脚本无语法错误。）
2. MyBatis-Plus `@TableLogic` 对 `deleted_at` 使用 `NOW()` 作为删除值，
   需确认 MySQL 版本下 `delval = "NOW()"` 生效（8.0 可用；若报错改为时间戳）。
3. `campus-content` 的 `SensitiveWordChecker` 被 message / trade 复用，形成对 content 的依赖；
   若团队认为横切能力不应挂在 content，可下沉到 `campus-common`（属重构，不影响契约）。
