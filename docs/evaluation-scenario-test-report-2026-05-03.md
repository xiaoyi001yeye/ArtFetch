# 评估场景联调测试报告

测试日期：2026-05-03  
测试方式：本地 `docker compose` 环境下，使用真实接口串联完整业务流程  
测试环境：`postgres + backend + frontend`  
后端地址：`http://localhost:8080`

## 一、场景模拟基础情况

### 1. 业务背景

模拟一个拍卖机构内部的评估复核场景：

- 项目名称：`2026春拍周春芽作品复核`
- 业务目标：对两件拟进入春拍专场的周春芽作品进行双专家估值复核，并由审核人完成终审归档
- 业务要求：
  - 项目创建后先由专家分别填写意见
  - 全部专家提交后，项目才能提交审核
  - 审核人可驳回单条专家评估，专家补充后重新提交
  - 审核通过后项目应进入完成态，并保留完整审计痕迹

### 2. 角色设置

- 管理员：`admin / 12345678`
- 临时专家甲：`codex_scene_exp1_20260503_161500 / Expert12345`
- 临时专家乙：`codex_scene_exp2_20260503_161500 / Expert22345`
- 临时审核人：`codex_scene_aud_20260503_161500 / Auditor12345`

说明：

- 临时账号由管理员在测试开始前创建
- 测试结束后 3 个临时账号均已禁用

### 3. 样本艺术品

本次直接从现有艺术品库按“作者包含周春芽”筛选，并选用前两件数据：

- `353772`：`黑色的线条红色的人体`
- `353771`：`石头`

### 4. 评估指标

为确保流程更接近真实评审，本次单独建立了 3 个项目指标：

1. `市场热度`
   - 输入方式：数字
   - 规则：`1-10` 分
   - 必填：是
2. `真伪及流通风险`
   - 输入方式：下拉选择
   - 选项：`低风险 / 中风险 / 高风险`
   - 必填：是
3. `专家核心意见`
   - 输入方式：多行文本
   - 必填：是

同时补建了一个临时指标模板，用于模拟管理员在正式配置项目时的准备动作。

## 二、执行过程

### 1. 项目创建阶段

- 管理员成功创建临时专家、审核人账号
- 通过 `/api/evaluations/preview-artworks` 预览候选艺术品
- 成功创建评估项目 `2026春拍周春芽作品复核-20260503_161500`
- 项目初始状态：`PENDING`

### 2. 专家评估阶段

- 两位专家都能在 `/api/evaluations/assigned` 中看到自己的分配项目
- 专家甲先对作品 `353772` 保存草稿，项目状态从 `PENDING` 变为 `IN_PROGRESS`
- 管理员查看作品汇总时，能看到草稿记录
- 审核人查看同一汇总时，看不到草稿，说明草稿隔离生效
- 管理员尝试在项目进入进行中后修改艺术品/专家/指标，接口返回 `400`，项目配置锁定生效
- 随后两位专家完成两件作品共 4 条评估提交，项目状态变为 `READY_FOR_REVIEW`

### 3. 审核阶段

- 管理员成功提交项目审核，状态变为 `IN_REVIEW`
- 审核人驳回专家乙对作品 `353772` 的评估，理由为：
  - `估值上沿偏高，缺少足够成交对比支撑，请补充说明后重提。`
- 项目状态变为 `REVIEW_REJECTED`
- 管理员此时再次送审，接口返回 `400`：
  - `必须等待全部专家评估提交后，才能提交审核`
- 专家乙修改后重新提交，评估状态变为 `RESUBMITTED`
- 管理员再次送审成功，项目重新进入 `IN_REVIEW`
- 审核人最终审核通过，项目状态变为 `COMPLETED`

### 4. 清理阶段

- 删除了临时模板和 3 个临时指标
- 禁用了本次测试创建的 3 个临时账号
- 额外验证了“已完成项目删除”这一边界行为，结果见下方问题清单

## 三、关键结果

| 检查点 | 结果 |
| --- | --- |
| 项目创建 | 通过 |
| 专家查看分配项目 | 通过 |
| 专家保存草稿 | 通过 |
| 草稿对审核人不可见 | 通过 |
| 项目进行中配置锁定 | 通过 |
| 全部专家提交后才能送审 | 通过 |
| 审核人驳回单条专家评估 | 通过 |
| 专家补充后重新提交 | 通过 |
| 最终审核通过 | 通过 |

## 四、发现的问题

### 1. 高优先级：审核通过/驳回没有写入系统审计日志

现象：

- 项目 `6` 的业务审核记录存在于 `evaluation_audit_records`
- 但系统审计日志 `auth_audit_logs` 中，仅记录了：
  - `evaluation.create`
  - `evaluation.submit-review`
  - `evaluation.submit-review`
  - `evaluation.delete`
- 没有 `approve` 或 `reject` 相关系统审计动作

影响：

- 审核是敏感操作，但系统级审计链路不完整
- 一旦后续需要统一按审计日志追责、导出或做安全稽核，会漏掉最关键的审核动作

代码定位：

- [backend/src/main/java/com/artfetch/evaluation/service/EvaluationAuditService.java](../backend/src/main/java/com/artfetch/evaluation/service/EvaluationAuditService.java)
  - `approve()` 与 `rejectReview()` 只写了 `EvaluationAuditRecord`
  - 未调用 `AuditLogService.recordSuccess(...)`

### 2. 高优先级：已完成项目仍然可以被删除，且删除后状态污染

现象：

- 项目审核通过后状态已为 `COMPLETED`
- 管理员仍可调用 `DELETE /api/evaluations/{id}`
- 删除成功后，数据库中的项目状态变为：
  - `status = CANCELLED`
  - `deleted_at` 有值
  - `audit_result = APPROVED`
  - `audit_comment` 仍保留审核通过意见

影响：

- 已完成项目被删除后，业务终态从“已完成”变成“已取消”，与审核结论冲突
- 审核通过的正式结果可能被后续误删，影响追溯和统计
- 最终会形成“已取消但审核已通过”的脏状态

代码定位：

- [backend/src/main/java/com/artfetch/evaluation/service/EvaluationProjectService.java](../backend/src/main/java/com/artfetch/evaluation/service/EvaluationProjectService.java)
  - `delete()` 直接把项目置为 `CANCELLED`
  - 没有校验项目当前是否允许删除

### 3. 中优先级：管理员查看评估结果时会看到 `NOT_STARTED` 占位评估

现象：

- 专家甲仅保存 1 条草稿后，管理员查看该作品汇总，返回状态为：
  - `NOT_STARTED,DRAFT`
- 也就是说，尚未开始填写的专家记录也被当成“评估结果”返回

影响：

- 结果页会混入未开始的占位记录，干扰管理员快速判断当前真实进度
- 前端汇总弹窗会出现空白评估卡片，用户体验较差

代码定位：

- [backend/src/main/java/com/artfetch/evaluation/service/ExpertReviewService.java](../backend/src/main/java/com/artfetch/evaluation/service/ExpertReviewService.java)
  - `getArtworkReviewSummary()` 在允许看草稿时直接返回全部记录
  - 没有过滤 `NOT_STARTED`

## 五、结论

本次“创建项目 → 专家评估 → 驳回重提 → 审核通过”的主流程已经可以完整跑通，核心状态流转基本符合预期。

但从真实评估场景视角看，当前还有 3 个必须尽快处理的问题：

1. 审核通过/驳回缺少系统审计日志
2. 已完成项目可以被删除，且会造成状态污染
3. 管理端结果汇总混入 `NOT_STARTED` 占位评估

建议优先修复前两个高优先级问题，再做一次同场景回归测试。

## 六、补充说明

- 本次为真实本地服务联调，不是伪造返回值
- 本次以接口串联为主，未覆盖多浏览器并发下的前端交互回归
- 临时测试账号、模板、指标均已清理；临时用户已禁用
