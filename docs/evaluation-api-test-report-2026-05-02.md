# 评估模块接口测试报告

测试日期：2026-05-02  
测试方式：基于 `curl` 的本地接口联调  
测试环境：`docker compose` 启动的 `postgres + backend + frontend`  
后端地址：`http://localhost:8080`

## 测试账号

- 管理员：`admin / 12345678`
- 临时专家：`codex_eval_expert_20260502125134 / Expert12345`
- 临时审核人：`codex_eval_auditor_20260502125134 / Auditor12345`

说明：

- 管理员密码 `12345678` 按本次联调要求记录在文档中。
- 临时专家和审核人账号用于本次联调，测试结束后已被禁用。

## 测试计划

1. 验证服务重启后接口可访问，登录接口可正常签发令牌。
2. 验证评估指标接口的创建、列表、详情、更新、删除。
3. 验证评估模板接口的创建、列表、详情、明细、更新、删除。
4. 验证评估项目接口的创建、列表、详情、项目指标、项目艺术品、项目专家、编辑、删除。
5. 验证专家登录后查看分配项目、获取本人评估表单、保存草稿、提交、被驳回后再次提交。
6. 验证管理员提交项目送审。
7. 验证审核人查看汇总、驳回专家评估、审核通过项目。
8. 验证权限与可见性边界：
   - 未登录不能访问专家评估接口
   - 管理员不能代替专家填写或修改
   - 审核人不能看到专家草稿
   - 项目进入 `IN_PROGRESS` 后不能修改艺术品、专家、指标

## 执行前问题与处理

在正式测试前发现并处理了两个问题：

1. 初次重启后，运行中的后端镜像仍是旧版本，`/api/evaluation-metrics` 和 `/api/evaluations` 返回 `404`。
2. 重建新后端镜像后，服务启动失败，日志报错：`scale has no meaning for SQL floating point types`。

已修复：

- 去除了评估实体中 `Double` 字段上不兼容 Hibernate 6 的 `scale/precision` 浮点列定义。
- 修复文件：
  - [EvaluationMetricDefinition.java](/Users/wyn/code/ArtFetch/backend/src/main/java/com/artfetch/evaluation/entity/EvaluationMetricDefinition.java)
  - [EvaluationMetricTemplateItem.java](/Users/wyn/code/ArtFetch/backend/src/main/java/com/artfetch/evaluation/entity/EvaluationMetricTemplateItem.java)
  - [EvaluationProjectMetric.java](/Users/wyn/code/ArtFetch/backend/src/main/java/com/artfetch/evaluation/entity/EvaluationProjectMetric.java)
  - [ExpertReviewScore.java](/Users/wyn/code/ArtFetch/backend/src/main/java/com/artfetch/evaluation/entity/ExpertReviewScore.java)

修复后重新构建并重启后端，服务成功启动，评估模块接口可访问。

## 测试结果

| 场景 | 关键接口 | 结果 |
| --- | --- | --- |
| 管理员登录 | `POST /api/auth/login` | 通过，返回有效 `tokenValue` |
| 未登录访问专家评估 | `GET /api/evaluations/{id}/artworks/{artworkId}/my-review` | 通过，返回 `401` |
| 创建专家账号 | `POST /api/users` | 通过，返回 `200` |
| 创建审核人账号 | `POST /api/users` | 通过，返回 `200` |
| 专家登录 | `POST /api/auth/login` | 通过，返回有效 `tokenValue` |
| 审核人登录 | `POST /api/auth/login` | 通过，返回有效 `tokenValue` |
| 创建评估指标 | `POST /api/evaluation-metrics` | 通过，返回 `200` |
| 查询评估指标列表 | `GET /api/evaluation-metrics` | 通过，返回 `200` |
| 查询评估指标详情 | `GET /api/evaluation-metrics/{id}` | 通过，返回 `200` |
| 更新评估指标 | `PUT /api/evaluation-metrics/{id}` | 通过，返回 `200` |
| 查询启用指标 | `GET /api/evaluation-metrics/enabled` | 通过，返回 `200` |
| 创建评估模板 | `POST /api/evaluation-metric-templates` | 通过，返回 `200` |
| 查询评估模板列表 | `GET /api/evaluation-metric-templates` | 通过，返回 `200` |
| 查询评估模板详情 | `GET /api/evaluation-metric-templates/{id}` | 通过，返回 `200` |
| 查询模板指标明细 | `GET /api/evaluation-metric-templates/{id}/items` | 通过，返回 `200` |
| 更新评估模板 | `PUT /api/evaluation-metric-templates/{id}` | 通过，返回 `200` |
| 预览艺术品 | `POST /api/evaluations/preview-artworks` | 通过，返回 `200` |
| 创建评估项目 | `POST /api/evaluations` | 通过，初始状态为 `PENDING` |
| 查询项目列表 | `GET /api/evaluations` | 通过，返回 `200` |
| 查询项目详情 | `GET /api/evaluations/{id}` | 通过，返回 `200` |
| 查询项目指标 | `GET /api/evaluations/{id}/metrics` | 通过，返回 `200` |
| 查询项目艺术品 | `GET /api/evaluations/{id}/artworks` | 通过，返回 `200` |
| 查询项目专家 | `GET /api/evaluations/{id}/experts` | 通过，返回 `200` |
| 项目进行前编辑 | `PUT /api/evaluations/{id}` | 通过，返回 `200` |
| 专家查看分配项目 | `GET /api/evaluations/assigned` | 通过，返回 `200` |
| 专家获取本人评估表单 | `GET /api/evaluations/{id}/artworks/{artworkId}/my-review` | 通过，初始状态为 `NOT_STARTED` |
| 专家保存草稿 | `POST /api/evaluations/{id}/artworks/{artworkId}/my-review` | 通过，状态变为 `DRAFT` |
| 管理员查看草稿汇总 | `GET /api/evaluations/{id}/artworks/{artworkId}/reviews` | 通过，可看到 `DRAFT` |
| 审核人查看草稿汇总 | `GET /api/evaluations/{id}/artworks/{artworkId}/reviews` | 通过，看不到草稿，返回空列表 |
| 管理员代填专家评估 | `POST /api/evaluations/{id}/artworks/{artworkId}/my-review` | 通过拦截，返回 `403` |
| 项目进行中修改艺术品/专家/指标 | `PUT /api/evaluations/{id}` | 通过拦截，返回 `400`，错误为“评估项目进入进行中后，不能修改艺术品、专家、指标” |
| 专家提交评估 | `POST /api/evaluations/{id}/artworks/{artworkId}/my-review/submit` | 通过，状态变为 `SUBMITTED` |
| 管理员提交项目送审 | `POST /api/evaluations/{id}/submit-review` | 通过，状态变为 `IN_REVIEW` |
| 审核人查看已提交汇总 | `GET /api/evaluations/{id}/artworks/{artworkId}/reviews` | 通过，可看到 `SUBMITTED` |
| 审核人驳回专家评估 | `POST /api/evaluations/{id}/expert-reviews/{reviewId}/audit/reject` | 通过，项目状态变为 `REVIEW_REJECTED` |
| 专家查看驳回原因 | `GET /api/evaluations/{id}/artworks/{artworkId}/my-review` | 通过，可看到 `rejectedReason` |
| 专家再次提交 | `POST /api/evaluations/{id}/artworks/{artworkId}/my-review/submit` | 通过，状态变为 `RESUBMITTED` |
| 管理员再次提交送审 | `POST /api/evaluations/{id}/submit-review` | 通过，状态变为 `IN_REVIEW` |
| 审核人审核通过项目 | `POST /api/evaluations/{id}/audit/approve` | 通过，状态变为 `COMPLETED` |
| 查询审核记录 | `GET /api/evaluations/{id}/audit-records` | 通过，记录到 `REJECT_REVIEW` 和 `APPROVE_PROJECT` |
| 删除评估项目 | `DELETE /api/evaluations/{id}` | 通过，返回 `200` |
| 查询已删除项目 | `GET /api/evaluations/{id}` | 通过，返回 `404` |
| 删除评估模板 | `DELETE /api/evaluation-metric-templates/{id}` | 通过，返回 `200` |
| 删除评估指标 | `DELETE /api/evaluation-metrics/{id}` | 通过，返回 `200` |

## 重点验收结论

- 管理员可创建、编辑、删除评估项目：已验证通过。
- 评估项目进入 `IN_PROGRESS` 后不能修改艺术品、专家、指标：已验证通过。
- 管理员不能代替专家填写或修改：已验证通过，接口返回 `403`。
- 审核人不能看到专家草稿：已验证通过，审核人看到的汇总为空列表。
- 专家必须登录账号后才能查看和提交本人评估：已验证通过，未登录返回 `401`，专家账号登录后可正常操作。
- 专家评估被驳回后可重新提交，审核流可继续完成：已验证通过。
- 审核记录可追踪驳回与通过动作：已验证通过。

## 测试后清理

- 已删除本次测试创建的评估项目。
- 已删除本次测试创建的评估模板。
- 已删除本次测试创建的评估指标。
- 已将本次测试创建的临时专家与临时审核人账号禁用。

## 结论

在修复后端实体映射启动问题并重新部署后，评估模块本次计划内接口均已通过 `curl` 联调验证，主流程、权限边界和审核流程与 PRD 要求一致，可继续进入前端联调和更大范围的回归测试。
