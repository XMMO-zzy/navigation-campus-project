# 13 · API 接口文档

> **文档编号**：13 ｜ **版本**：v1.0 ｜ **日期**：2026-09-29 ｜ **状态**：评审稿（Apifox 契约基线）
> **上游**：《10-需求文档》FR 编号 ／《12-数据库设计》表字段 ｜ **下游**：前后端开发与联调（《19-团队分工与联调方案》§4）

---

## 1. 通用约定

| 项 | 约定 |
|---|---|
| Base URL | `https://<domain>/api/v1`（dev: `http://dev.example.local:8080/api/v1`） |
| 协议 | HTTPS（小程序强制）；Content-Type: `application/json` |
| 鉴权 | `Authorization: Bearer <accessToken>`；游客不携带 |
| 字段命名 | camelCase；时间 ISO8601 +08:00，如 `2026-10-12T09:00:00+08:00` |
| 分页 | 请求 `page`（从 1）/ `size`（默认 20，≤50）；响应 `data.list / total / page / size` |
| 幂等 | 写操作带 `clientId`（前端 UUID）防重复提交 |
| 频控 | 登录 10 次/分，发布 5 次/分，超限返回 40009 |

**统一响应体**

```json
{ "code": 0, "msg": "ok", "data": { } }        // 成功
{ "code": 40004, "msg": "标题不能为空", "data": null } // 失败
```

## 2. 错误码（全局唯一，维护于 common 包 ErrorCode）

| code | 含义 | 前端处理建议 |
|---|---|---|
| 0 | 成功 | — |
| 40001 | 未登录 / Token 无效 | 清除本地态，弹登录引导 |
| 40002 | Token 过期 | 静默刷新重试一次，失败转 40001 |
| 40003 | 权限不足（需认证用户） | 引导去认证页 |
| 40004 | 参数校验失败 | 表单错误提示（msg 携带字段说明） |
| 40005 | 资源不存在 | 空态/返回列表 |
| 40006 | 重复操作（重复报名/已收藏等） | Toast 提示 |
| 40007 | 状态冲突（已截止/订单状态不允许） | Toast + 刷新详情 |
| 40008 | 内容命中敏感词（拦截） | 高亮提示修改 |
| 40009 | 频率超限 | Toast "操作太频繁" |
| 40010 | 文件类型/大小不合规 | 引导重传 |
| 41001 | 学号认证审核中 | 认证页置为"认证中"态 |
| 41002 | 认证未通过 | 展示驳回原因 + 重新提交入口 |
| 42001 | 微信授权/服务通知调用失败 | 引导重试 |
| 50000 | 服务器内部错误 | Toast "服务开小差了" + 上报 |
| 50001 | 第三方依赖失败（IM/COS/审核） | 同上，附带 traceId |

## 3. 接口清单

> 完整出参与字段校验以 Apifox 为准；本文列出核心接口，标 ◆ 的附请求/响应示例。

### 3.1 M1 账号与认证（BE-D）

| 方法 路径 | 说明 | 需求 |
|---|---|---|
| POST `/auth/wx-login` ◆ | 微信一键授权登录（code 换 openID，首登建号） | FR-M1-01 |
| POST `/auth/phone-login` | 手机号验证码登录（备用通道） | FR-M1-02 |
| POST `/auth/refresh` | 刷新 accessToken | FR-M1-04 |
| GET `/users/me` | 我的主页头部：资料卡 + 数据概览 + 认证状态 | FR-M7-07 |
| PUT `/users/me` | 编辑资料（头像/昵称/简介/专业/技能/爱好） | FR-M7-07 |
| POST `/verify/apply` ◆ | 提交学号认证（学号+姓名+校园卡照片） | FR-M1-05 |
| GET `/verify/status` | 认证四态 + 未通过原因 | FR-M1-06 |
| GET `/users/{id}` | 他人主页（受隐私设置约束） | FR-M2-11 |
| POST `/users/logout` | 退出登录（清除服务端会话） | FR-M1-07 |
| POST `/users/deactivate` | 账号注销（冷静期 15 天） | FR-M1-08 |

### 3.2 M2 内容与信息流（BE-E / 前端 FE-B）

| 方法 路径 | 说明 | 需求 |
|---|---|---|
| GET `/feed?tab=` ◆ | 动态页信息流：`recommend/follow/activity/campus` | FR-M2-01/02 |
| GET `/home/feeds` | 首页推荐流（机会推荐 + 信息推荐） | FR-M2-08/09 |
| GET `/todos` | 首页重要消息待办（即将截止/报名开始/规则更新/协作待办） | FR-M3-05 |
| POST `/posts` ◆ | 发布动态（走敏感词机审） | FR-M2-04 |
| GET `/posts/{id}` | 动态详情 | FR-M2-02 |
| PUT/DELETE `/posts/{id}` | 编辑 / 删除（仅本人，删为软删） | FR-M7-07 |
| POST `/posts/{id}/like` ｜ DELETE 同路径 | 点赞 / 取消 | FR-M2-03 |
| POST `/posts/{id}/favorite` ｜ DELETE 同路径 | 收藏 / 取消（触发截止提醒联动） | FR-M2-03 |
| GET `/posts/{id}/comments` ｜ POST 同路径 | 评论列表 / 发表评论（校验评论权限） | FR-M2-03 |
| GET `/search?kw=&type=` | 全局搜索（动态/帖子/重要消息/校园话题） | FR-M2-07 |
| GET/DELETE `/search/history` | 搜索历史（DELETE 支持单条 `?id=` 与全部清空） | FR-M2-07 |
| GET `/search/hot` | 热门搜索词（Redis ZSet） | FR-M2-07 |
| PUT `/posts/{id}/dislike` | 不感兴趣（负反馈入画像） | FR-M2-11 |
| POST `/users/{id}/block` ｜ DELETE | 屏蔽 / 取消屏蔽 | FR-M2-11 |
| GET `/notices` | 官方通知/规则更新/平台公告列表 | FR-M2-09 |

### 3.3 M3 消息与协作（BE-F / 前端 FE-C）

| 方法 路径 | 说明 | 需求 |
|---|---|---|
| GET `/messages?box=&biz=` ◆ | 通知列表：box=all/unread，biz=activity/competition/trade/buddy/system | FR-M3-01 |
| PUT `/messages/read-all` | 全部已读 | FR-M3-02 |
| GET `/messages/badge` | 各分类未读数（Tab 角标聚合） | FR-M3-08 |
| GET/PUT `/messages/settings` | 消息分类开关 + 免打扰设置 | FR-M3-02 |
| GET `/im/sign` | 获取 TIM userSig（私信/群聊通道） | FR-M3-03 |
| GET `/team-applies?direction=` ◆ | 申请中心：收到的申请 / 我的申请 / 收到的邀请 | FR-M3-04 |
| PUT `/team-applies/{id}` | 处理：agree / reject / ask（补充询问）/ withdraw（撤回） | FR-M3-04 |
| POST `/teams` | 创建队伍（活动/竞赛组队） | US-03a |
| GET `/teams/{id}` | 队伍详情（成员/分工/公告/进度） | FR-M3-04 |
| PUT `/teams/{id}/members/{uid}` | 移除成员 / 转让队长（组织者权限） | FR-M5-01 |

### 3.4 M4 二手交易（BE-G）

| 方法 路径 | 说明 | 需求 |
|---|---|---|
| POST `/goods` ◆ | 发布商品（3 步向导数据一次提交；含图片审核结果） | FR-M4-01 |
| GET `/goods?category=&cond=&min=&max=` | 商品列表（两栏瀑布流 + 筛选） | FR-M4-02 |
| GET `/goods/my?view=` | 我的视图：published/favorite/orders | FR-M4-03 |
| GET `/goods/{id}` | 商品详情（联系方式按权限脱敏） | FR-M4-02 |
| POST `/goods-wanted` | 发布求购（触发反向推送） | FR-M4-06 |
| POST `/orders` ◆ | 下单（买家视角创建，待卖家确认） | FR-M4-04 |
| POST `/orders/{no}/seller-confirm` | 卖家确认（同意交易） | FR-M4-04 |
| POST `/orders/{no}/face-confirm` ◆ | **面交双向确认**（买卖双方各调一次） | FR-M4-04 |
| POST `/orders/{no}/cancel` | 取消订单（状态机校验） | FR-M4-05 |
| GET `/orders/{no}` | 订单详情 + 状态时间线 | FR-M4-04 |
| POST `/orders/{no}/review` | 互评 + 信用分变更（P2） | FR-M4-07 |

### 3.5 M5 活动与竞赛（BE-G）

| 方法 路径 | 说明 | 需求 |
|---|---|---|
| POST `/activities` ◆ | 发布活动（仅认证用户，服务端校验） | FR-M5-01 |
| GET `/activities?category=&status=` | 活动列表（申请参加/表明意愿） | FR-M5-02 |
| GET `/activities/{id}` | 活动详情（时间/人数/地点/要求/规则） | FR-M5-02 |
| POST `/activities/{id}/enroll` | 报名（重复拦截 40006、截止拦截 40007） | FR-M5-05 |
| PUT `/enrolls/{id}` | 组织者审核报名：approve/reject | FR-M5-02 |
| GET `/activities/my-enrolls` | 我参与的活动（进行中/历史） | FR-M7-07 |
| POST `/competitions` | 发布竞赛（仅认证用户） | FR-M5-03 |
| GET `/competitions?level=&status=` | 竞赛列表 + 多维筛选 | FR-M5-04 |
| GET `/competitions/calendar?month=` | 竞赛日程日历（报名/初赛/决赛/截止） | FR-M5-04 |
| POST `/competitions/{id}/enroll` | 报名竞赛（可关联 team_id） | FR-M5-05 |
| PUT `/competition-enrolls/{id}/result` | 提交成果（获奖/作品资料，P2） | FR-M5-07 |
| GET `/competitions/my-enrolls` | 我的参赛记录 + 成果展示 | FR-M7-07 |

### 3.6 M6 找搭子（BE-G）

| 方法 路径 | 说明 | 需求 |
|---|---|---|
| POST `/buddy-posts` | 发布搭子帖（类型/标签/展示信息/gender_limit） | FR-M6-01 |
| GET `/buddy-posts?type=&tag=&femaleOnly=` | 搭子列表（爱好筛选、女性模式） | FR-M6-02/05 |
| GET `/buddy-posts/my?view=` | published/favorite/matched（我滴过的） | FR-M6-03 |
| POST `/buddy-posts/{id}/apply` | 申请加入 → 对方同意后解锁联系方式 | FR-M6-04 |

### 3.7 M7 支撑与治理（BE-D / 全员接入）

| 方法 路径 | 说明 | 需求 |
|---|---|---|
| POST `/files/upload` | 文件上传（返回 file_id/url + 审核状态，≤10MB，白名单） | FR-M7-04 |
| GET/PUT `/drafts/{type}` | 草稿读取/保存（五类，JSON payload） | FR-M2-10 |
| POST `/reports` ◆ | 举报（原因 + 证据材料） | FR-M7-03 |
| GET `/reports/my` | 我的举报记录（进度/结果/申诉入口） | FR-M7-06 |
| GET/PUT `/users/me/privacy` | 隐私设置（资料/动态可见范围、联系权限、个性化推荐开关） | FR-M7-06 |
| GET/PUT `/users/me/blacklist` | 黑名单管理 | FR-M7-06 |
| GET `/configs` | 前端字典（品类/成色/话题等 sys_config） | — |
| GET/POST `/feedback` | 帮助与反馈 | FR-M7-06 |

### 3.8 管理端 `/admin/**`（BE-D）

审核队列（动态/商品/搭子）、学号认证审核、举报工单处理、用户封禁/禁言、信用分调整、官方通知发布、竞赛活动数据看板、审计日志查询。（权限：管理员角色，双因素登录，另行维护接口，本节从略。）

## 4. 核心接口示例

### ◆ POST /auth/wx-login

```json
// Request
{ "code": "0a3xWm000vJ9Q13...", "clientId": "c-8f2a" }
// Response data
{
  "accessToken": "eyJhbGciOi...", "refreshToken": "rt-9d8e...",
  "expiresIn": 7200,
  "user": { "id": 10001, "nickname": "小航", "avatar": "https://cos.../a.jpg",
            "userType": 1, "verifyStatus": 0 }
}
```

### ◆ POST /verify/apply

```json
// Request
{ "studentNo": "2023011234", "realName": "杨航",
  "campusCardFileId": 88001, "clientId": "c-71bc" }
// Response data
{ "verifyStatus": 1 }   // 转为"认证中"；审核通过后 userType=2
```

### ◆ GET /feed?tab=recommend&page=1&size=20

```json
// Response data
{ "list": [
  { "contentType": 1, "contentId": 501, "title": null,
    "user": { "id": 10001, "nickname": "小航", "schoolTag": "XX学院", "followed": false },
    "text": "求组队打互联网+，缺后端…", "images": ["https://cos.../1.jpg"],
    "topics": ["竞赛", "组队"],
    "cnt": { "like": 12, "comment": 3, "favorite": 5 },
    "link": { "type": 4, "id": 301, "title": "互联网+大赛", "deadlineLeft": "3天" },
    "createdAt": "2026-11-20T14:00:00+08:00" },
  { "contentType": 2, "contentId": 2003, "title": "九成新公路车",
    "price": 699.00, "cond": "九成新", "image": "https://cos.../b.jpg",
    "cnt": { "favorite": 21 }, "createdAt": "2026-11-20T10:00:00+08:00" }
], "total": 128, "page": 1, "size": 20 }
```

### ◆ POST /orders

```json
// Request
{ "goodsId": 2003, "clientId": "c-ee01" }
// Response data
{ "orderNo": "G20261120A8E2", "status": 10, "statusText": "待卖家确认",
  "payTimeoutAt": "2026-11-20T15:30:00+08:00" }
```

### ◆ POST /orders/{no}/face-confirm

```json
// Request
{ "clientId": "c-ee02" }   // 同一用户重复确认 → 40006
// Response data（后确认方触发）
{ "status": 50, "statusText": "已完成",
  "buyerConfirmAt": "2026-11-21T12:03:00+08:00",
  "sellerConfirmAt": "2026-11-21T12:03:31+08:00" }
// 先确认方响应：{ "status": 40, "statusText": "待对方确认" }
```

### ◆ GET /team-applies?direction=received

```json
// Response data
{ "list": [
  { "id": 9001, "team": { "id": 77, "name": "互联网+后端小队", "bizType": 4,
      "bizTitle": "互联网+大赛", "quota": 5, "current": 3 },
    "user": { "id": 10086, "nickname": "阿鲤", "schoolTag": "XX学院",
      "skills": ["Java", "MySQL"], "bio": "大二，打过蓝桥杯" },
    "message": "熟悉 Spring Boot，可投入 10h/周",
    "status": 0, "createdAt": "2026-11-22T09:00:00+08:00" }
], "total": 1 }
```

### ◆ POST /activities

```json
// Request（普通用户调用 → 40003）
{ "title": "迎新晚会志愿者招募", "category": 1, "capacity": 30,
  "place": "大学生活动中心", "startAt": "2026-11-30T18:30:00+08:00",
  "endAt": "2026-11-30T21:00:00+08:00", "enrollStart": "2026-11-22T00:00:00+08:00",
  "enrollDeadline": "2026-11-28T23:59:59+08:00", "needReview": true,
  "content": "负责签到与引导…", "clientId": "c-ab12" }
```

### ◆ POST /reports

```json
// Request
{ "targetType": 1, "targetId": 501, "reasonType": 2,
  "desc": "疑似刷单诈骗", "evidenceFileIds": [88100, 88101] }
// Response data
{ "reportId": 6601, "status": 0, "slaHours": 24 }
```

## 5. 契约管理

1. Apifox 维护全部接口（含 §3 全表出参），本文为基线快照；变更须双端评审（BE-D + FE-A）；
2. Mock 优先：FE 依赖的接口在开发前 3 天提供 Mock（见《19》§4 联调节奏）；
3. 破坏性变更（删字段/改语义）需升版本 `/api/v2`，禁止原地修改；
4. 灰度期间 `/api/v1` 冻结，仅允许加法（新增可选字段/接口）。
