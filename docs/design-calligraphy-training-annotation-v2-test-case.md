# 书画模型训练标注模板 V2 测试用例设计

## 1. 背景

本测试用例用于验证管理员使用系统内置“书画模型训练标注模板V2”后，选择 20 个作品，模拟专家完成 0-10 分数值型打分，并最终导出模型训练所需 JSON 文件的端到端流程。

目标不是只验证单个接口，而是覆盖一条可自动化执行的业务链路：

系统初始化内置指标和模板 -> 管理员验证/选择模板 -> 创建评估项目 -> 专家提交评分 -> 管理员送审 -> 审核通过 -> 创建训练数据集 -> 选择 20 个作品 -> 校验样本 -> 生成并下载训练 zip -> 解压并验证 `annotations.json`。

## 2. 当前系统现状与差距

当前后端已有以下基础能力：

| 能力 | 当前接口/模块 | 状态 |
| --- | --- | --- |
| 创建评估指标 | `POST /api/evaluation-metrics` | 已有 |
| 创建评估指标模板 | `POST /api/evaluation-metric-templates` | 已有 |
| 创建评估项目 | `POST /api/evaluations` | 已有 |
| 专家提交评分 | `POST /api/evaluations/{evaluationId}/artworks/{artworkId}/my-review/submit` | 已有 |
| 项目送审/审核通过 | `POST /api/evaluations/{id}/submit-review`、`POST /api/evaluations/{id}/audit/approve` | 已有 |
| 训练数据集创建/检查/生成/下载 | `/api/auto-evaluation/datasets` | 已有 |

但导出训练数据集存在一个重要限制：`AutoEvaluationDatasetService` 当前只接受系统内置模板 `calligraphy_training_annotation`，并且只校验 5 个固定指标：

- `calligraphy_brush`
- `calligraphy_composition`
- `calligraphy_ink`
- `calligraphy_color`
- `calligraphy_technique`

导出的 `annotations.json` 当前也只生成：

```json
{
  "image_path": "images/artwork-1.jpg",
  "features": {
    "brush": 8.1,
    "composition": 7.6,
    "ink": 8.3,
    "color": 7.2,
    "technique": 8.5,
    "valuation_log": 12.9
  }
}
```

因此，V2 用例要完整走到“训练 zip 中包含 11 个指标的 JSON”，需要在脚本实现前确认或补齐以下能力：

1. 训练数据集导出不能继续写死 5 个指标，应按来源评估项目的模板/项目指标配置动态导出数值型指标。
2. 模板或项目指标需要能提供稳定的 JSON 字段映射；新增 `exportField` 字段保存模型训练 JSON 字段名，本用例中模板名称为“书画模型训练标注模板V2”，模板编码为 `calligraphy_training_annotation_v2`，11 个指标分别映射到第 3 节的 JSON 字段。
3. 支持训练数据集 zip 下载，脚本下载 zip 后解压并验证其中的 `annotations.json`、`manifest.json` 和 `skipped-samples.json`。

如果这些能力未补齐，脚本应在“创建训练数据集”“生成训练 zip”或“验证 JSON”阶段给出明确失败原因，而不是静默跳过。

设计结论：评估指标定义仍然保持动态配置，管理员可以按业务需要新增指标；中文显示名使用 `name`，业务编码使用 `code`，训练 JSON 字段名使用 `exportField`。训练导出层只读取当前评估项目实际使用的数值型指标和字段映射，不应把 V2 的 11 个字段写死进导出代码。V2 模板由系统初始化为内置模板，测试脚本验证并使用该模板，但导出能力仍然保持动态，不为 V2 写专用导出分支。

`exportField` 需要随指标配置完整流转：

- 指标定义：`evaluation_metric_definitions.export_field`。
- 模板项快照：`evaluation_metric_template_items.export_field_snapshot`。
- 项目指标快照：`evaluation_project_metrics.export_field`。
- DTO 和请求：`CreateEvaluationMetricDefinitionRequest`、`UpdateEvaluationMetricDefinitionRequest`、`MetricConfigRequest`、`MetricConfigDto`、`EvaluationMetricDefinitionDto`。

管理员在配置每个指标定义时都必须维护 `exportField`。专家评分页面只展示中文指标名、说明、评分控件和评分指引，不展示也不允许编辑 `exportField`。

`exportField` 必须全局唯一，并在指标定义层建立唯一约束或等效服务校验，确保一个训练字段长期对应一个稳定指标含义。创建和更新指标定义时均需要校验唯一性。

V2 的 11 个指标和模板应由系统初始化器维护为内置数据：

- 模板编码：`calligraphy_training_annotation_v2`。
- 模板名称：`书画模型训练标注模板V2`。
- 模板和指标默认启用。
- 内置 V2 指标、模板和模板项不可编辑、不可删除。
- 内置 V2 指标的 `name`、`code`、`exportField`、分值范围、步长和必填规则应保持稳定。
- 后续如需调整训练口径，应新增 V3 模板和新指标/字段映射，不直接修改 V2。

## 3. V2 指标定义

所有指标均为数值型、必填、0-10 分，步长 0.1，单位“分”。

| 序号 | 指标名称 `name` | 建议编码 `code` | 导出字段 `exportField` | 说明 |
| --- | --- | --- | --- | --- |
| 1 | 画工 | `calligraphy_v2_craftsmanship` | `craftsmanship` | 技法成熟度、完成度、造型与线条控制 |
| 2 | 构图 | `calligraphy_v2_composition` | `composition` | 画面结构、空间关系、视觉平衡 |
| 3 | 笔墨 | `calligraphy_v2_ink_brushwork` | `ink_brushwork` | 笔触、墨色层次、水墨表现 |
| 4 | 用色 | `calligraphy_v2_color` | `color` | 色彩协调、设色表现、综合色感 |
| 5 | 题材 | `calligraphy_v2_subject` | `subject` | 题材辨识度、市场接受度、艺术表达适配度 |
| 6 | 尺寸 | `calligraphy_v2_size` | `size` | 尺幅对表现力、收藏和市场价值的影响 |
| 7 | 提拔 | `calligraphy_v2_inscription` | `inscription` | 题跋、款识、印章等辅助信息质量 |
| 8 | 来源著录 | `calligraphy_v2_provenance` | `provenance` | 来源、出版、展览、著录等可追溯性 |
| 9 | 稀缺性 | `calligraphy_v2_rarity` | `rarity` | 作者、时期、题材、流通稀缺程度 |
| 10 | 品相 | `calligraphy_v2_condition` | `condition` | 保存状态、缺损、修复情况 |
| 11 | 裱工 | `calligraphy_v2_mounting` | `mounting` | 装裱质量、完整性和展示保存价值 |

备注：“提拔”按书画语境推定为“题跋/题拔”相关指标，脚本和文档统一使用页面显示名“提拔”，编码使用 `inscription`，避免导出字段出现中文或歧义拼音。

`valuation_log` 保留为训练目标字段，但不是专家打分指标。它由专家提交的 `finalEstimateAmount` 计算得到。单专家时 `valuation_log = ln(finalEstimateAmount)`；多专家平均聚合时，先对每个专家估值金额取自然对数，再平均：`valuation_log = avg(ln(amount_1), ln(amount_2), ...)`。因此每条训练样本包含 11 个专家评分特征和 1 个估值训练目标。

## 4. 测试数据策略

### 4.1 运行参数

脚本建议命名为：

```bash
scripts/run-calligraphy-training-annotation-v2-scenario.sh
```

建议支持环境变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `ARTFETCH_BASE_URL` | `http://localhost:8080/api` | 后端 API 地址 |
| `ARTFETCH_ADMIN_USERNAME` | `admin` | 管理员账号 |
| `ARTFETCH_ADMIN_PASSWORD` | `12345678` | 管理员密码 |
| `ARTFETCH_SCENARIO_ARTIST` | 空 | 作品筛选作者，空则取任意可用作品 |
| `ARTFETCH_SAMPLE_SIZE` | `20` | 作品数量，本用例固定断言为 20 |
| `ARTFETCH_REPORT_OUTPUT_DIR` | `docs/generated` | 证据和报告输出目录 |
| `ARTFETCH_KEEP_RESOURCES` | `false` | 是否保留临时项目、数据集和用户 |

脚本运行前提：后端服务已启动，且待测代码已经完成编译和服务重启。脚本本身只通过 API 执行业务流程，不负责重启后端或前端服务。

### 4.2 临时用户

脚本创建并使用 2 个专家和 1 个审核人：

| 用户 | 角色 | 用途 |
| --- | --- | --- |
| `codex_v2_exp1_<runId>` | `EXPERT` | 模拟专家甲提交 20 件作品评分 |
| `codex_v2_exp2_<runId>` | `EXPERT` | 模拟专家乙提交 20 件作品评分 |
| `codex_v2_aud_<runId>` | `AUDITOR` | 审核并通过项目 |

使用 2 个专家的原因是可以验证 `AVERAGE_ALL_EXPERTS` 聚合策略，导出的 JSON 特征值应为两位专家同一作品同一指标评分的平均值。

脚本不复用真实专家或审核人账号，避免污染人工任务和真实审计记录。临时用户在脚本结束后禁用。

### 4.3 作品选择

优先通过 `POST /api/evaluations/preview-artworks` 选择 20 个候选作品：

1. 如果设置 `ARTFETCH_SCENARIO_ARTIST`，使用作者包含条件筛选。
2. 如果未设置或不足 20 件，脚本应使用宽松条件重试，例如按已有作品分页取前 20 件。
3. 最终不足 20 件时，用例失败，失败原因为“可用于 V2 标注的作品不足 20 件”。

测试环境不得直接请求高清图字节接口，例如：

- `GET /api/artworks/{id}/hd-image`
- `GET /api/artworks/{id}/hd-image-v2`

样本可用性通过训练数据集 `check` 结果、图片对象元数据、跳过原因和预计包大小判断。

脚本在创建评估项目前应尽量选择具备训练图片元数据的作品；但项目完成并进入数据集 `check` 后，如果仍出现缺图、缺估值或缺评分导致的 skipped 样本，脚本不自动替换作品或重建项目，而是直接失败并报告 skipped 原因。

## 5. 流程设计

### 5.1 管理员验证内置指标

管理员登录后，调用 `GET /api/evaluation-metrics/enabled` 或按关键字查询，验证系统初始化器已经创建 11 个 V2 内置指标。

指标定义应包含以下关键字段：

```json
{
  "code": "calligraphy_v2_craftsmanship",
  "exportField": "craftsmanship",
  "name": "画工",
  "description": "技法成熟度、完成度、造型与线条控制",
  "category": "书画模型训练V2",
  "applicableArtworkTypes": "书画",
  "scoreType": "numeric",
  "minScore": 0,
  "maxScore": 10,
  "scoreStep": 0.1,
  "defaultWeight": 1,
  "required": true,
  "inputComponent": "input-number",
  "scoringGuide": "请按 0-10 分打分，可保留 1 位小数。",
  "unit": "分",
  "tags": "书画,训练标注,V2",
  "sortOrder": 1
}
```

断言：

- HTTP 200。
- 11 个 V2 指标均存在且 `id` 非空。
- 每个指标 `builtIn == true`、`enabled == true`。
- 每个指标 `scoreType` 为 `numeric`。
- 每个指标 `exportField` 与第 3 节一致。
- 每个指标 `minScore=0`、`maxScore=10`、`scoreStep=0.1`、`required=true`。

### 5.2 管理员验证并选择内置模板

调用 `GET /api/evaluation-metric-templates` 查询模板列表，定位内置模板：

```json
{
  "code": "calligraphy_training_annotation_v2",
  "name": "书画模型训练标注模板V2",
  "description": "用于书画模型训练的 11 维专家标注模板。",
  "enabled": true
}
```

随后调用 `GET /api/evaluation-metric-templates/{id}/items`，验证模板项按 11 个指标顺序填入，每项保留指标定义来源和导出字段：

- `sourceMetricDefinitionId`
- `sourceVersion`
- `code`
- `exportField`
- `name`
- `scoreType`
- `minScore`
- `maxScore`
- `scoreStep`
- `required`
- `sortOrder`

断言：

- HTTP 200。
- 返回模板 `id` 非空。
- 模板 `code == calligraphy_training_annotation_v2`。
- 模板名称为“书画模型训练标注模板V2”。
- 模板 `builtIn == true`、`enabled == true`。
- `items.length == 11`。
- 每个模板项均为 0-10 数值型必填。
- 每个模板项 `exportField` 非空，且满足英文 snake_case。

注意：当前模板创建请求没有 `code` 入参；V2 模板编码应由系统初始化器写入。当前指标、模板项和项目指标请求也没有独立的“导出字段”入参，本用例要求新增 `exportField`，避免把业务编码 `calligraphy_v2_*` 和模型字段 `craftsmanship` 等强绑定。

### 5.3 管理员创建评估项目

调用 `POST /api/evaluations` 创建评估项目：

- 名称：`书画模型训练标注V2自动化场景-<runId>`
- 作品：20 件
- 专家：专家甲、专家乙
- 审核人：临时审核人
- 指标：从模板项复制 11 个项目指标

断言：

- HTTP 200。
- 项目 `artworkCount == 20`。
- 项目 `expertCount == 2`。
- 项目 `expectedReviewCount == 40`。
- 项目指标数为 11。

### 5.4 专家模拟打分

专家甲、专家乙分别登录，对 20 件作品全部直接提交评分，不单独模拟草稿保存。草稿、驳回、重提等评估流程细节由既有 `scripts/run-evaluation-scenario.sh` 覆盖，本用例聚焦训练标注和导出。

每次调用：

```http
POST /api/evaluations/{evaluationId}/artworks/{artworkId}/my-review/submit
```

请求体：

```json
{
  "finalEstimate": "RMB 120000-160000",
  "finalEstimateAmount": 140000,
  "finalEstimateCurrency": "CNY",
  "comment": "自动化模拟评分：作品结构完整，指标分布用于训练数据验证。",
  "scores": [
    { "projectMetricId": 101, "score": 8.1 },
    { "projectMetricId": 102, "score": 7.6 }
  ]
}
```

评分生成规则应可重复，建议使用确定性公式而不是随机数：

```text
score = roundTo1Decimal(5.0 + ((artworkIndex * 7 + metricIndex * 3 + expertIndex) % 50) / 10)
```

并限制在 `[0, 10]`。

断言：

- 每次提交 HTTP 200。
- 返回状态为 `SUBMITTED` 或 `RESUBMITTED`。
- 每条评估包含 11 个 score。
- 40 条专家评估全部提交后，项目状态为 `READY_FOR_REVIEW`。

### 5.5 管理员送审，审核人通过

管理员调用：

```http
POST /api/evaluations/{id}/submit-review
```

审核人调用：

```http
POST /api/evaluations/{id}/audit/approve
```

断言：

- 送审后项目状态为 `IN_REVIEW`。
- 审核通过后项目状态为 `COMPLETED`。
- `auditResult == APPROVED`。
- 审计日志包含 `evaluation.submit-review` 和 `evaluation.audit.approve`。

### 5.6 创建训练数据集

调用：

```http
POST /api/auto-evaluation/datasets
```

建议使用平均聚合：

```json
{
  "name": "书画模型训练标注模板V2数据集-<runId>",
  "sourceEvaluationId": 123,
  "aggregationStrategy": "AVERAGE_ALL_EXPERTS"
}
```

然后调用：

```http
POST /api/auto-evaluation/datasets/{id}/selected-artworks
POST /api/auto-evaluation/datasets/{id}/check
```

断言：

- 数据集创建 HTTP 200。
- `selectedCount == 20`。
- `sampleCount == 20`。
- `skippedCount == 0`。
- `samples.length` 至少包含可预览的前若干样本。
- 未触发移动端硬上限：`exceedsMobileHardLimit == false`。
- 本测试用例不允许样本跳过；如果 `skippedCount > 0`，即使用例能生成部分 JSON，也判定失败。

如果当前后端返回“评估项目未使用系统内置书画训练标注模板”或“缺少固定 5 指标”，用例应标记为“动态指标导出能力未实现”，并输出明确修复建议：训练数据集导出应改为读取项目指标配置，而不是固定校验 `calligraphy_brush` 等 5 个内置指标。

动态导出校验规则：

- 仅导出 `scoreType` 为数值型的项目指标。
- 每个指标定义必须有非空 `exportField`，项目指标快照也必须保留该字段。
- `exportField` 在指标定义层全局唯一；导出前仍需再次校验同一项目内无重复字段，避免历史数据或手工数据污染。
- `exportField` 建议限制为 `^[a-z][a-z0-9_]*$`。
- 专家评分特征导出原始 0-10 分；多专家场景导出专家均分，不应用模板权重。
- 缺失必填指标评分的作品进入 `skipped-samples.json`，原因记录为 `MISSING_REQUIRED_METRIC_SCORE`。
- 缺失 `finalEstimateAmount` 或金额小于等于 0 的作品进入 `skipped-samples.json`，原因记录为 `MISSING_FINAL_ESTIMATE_AMOUNT`。

### 5.7 生成并下载训练 zip

调用：

```http
POST /api/auto-evaluation/datasets/{id}/generate
```

轮询：

```http
GET /api/auto-evaluation/datasets/{id}
```

直到：

- `status == READY`：继续导出。
- `status == FAILED`：用例失败并记录 `errorMessage`。
- 超过超时时间：用例失败。

下载训练数据集 zip：

```http
GET /api/auto-evaluation/datasets/{id}/download
```

下载文件保存为：

```text
docs/generated/calligraphy-training-annotation-v2-<runId>/artfetch-training-dataset-<datasetId>.zip
```

脚本随后解压到：

```text
docs/generated/calligraphy-training-annotation-v2-<runId>/unzipped/
```

验证边界：

- 可以读取 `annotations.json`、`manifest.json` 和 `skipped-samples.json`。
- 可以通过 zip 目录或文件元数据确认 `images/` 下存在 20 个图片条目，且每个图片条目 size > 0。
- 不打开、不解码、不渲染图片文件。
- 不调用 `GET /api/artworks/{id}/hd-image`、`GET /api/artworks/{id}/hd-image-v2` 等高清图字节接口。

## 6. JSON 断言

`annotations.json` 应为纯数组，每个作品一条记录：

```json
[
  {
    "image_path": "images/artwork-1001.jpg",
    "features": {
      "craftsmanship": 8.1,
      "composition": 7.6,
      "ink_brushwork": 8.3,
      "color": 7.2,
      "subject": 7.9,
      "size": 6.8,
      "inscription": 6.5,
      "provenance": 7.1,
      "rarity": 8.0,
      "condition": 7.4,
      "mounting": 6.9,
      "valuation_log": 11.849398
    }
  }
]
```

必须断言：

- 顶层是数组。
- 数组长度为 20。
- 每条记录包含 `image_path`。
- 每条记录包含 `features`。
- `features` 包含 11 个 V2 指标字段。
- 11 个指标均为数字，范围 `[0, 10]`，为原始评分或多专家均分，不乘模板权重。
- 保留估值训练目标，`valuation_log` 为数字且大于 0；它不计入 11 个专家评分指标。
- 不包含专家姓名、账号、审核意见等非训练字段。

对于平均聚合策略，任选 3 个作品做精确校验：

```text
features[metric] == average(expert1Score, expert2Score)
features.valuation_log == average(ln(expert1FinalEstimateAmount), ln(expert2FinalEstimateAmount))
```

允许浮点误差：`abs(actual - expected) <= 0.000001`。

## 7. 清理策略

默认清理：

- 禁用临时专家和审核人。
- 训练数据集若仍为 `DRAFT` 或 `FAILED`，删除。

默认保留：

- 已完成评估项目。当前系统规则禁止删除完成态项目，应保留用于审计。
- 已生成且 `READY` 的训练数据集，便于用户后续在系统内查看并手动下载。
- 本地 `docs/generated/calligraphy-training-annotation-v2-<runId>/` 下的 zip、解压目录、JSON 文件和执行报告，作为本次自动化测试证据包。

如果设置 `ARTFETCH_KEEP_RESOURCES=true`，脚本不清理，便于人工复查。

## 8. 输出报告

脚本应输出两类文件：

```text
docs/generated/calligraphy-training-annotation-v2-<runId>/scenario-report.json
docs/generated/calligraphy-training-annotation-v2-<runId>/scenario-report.md
```

报告至少包含：

- runId、执行时间、后端地址。
- 内置指标 ID、内置模板 ID、项目 ID、数据集 ID。
- 20 个 artworkId。
- 40 条专家评估提交结果。
- 项目状态流转。
- 数据集 `check` 结果。
- zip 下载路径、解压目录和 `annotations.json` 路径。
- 所有断言结果。
- 失败原因和清理说明。

## 9. 用例通过标准

本用例通过需要同时满足：

1. 管理员成功验证 11 个 V2 内置数值型指标。
2. 管理员成功验证并选择内置“书画模型训练标注模板V2”。
3. 评估项目包含 20 件作品、2 个专家、11 个指标。
4. 两位专家共提交 40 条评估记录。
5. 项目送审并审核通过。
6. 训练数据集检查得到 20 个可生成样本、0 个跳过样本。
7. 成功生成并下载训练数据集 zip。
8. `annotations.json` 有 20 条样本，每条包含 11 个 V2 指标，数值范围均为 0-10。
9. zip 中存在 `annotations.json`、`manifest.json`、`skipped-samples.json` 和 20 个 `images/` 图片条目，每个图片条目 size > 0。
10. 全程未直接调用高清图字节接口，且脚本不打开、不解码、不渲染图片文件。

## 10. 后续脚本实现建议

脚本可复用 `scripts/run-evaluation-scenario.sh` 的结构：

- `http_request`
- `login_user`
- `record_check`
- `cleanup_resources`
- `generate_json_report`
- `generate_markdown_report`

建议新增函数：

- `assert_builtin_metrics_v2`
- `assert_builtin_template_v2`
- `select_twenty_artworks`
- `create_v2_evaluation_project`
- `submit_all_expert_scores`
- `approve_project`
- `create_and_check_dataset`
- `generate_dataset_and_download_zip`
- `assert_annotations_json`

实现脚本前应先处理第 2 节列出的动态导出能力差距，否则脚本只能覆盖到“专家评分完成”，无法真正完成“最终下载训练 zip 并验证 11 指标 JSON”。
