# ArtFetch 用户、角色、权限与功能关系 PRD

## 文档信息

| 项 | 内容 |
|---|---|
| 当前版本 | v2 |
| 文档状态 | 需求评审中 |
| 最后更新日期 | 2026-04-25 |
| 关联文档 | `docs/prd-evaluation.md` |

## 版本记录

| 版本 | 日期 | 变更说明 |
|---|---|---|
| v1 | 2026-04-25 | 初版，定义用户、角色、权限、功能关系，覆盖当前已实现采集功能和后续艺术品评估功能，并给出推荐实现原理。 |
| v2 | 2026-04-25 | 将推荐鉴权框架调整为 Sa-Token，补充 Sa-Token 的后端实现方式、注解示例、Token 传递方式和选型说明。 |

## 1. 背景

当前 ArtFetch 已实现艺术品数据采集和管理能力，包括检索任务、图片下载、成交价补充、艺术品筛选、详情查看和 Excel 导出。系统目前没有应用用户、角色、权限体系，所有前端页面和后端接口默认可访问。

随着艺术品评估功能加入，系统会出现管理员、专家、审核人等不同角色。专家之间还要求评估内容互不可见，审核人可以驳回某个专家对某件艺术品的单条评估。因此，需要新增一套统一的用户、角色、权限与功能关系设计，既覆盖已有功能，也支撑后续评估流程。

## 2. 目标

- 定义系统用户、角色、权限和功能之间的关系。
- 明确当前已实现功能在权限体系中的归属。
- 明确艺术品评估功能中管理员、专家、审核人的权限边界。
- 支持多专家评估中专家互不可见。
- 支持审核人按“某专家 + 某艺术品”的单条评估进行驳回。
- 为后续代码实现提供推荐的数据模型、权限编码和基于 Sa-Token 的鉴权方式。

## 3. 当前实现现状

### 3.1 当前已实现前端页面

| 页面 | 路径 | 说明 |
|---|---|---|
| 检索任务 | `/tasks` | 创建、查看、启动、暂停、恢复、取消、删除任务，查看失败记录并重试 |
| 艺术品数据 | `/artworks` | 按任务、关键词、作者、拍卖日期、拍品编号、高清图状态筛选艺术品 |
| 艺术品详情 | `/artworks/:id` | 查看单件艺术品详情、图片、拍卖信息等 |

### 3.2 当前已实现后端 API

任务相关：

```http
POST /api/tasks
GET /api/tasks
GET /api/tasks/{id}
POST /api/tasks/{id}/start
POST /api/tasks/{id}/pause
POST /api/tasks/{id}/resume
POST /api/tasks/{id}/cancel
DELETE /api/tasks/{id}
GET /api/tasks/{id}/failures
POST /api/tasks/{id}/failures/retry
POST /api/tasks/{taskId}/failures/{failureId}/retry
```

艺术品相关：

```http
GET /api/artworks
GET /api/artworks/{id}
GET /api/artworks/{id}/original-image
GET /api/artworks/{id}/hd-image
POST /api/artworks/{id}/original-image/redownload
POST /api/artworks/{id}/hd-image/redownload
POST /api/artworks/{id}/transaction-price/supplement
GET /api/artworks/export
```

### 3.3 当前未实现内容

- 未实现应用用户登录。
- 未实现角色管理。
- 未实现权限管理。
- 未实现菜单级权限。
- 未实现接口级权限。
- 未实现数据范围权限。
- 未实现专家、审核人等业务角色。

## 4. 核心概念

### 4.1 用户 User

用户是可以登录 ArtFetch 并执行功能操作的人。

用户可以被分配一个或多个角色。用户是否能执行某个功能，由其角色拥有的权限决定。

### 4.2 角色 Role

角色是一组权限的集合。

示例：

- 系统管理员
- 数据采集员
- 数据查看员
- 评估管理员
- 专家
- 审核人

### 4.3 权限 Permission

权限是系统中最小的可授权能力。

建议采用权限编码表示，例如：

- `task:create`
- `artwork:export`
- `evaluation:review:submit`
- `evaluation:audit:reject`

权限可以绑定到菜单、按钮、接口或数据范围。

### 4.4 功能 Function

功能是用户可感知的业务能力，例如“创建采集任务”“导出艺术品”“提交专家评估”“驳回单条专家评估”。

功能通常需要一个或多个权限支撑。

### 4.5 数据范围 Data Scope

数据范围用于控制用户能看到哪些数据。

在评估模块中，数据范围非常重要：

- 专家只能看到分配给自己的评估项目。
- 专家只能看到自己的专家评估记录。
- 专家不能看到其他专家对同一艺术品的评分、评语和估价。
- 审核人可以看到待审核项目下所有专家评估结果。
- 管理员可以看到全部评估项目和全部专家评估结果。

## 5. 用户类型设计

### 5.1 内部用户

内部用户是系统运营和管理人员。

典型角色：

- 系统管理员
- 数据采集员
- 数据查看员
- 评估管理员
- 审核人

### 5.2 专家用户

专家用户参与艺术品评估。

典型权限：

- 查看分配给自己的评估项目。
- 查看自己需要评估的艺术品。
- 提交自己的专家评估。
- 修改被驳回的单条专家评估。

专家用户不应拥有以下权限：

- 查看其他专家评分。
- 查看其他专家评语。
- 查看其他专家最终估价。
- 审核评估项目。
- 管理评估指标定义。

### 5.3 初期简化方案

如果第一阶段不做完整登录体系，可以先使用文本字段：

- `expertName`
- `auditorName`
- `createdByName`

但接口设计和数据模型应预留：

- `expertId`
- `auditorId`
- `createdBy`

推荐第一阶段仍优先实现基础用户表和登录，否则专家互不可见、单条驳回通知和审计追踪会比较脆弱。

## 6. 推荐角色设计

### 6.1 系统管理员 ADMIN

拥有系统全部权限。

适用人群：

- 系统维护人员。
- 产品或业务负责人。

### 6.2 数据采集员 DATA_OPERATOR

负责创建和管理采集任务。

适用人群：

- 负责从 Artron 抓取数据的运营人员。

### 6.3 数据查看员 DATA_VIEWER

负责查看和导出艺术品数据。

适用人群：

- 只需要查看艺术品和导出数据的业务人员。

### 6.4 评估管理员 EVALUATION_MANAGER

负责创建评估项目、配置评估指标、分配专家和提交审核。

适用人群：

- 评估项目负责人。

### 6.5 专家 EXPERT

负责对分配给自己的艺术品进行评估。

适用人群：

- 外部或内部艺术品评估专家。

### 6.6 审核人 AUDITOR

负责审核专家评估结果。

适用人群：

- 评估质量控制人员。
- 评估负责人。

## 7. 权限编码设计

### 7.1 任务权限

| 权限编码 | 权限名称 | 对应功能 |
|---|---|---|
| `task:view` | 查看任务 | 查看任务列表、任务详情 |
| `task:create` | 创建任务 | 创建检索、图片、成交价等任务 |
| `task:start` | 启动任务 | 启动任务 |
| `task:pause` | 暂停任务 | 暂停任务 |
| `task:resume` | 恢复任务 | 恢复任务 |
| `task:cancel` | 取消任务 | 取消任务 |
| `task:delete` | 删除任务 | 删除任务 |
| `task:failure:view` | 查看失败记录 | 查看任务失败记录 |
| `task:failure:retry` | 重试失败记录 | 重试失败记录 |

### 7.2 艺术品数据权限

| 权限编码 | 权限名称 | 对应功能 |
|---|---|---|
| `artwork:view` | 查看艺术品 | 查看艺术品列表、详情 |
| `artwork:image:view` | 查看图片 | 查看原图、高清图 |
| `artwork:image:redownload` | 重新下载图片 | 重新下载原图或高清图 |
| `artwork:transaction-price:supplement` | 补充成交价 | 单件补充成交价 |
| `artwork:export` | 导出艺术品 | 导出 Excel |

### 7.3 评估指标权限

| 权限编码 | 权限名称 | 对应功能 |
|---|---|---|
| `evaluation-metric:view` | 查看评估指标 | 查看指标库 |
| `evaluation-metric:create` | 创建评估指标 | 新建指标定义 |
| `evaluation-metric:update` | 编辑评估指标 | 编辑指标定义 |
| `evaluation-metric:disable` | 停用评估指标 | 停用指标定义 |
| `evaluation-template:view` | 查看指标模板 | 查看模板 |
| `evaluation-template:create` | 创建指标模板 | 新建模板 |
| `evaluation-template:update` | 编辑指标模板 | 编辑模板 |
| `evaluation-template:disable` | 停用指标模板 | 停用模板 |

### 7.4 评估项目权限

| 权限编码 | 权限名称 | 对应功能 |
|---|---|---|
| `evaluation:view` | 查看评估项目 | 查看评估项目列表、详情 |
| `evaluation:create` | 创建评估项目 | 新建评估项目 |
| `evaluation:update` | 编辑评估项目 | 编辑项目基本信息、艺术品、指标、专家 |
| `evaluation:delete` | 删除评估项目 | 删除草稿或未开始项目 |
| `evaluation:publish` | 发布评估项目 | 发布评估项目并锁定配置，允许专家开始评估 |
| `evaluation:submit-review` | 提交审核 | 将评估项目提交审核 |
| `evaluation:result:view` | 查看评估结果 | 查看多专家评估结果 |

### 7.5 专家评估权限

| 权限编码 | 权限名称 | 对应功能 |
|---|---|---|
| `evaluation-review:assigned:view` | 查看我的评估 | 查看分配给自己的评估项目 |
| `evaluation-review:own:view` | 查看自己的评估 | 查看自己的专家评估记录 |
| `evaluation-review:own:save` | 保存自己的评估 | 保存草稿 |
| `evaluation-review:own:submit` | 提交自己的评估 | 提交专家评估 |
| `evaluation-review:own:resubmit` | 重新提交被驳回评估 | 修改并重新提交被驳回的单条评估 |

### 7.6 审核权限

| 权限编码 | 权限名称 | 对应功能 |
|---|---|---|
| `evaluation-audit:view` | 查看待审核项目 | 查看审核页 |
| `evaluation-audit:approve` | 审核通过 | 审核通过整个项目 |
| `evaluation-audit:reject-review` | 驳回单条专家评估 | 驳回某专家对某艺术品的评估 |
| `evaluation-audit:history:view` | 查看审核历史 | 查看审核记录 |

### 7.7 用户与角色权限

| 权限编码 | 权限名称 | 对应功能 |
|---|---|---|
| `user:view` | 查看用户 | 查看用户列表 |
| `user:create` | 创建用户 | 新增用户 |
| `user:update` | 编辑用户 | 编辑用户信息 |
| `user:disable` | 停用用户 | 停用用户 |
| `role:view` | 查看角色 | 查看角色列表 |
| `role:create` | 创建角色 | 新建角色 |
| `role:update` | 编辑角色 | 编辑角色权限 |
| `role:disable` | 停用角色 | 停用角色 |

## 8. 角色与权限矩阵

| 功能域 | 权限 | ADMIN | DATA_OPERATOR | DATA_VIEWER | EVALUATION_MANAGER | EXPERT | AUDITOR |
|---|---|---:|---:|---:|---:|---:|---:|
| 任务 | `task:view` | 是 | 是 | 可选 | 可选 | 否 | 可选 |
| 任务 | `task:create` | 是 | 是 | 否 | 否 | 否 | 否 |
| 任务 | `task:start/pause/resume/cancel` | 是 | 是 | 否 | 否 | 否 | 否 |
| 任务 | `task:delete` | 是 | 可选 | 否 | 否 | 否 | 否 |
| 失败记录 | `task:failure:view` | 是 | 是 | 否 | 否 | 否 | 否 |
| 失败记录 | `task:failure:retry` | 是 | 是 | 否 | 否 | 否 | 否 |
| 艺术品 | `artwork:view` | 是 | 是 | 是 | 是 | 仅分配项目内 | 是 |
| 艺术品 | `artwork:image:view` | 是 | 是 | 是 | 是 | 仅分配项目内 | 是 |
| 艺术品 | `artwork:image:redownload` | 是 | 是 | 否 | 否 | 否 | 否 |
| 艺术品 | `artwork:transaction-price:supplement` | 是 | 是 | 否 | 否 | 否 | 否 |
| 艺术品 | `artwork:export` | 是 | 可选 | 是 | 是 | 否 | 可选 |
| 指标 | `evaluation-metric:*` | 是 | 否 | 否 | 是 | 否 | 可选查看 |
| 模板 | `evaluation-template:*` | 是 | 否 | 否 | 是 | 否 | 可选查看 |
| 评估项目 | `evaluation:view` | 是 | 否 | 否 | 是 | 仅分配项目 | 是 |
| 评估项目 | `evaluation:create/update/delete` | 是 | 否 | 否 | 是 | 否 | 否 |
| 评估项目 | `evaluation:submit-review` | 是 | 否 | 否 | 是 | 可选 | 否 |
| 专家评估 | `evaluation-review:own:*` | 可选 | 否 | 否 | 否 | 是 | 否 |
| 审核 | `evaluation-audit:*` | 是 | 否 | 否 | 可选 | 否 | 是 |
| 用户角色 | `user:*`, `role:*` | 是 | 否 | 否 | 否 | 否 | 否 |

说明：

- `仅分配项目内` 表示专家只能访问自己被分配的评估项目及其中艺术品。
- `可选` 表示可根据实际运营流程决定是否授予。
- `evaluation-review:own:*` 必须强制数据隔离，只能作用于当前专家自己的评估记录。

## 9. 功能与权限关系

### 9.1 已实现功能映射

| 已实现功能 | 当前入口 | 当前 API | 建议权限 |
|---|---|---|---|
| 查看任务列表 | `/tasks` | `GET /api/tasks` | `task:view` |
| 创建任务 | `/tasks` | `POST /api/tasks` | `task:create` |
| 启动任务 | `/tasks` | `POST /api/tasks/{id}/start` | `task:start` |
| 暂停任务 | `/tasks` | `POST /api/tasks/{id}/pause` | `task:pause` |
| 恢复任务 | `/tasks` | `POST /api/tasks/{id}/resume` | `task:resume` |
| 取消任务 | `/tasks` | `POST /api/tasks/{id}/cancel` | `task:cancel` |
| 删除任务 | `/tasks` | `DELETE /api/tasks/{id}` | `task:delete` |
| 查看失败记录 | `/tasks` | `GET /api/tasks/{id}/failures` | `task:failure:view` |
| 重试失败记录 | `/tasks` | `POST /api/tasks/{id}/failures/retry` | `task:failure:retry` |
| 查看艺术品列表 | `/artworks` | `GET /api/artworks` | `artwork:view` |
| 查看艺术品详情 | `/artworks/:id` | `GET /api/artworks/{id}` | `artwork:view` |
| 查看原图 | `/artworks/:id` | `GET /api/artworks/{id}/original-image` | `artwork:image:view` |
| 查看高清图 | `/artworks/:id` | `GET /api/artworks/{id}/hd-image` | `artwork:image:view` |
| 重新下载原图 | `/artworks/:id` | `POST /api/artworks/{id}/original-image/redownload` | `artwork:image:redownload` |
| 重新下载高清图 | `/artworks/:id` | `POST /api/artworks/{id}/hd-image/redownload` | `artwork:image:redownload` |
| 补充成交价 | `/artworks/:id` | `POST /api/artworks/{id}/transaction-price/supplement` | `artwork:transaction-price:supplement` |
| 导出艺术品 | `/artworks` | `GET /api/artworks/export` | `artwork:export` |

### 9.2 艺术品评估功能映射

| 评估功能 | 建议入口 | 建议 API | 建议权限 |
|---|---|---|---|
| 查看指标库 | `/evaluation-metrics` | `GET /api/evaluation-metrics` | `evaluation-metric:view` |
| 创建指标 | `/evaluation-metrics` | `POST /api/evaluation-metrics` | `evaluation-metric:create` |
| 编辑指标 | `/evaluation-metrics/:id` | `PUT /api/evaluation-metrics/{id}` | `evaluation-metric:update` |
| 查看指标模板 | `/evaluation-metric-templates` | `GET /api/evaluation-metric-templates` | `evaluation-template:view` |
| 创建评估项目 | `/evaluations/new` | `POST /api/evaluations` | `evaluation:create` |
| 编辑评估项目 | `/evaluations/:id` | `PUT /api/evaluations/{id}` | `evaluation:update` |
| 分配专家 | `/evaluations/:id` | `POST /api/evaluations/{id}/experts` | `evaluation:update` |
| 查看评估项目 | `/evaluations` | `GET /api/evaluations` | `evaluation:view` |
| 查看我的评估 | `/my-evaluations` | `GET /api/evaluations?scope=assigned` | `evaluation-review:assigned:view` |
| 查看自己的评估记录 | `/evaluations/:id/artworks/:artworkId/review` | `GET /api/evaluations/{id}/artworks/{artworkId}/my-review` | `evaluation-review:own:view` |
| 保存自己的评估 | 同上 | `PUT /api/evaluations/{id}/artworks/{artworkId}/my-review` | `evaluation-review:own:save` |
| 提交自己的评估 | 同上 | `POST /api/evaluations/{id}/artworks/{artworkId}/my-review/submit` | `evaluation-review:own:submit` |
| 发布评估项目 | `/evaluations/:id` | `POST /api/evaluations/{id}/publish` | `evaluation:publish` |
| 提交审核 | `/evaluations/:id` | `POST /api/evaluations/{id}/submit-review` | `evaluation:submit-review` |
| 查看审核页 | `/evaluations/:id/audit` | `GET /api/evaluations/{id}` | `evaluation-audit:view` |
| 审核通过项目 | `/evaluations/:id/audit` | `POST /api/evaluations/{id}/audit/approve` | `evaluation-audit:approve` |
| 驳回单条专家评估 | `/evaluations/:id/audit` | `POST /api/evaluations/{id}/expert-reviews/{reviewId}/audit/reject` | `evaluation-audit:reject-review` |

## 10. 数据范围规则

### 10.1 任务数据范围

MVP 阶段可先不做任务数据范围隔离，拥有 `task:view` 的用户可查看全部任务。

后续可扩展：

- 只能查看自己创建的任务。
- 只能查看所属部门任务。
- 管理员查看全部任务。

### 10.2 艺术品数据范围

MVP 阶段可按权限控制是否能查看艺术品。

评估专家的数据范围例外：

- 专家可以查看被分配评估项目中的艺术品基础信息。
- 专家不能访问未分配项目中的艺术品详情。
- 专家不能执行图片重下载、成交价补充和 Excel 导出。

### 10.3 评估项目数据范围

| 角色 | 数据范围 |
|---|---|
| ADMIN | 全部评估项目 |
| EVALUATION_MANAGER | 自己创建或被授权管理的评估项目 |
| EXPERT | 被分配给自己的评估项目 |
| AUDITOR | 被分配审核或进入审核池的评估项目 |

### 10.4 专家评估数据范围

这是评估模块的核心安全规则：

- 专家只能读取自己的 `ExpertReview`。
- 专家只能保存、提交、重新提交自己的 `ExpertReview`。
- 专家不能通过接口获取其他专家的 `ExpertReview`。
- 管理员和审核人可以按权限查看同一艺术品下所有专家评估。
- 被驳回的单条专家评估只能由原专家修改。

## 11. 推荐数据模型

### 11.1 User

应用用户表。

字段建议：

- id
- username
- passwordHash
- displayName
- email
- phone
- status
- lastLoginAt
- createdAt
- updatedAt

### 11.2 Role

角色表。

字段建议：

- id
- code
- name
- description
- enabled
- createdAt
- updatedAt

### 11.3 Permission

权限表。

字段建议：

- id
- code
- name
- description
- resourceType
- enabled
- createdAt
- updatedAt

### 11.4 UserRole

用户角色关联表。

字段建议：

- id
- userId
- roleId
- createdAt

### 11.5 RolePermission

角色权限关联表。

字段建议：

- id
- roleId
- permissionId
- createdAt

### 11.6 UserProfile 扩展字段

如果专家和审核人需要额外信息，可增加用户扩展表或在用户表中保留扩展字段。

专家扩展字段建议：

- specialty
- organization
- title
- introduction
- enabledForEvaluation

审核人扩展字段建议：

- auditLevel
- enabledForAudit

## 12. 推荐实现原理

### 12.1 权限模型

推荐采用 RBAC 为主、少量 ABAC 数据范围规则补充。

- RBAC：用户通过角色获得权限。
- ABAC：在特定业务场景中根据用户与数据的关系做判断，例如专家是否被分配到该评估项目。

示例：

- 是否能访问 `GET /api/evaluations/{id}`：先检查 `evaluation:view`，再检查用户是否有该项目数据范围。
- 是否能访问 `GET /api/evaluations/{id}/artworks/{artworkId}/my-review`：检查 `evaluation-review:own:view`，再检查当前用户是否是该项目专家。
- 是否能访问其他专家评估结果：只有 `evaluation:result:view` 或 `evaluation-audit:view` 的用户可访问。

### 12.2 后端实现建议

后端建议引入 Sa-Token 作为鉴权框架。

选型原因：

- 对当前 Spring Boot 单体应用更轻量，上手成本低。
- 支持登录认证、角色认证、权限认证和注解鉴权。
- 适配前后端分离 Token 模式。
- 后续可以扩展 Redis 会话、JWT、踢人下线、二级认证和 SSO。
- 相比自研拦截器，减少重复造轮子；相比 Spring Security，配置和概念更简单。

推荐步骤：

1. 引入 Sa-Token Spring Boot Starter。
2. 增加用户、角色、权限表。
3. 实现登录接口，校验账号密码后调用 `StpUtil.login(userId)`。
4. 登录接口返回 Sa-Token token 信息。
5. 前端 Axios 自动携带 token。
6. 实现 Sa-Token 权限加载接口，返回当前用户权限码和角色码。
7. 后端接口使用 Sa-Token 注解做权限校验。
8. 对评估专家接口增加数据范围校验。
9. 对审核接口增加审核人权限校验。
10. 增加操作审计日志。

接口级权限可以用注解表达：

```java
@SaCheckPermission("task:create")
```

角色校验可以用：

```java
@SaCheckRole("ADMIN")
```

登录示例：

```java
StpUtil.login(user.getId());
String token = StpUtil.getTokenValue();
```

获取当前用户：

```java
Long currentUserId = StpUtil.getLoginIdAsLong();
```

数据范围建议在 Service 层校验：

```java
evaluationAccessService.requireExpertAssigned(evaluationId, currentUserId);
expertReviewAccessService.requireOwnReview(reviewId, currentUserId);
```

不要只在前端隐藏按钮，后端必须强制校验权限和数据范围。

### 12.2.1 Sa-Token 配置建议

建议采用前后端分离 token 模式：

- token 名称：`Authorization`
- token 前缀：`Bearer`
- token 有效期：可先设为 7 天。
- 是否自动续期：第一版可开启。
- token 存储：第一版明确使用应用内存，不接 Redis。

示例配置方向：

```yaml
sa-token:
  token-name: Authorization
  token-prefix: Bearer
  timeout: 604800
  active-timeout: -1
  is-concurrent: true
  is-share: false
  token-style: uuid
```

说明：

- 单体本地开发或单实例部署时，内存会话足够简单。
- 第一版不接 Redis，后端重启后用户需要重新登录。
- 如果后续 Docker 多实例部署，或需要重启后 token 不失效，再接入 Redis。
- 如果需要无状态 token，可后续切换 Sa-Token JWT 插件。

### 12.2.2 Sa-Token 权限加载建议

需要实现 Sa-Token 的权限接口，为当前登录用户返回角色和权限。

推荐逻辑：

- 根据 `userId` 查询用户角色。
- 根据角色查询权限编码。
- 返回给 Sa-Token 做注解鉴权。

权限编码仍沿用本文第 7 节定义，例如：

- `task:create`
- `artwork:export`
- `evaluation-review:own:submit`
- `evaluation-audit:reject-review`

### 12.3 前端实现建议

前端建议维护当前用户信息：

- userId
- displayName
- roles
- permissions

前端根据权限控制：

- 菜单是否展示。
- 页面是否可访问。
- 按钮是否展示。
- 表格操作项是否展示。

前端权限控制只负责体验，不能作为安全边界。

### 12.4 专家互不可见实现建议

专家评估接口拆成两类：

专家自己的接口：

```http
GET /api/evaluations/{evaluationId}/artworks/{artworkId}/my-review
PUT /api/evaluations/{evaluationId}/artworks/{artworkId}/my-review
```

管理员/审核人的汇总接口：

```http
GET /api/evaluations/{evaluationId}/artworks/{artworkId}/reviews
```

实现原则：

- `my-review` 根据当前登录用户自动定位专家评估记录，不允许前端传 `expertId` 决定查看谁。
- `reviews` 需要 `evaluation:result:view` 或 `evaluation-audit:view` 权限。
- 专家角色默认不授予 `reviews` 接口权限。

### 12.5 单条驳回实现建议

审核驳回不直接驳回整个项目中的全部评估，而是驳回一条 `ExpertReview`。

建议流程：

1. 审核人选择某件艺术品下某位专家的评估记录。
2. 审核人填写驳回原因。
3. 系统将该 `ExpertReview.status` 改为 `REVIEW_REJECTED`。
4. 系统将 `EvaluationProject.status` 改为 `REVIEW_REJECTED`。
5. 原专家只能修改被驳回的 `ExpertReview`。
6. 原专家重新提交后，该记录状态改为 `RESUBMITTED` 或 `SUBMITTED`。
7. 所有被驳回记录重新提交后，项目可再次提交审核。

## 13. 菜单设计建议

| 菜单 | 路径 | 需要权限 |
|---|---|---|
| 检索任务 | `/tasks` | `task:view` |
| 艺术品数据 | `/artworks` | `artwork:view` |
| 评估项目 | `/evaluations` | `evaluation:view` |
| 我的评估 | `/my-evaluations` | `evaluation-review:assigned:view` |
| 待审核评估 | `/evaluation-audits` | `evaluation-audit:view` |
| 评估指标库 | `/evaluation-metrics` | `evaluation-metric:view` |
| 指标模板 | `/evaluation-metric-templates` | `evaluation-template:view` |
| 用户管理 | `/users` | `user:view` |
| 角色管理 | `/roles` | `role:view` |

## 14. 审计日志建议

建议记录关键操作：

- 登录成功 / 登录失败。
- 创建、启动、暂停、恢复、取消、删除任务。
- 导出艺术品数据。
- 创建、编辑、删除评估项目。
- 分配专家。
- 专家保存、提交、重新提交评估。
- 提交审核。
- 审核通过。
- 驳回单条专家评估。
- 修改角色权限。

审计日志字段建议：

- id
- userId
- username
- action
- resourceType
- resourceId
- description
- ipAddress
- userAgent
- createdAt

## 15. MVP 建议

第一版建议实现：

- 引入 Sa-Token 作为后端鉴权框架。
- 基础用户登录。
- 固定内置角色：ADMIN、DATA_OPERATOR、DATA_VIEWER、EVALUATION_MANAGER、EXPERT、AUDITOR。
- 固定权限编码，不先做复杂权限配置 UI。
- 用户可分配一个或多个角色。
- 前端按权限展示菜单和按钮。
- 后端使用 Sa-Token 注解按权限保护接口。
- 专家评估接口强制只返回当前专家自己的数据。
- 审核人可以查看多专家评估汇总。
- 审核人可以驳回单条专家评估。

第一版可以暂缓：

- 部门组织架构。
- 自定义数据范围。
- 复杂角色配置页面。
- 多租户隔离。
- 单点登录。
- Sa-Token Redis / JWT / SSO 高级能力。
- 细粒度字段级权限。

## 16. 验收标准

### 16.1 用户与登录

- 用户可以登录系统。
- 未登录用户不能访问受保护页面和 API。
- 登录后可以获取当前用户角色和权限。

### 16.2 菜单与按钮权限

- 没有权限的用户看不到对应菜单。
- 没有权限的用户看不到对应操作按钮。
- 即使绕过前端直接请求 API，后端也应拒绝无权限请求。

### 16.3 已有功能权限

- 只有具备 `task:create` 的用户可以创建任务。
- 只有具备 `task:start` 的用户可以启动任务。
- 只有具备 `artwork:export` 的用户可以导出艺术品。
- 只有具备 `artwork:image:redownload` 的用户可以重新下载图片。

### 16.4 评估权限

- 只有评估管理员可以创建和编辑评估项目。
- 专家只能看到分配给自己的评估项目。
- 专家只能看到自己的专家评估记录。
- 专家不能看到其他专家的评分、评语和估价。
- 审核人可以看到待审核项目下所有专家评估结果。
- 审核人可以驳回某专家对某艺术品的单条评估。
- 被驳回的单条评估只能由对应专家修改和重新提交。

## 17. 待确认问题

1. 第一版是否必须实现登录，还是继续采用专家名称、审核人名称文本字段过渡？
2. 专家是否属于系统用户，还是通过一次性链接进入评估页面？
3. 是否需要用户注册，还是只允许管理员创建用户？
4. 是否需要角色权限配置 UI，还是第一版使用内置角色和固定权限？
5. 是否需要部门、机构、团队概念？
6. 评估管理员是否可以查看专家未提交的草稿？
7. 审核人是否可以修改专家评估内容，还是只能通过/驳回？
8. 审核人是否必须被指定到项目，还是所有审核人都能审核所有待审核项目？
9. 是否需要导出操作审计和水印？
10. 是否需要对成交价、高清图等敏感字段做字段级权限控制？
