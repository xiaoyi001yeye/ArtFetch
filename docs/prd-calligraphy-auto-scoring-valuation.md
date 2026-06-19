# 书画特征自动化打分与估值系统需求文档

## 文档信息

| 项 | 内容 |
|---|---|
| 文档来源 | `/Users/wyn/Documents/书画特征自动化打分与估值系统（后端部分）.pdf` |
| 当前版本 | v1 |
| 文档状态 | 需求分析稿 |
| 最后更新日期 | 2026-06-19 |

## 1. 背景

PPT 描述的是一个基于图像视觉特征的书画自动打分与估值系统。系统使用 DINOv2 作为冻结的视觉 Backbone，从输入图片中提取 768 维图像特征，再通过 6 个预测头输出书画特征评分和估值结果。

当前演示流程已经以本地工具形式跑通：

- `data_prep_pixel.exe`：当前用于模拟生成训练标注数据，产物为 `annotations.json`。
- `ArtEvaluationSystem.exe`：通过浏览器访问 `http://127.0.0.1:7860/`，导入 `annotations.json` 后训练预测头，生成 `art_dinov2_local_head_model.pth`。
- `art_dinov2_local_head_model.pth`：训练后的本地模型文件，用于后续图片评估。

PPT 中明确提到，模拟数据生成部分后续需要单独实现一个前端应用，让专家打分并最终生成 `annotations.json`。结合 ArtFetch 当前能力，这个“专家打分前端”不建议另起孤岛应用，而应优先复用 ArtFetch 已有的专家评估项目、指标库、专家移动端评估页、权限、审核和高清图能力。

## 2. 要做什么

系统要完成从专家标注到模型训练、再到自动评分估值的闭环：

1. 管理员从 ArtFetch 艺术品库中选择一批书画作品作为训练样本。
2. 专家对样本图片进行人工评分和估值标注。
3. 系统把专家标注结果转换为模型训练需要的 `annotations.json`。
4. 后端启动或调度模型训练流程，生成模型文件 `art_dinov2_local_head_model.pth`。
5. 管理员管理模型版本，包括训练数据来源、训练参数、训练日志和模型文件。
6. 系统使用指定模型对新的书画图片或 ArtFetch 中已有艺术品进行自动评分和估值。
7. 自动评分结果进入 ArtFetch 评估流程，供专家复核、人工修正、审核和沉淀为下一轮训练数据。

## 3. 目标

### 3.1 业务目标

- 把书画作品的主观经验评估转化为可沉淀、可复用、可迭代的结构化数据。
- 降低专家重复初评成本，让模型先给出笔触、构图、墨量、用色、画工和估值建议。
- 支持专家复核模型结果，使人工经验继续作为训练数据反哺模型。
- 让模型版本、训练样本、评分结果和审核过程可追踪。

### 3.2 产品目标

- 不另建孤立标注系统，优先嵌入 ArtFetch 当前专家评估模块。
- 模型输出结果应能和 ArtFetch 现有评估指标体系对齐。
- 自动评估结果不能直接替代专家结论，第一阶段定位为“AI 初评 / 专家辅助”。
- 支持按项目、艺术品、模型版本查看自动评分与专家评分差异。

## 4. PPT 中的模型流程

### 4.1 模型架构

模型链路如下：

```text
输入图片 -> DINOv2 Backbone（冻结） -> 768 维特征 -> 6 个预测头 -> 输出评分/估值
```

关键要求：

- DINOv2 Backbone 冻结，不在本地完整微调大模型。
- 只训练轻量预测头，降低本地算力压力。
- CPU 环境也应能完成预测头训练并生成模型文件。
- 训练产物为 `art_dinov2_local_head_model.pth`。

### 4.2 标注数据

训练数据文件为 `annotations.json`。PPT 示例中每张图片对应一条记录：

```json
{
  "image_path": "01.png",
  "features": {
    "brush": 10.0,
    "composition": 9.4,
    "ink": 7.3,
    "color": 10.0,
    "technique": 9.7,
    "valuation_log": 6.017218362491441
  }
}
```

字段含义建议统一为：

| 字段 | 含义 | 建议范围/说明 |
|---|---|---|
| `image_path` | 样本图片路径 | 训练任务可访问的本地路径或对象存储 key |
| `features.brush` | 笔触/笔墨评分 | 0-10 或 1-10，需项目级统一 |
| `features.composition` | 构图评分 | 0-10 或 1-10，需项目级统一 |
| `features.ink` | 墨量/墨色评分 | 0-10 或 1-10，需项目级统一 |
| `features.color` | 用色评分 | 0-10 或 1-10，需项目级统一 |
| `features.technique` | 技法/画工评分 | 0-10 或 1-10，需项目级统一 |
| `features.valuation_log` | 估值的对数值 | 后端需定义原始价格与 log 转换规则 |

### 4.3 模型训练

训练工具当前通过 `ArtEvaluationSystem.exe` 启动，并在本地浏览器中访问 `http://127.0.0.1:7860/`。训练流程包括：

1. 上传或选择 `annotations.json`。
2. 设置微调训练轮数。
3. 提取 DINOv2 图像特征。
4. 训练 6 个预测头。
5. 输出训练日志和 Loss。
6. 保存模型文件 `art_dinov2_local_head_model.pth`。

### 4.4 模型使用

模型使用流程包括：

1. 选择已生成的 `art_dinov2_local_head_model.pth`。
2. 上传待评估书画图片，或从 ArtFetch 艺术品库选择已有图片。
3. 后端调用模型推理。
4. 输出笔触、构图、墨量、用色、画工评分，以及市场估值倾向。
5. 将结果保存为自动评估记录，并展示给管理员或专家。

## 5. ArtFetch 当前可结合能力

ArtFetch 当前已经具备大量可复用基础能力，不需要从零开始搭建标注和管理系统。

### 5.1 艺术品数据源

现有 `Artwork` 已保存书画评估需要的大部分基础信息：

- 标题、拍品编号、作者、材质、形制、尺寸、描述。
- 拍卖公司、拍卖会、拍卖专场、拍卖日期、拍卖地点。
- 估价、成交价、成交价状态。
- 预览图、原图、高清图、本地路径、对象存储 key、文件大小和迁移状态。

可结合事项：

- 用现有艺术品列表作为训练样本池。
- 通过作者、材质、拍卖公司、拍卖日期、是否有高清图等条件筛选训练样本。
- 用成交价或估价作为 `valuation_log` 的候选来源，但需要人工确认币种、价格区间和未成交数据处理规则。

### 5.2 高清图与对象存储

ArtFetch 已支持原图、高清图下载和对象存储迁移，并通过鉴权接口读取图片。

可结合事项：

- 训练数据应优先使用高清图或原图，而不是列表缩略图。
- `annotations.json` 中的 `image_path` 可以由后端导出为本地临时训练目录路径，也可以扩展为对象存储 key。
- 测试环境验证时应遵守现有 HD 图片规则，不直接批量下载全分辨率图片；检查文件存在、大小、object key、ETag 和状态即可。

### 5.3 专家评估模块

ArtFetch 已有专家评估项目能力，包括：

- 评估指标定义。
- 评估指标模板。
- 按条件选择艺术品。
- 为每个“专家 + 艺术品”生成评估记录。
- 专家移动端评估页面。
- 专家保存草稿、提交评估。
- 管理员查看进度和结果。
- 审核人审核通过或驳回。
- 多专家互不可见和数据范围控制。

可结合事项：

- 把 PPT 中需要新做的专家打分应用，落到 ArtFetch 现有专家评估页面。
- 新建“书画模型训练标注模板”，默认包含 `brush`、`composition`、`ink`、`color`、`technique` 和 `valuation_log` 对应指标。
- 已审核通过的专家评估结果，可以一键生成 `annotations.json`。
- 多专家评分可以支持平均值、中位数、审核最终值或指定专家值等汇总策略。
- 自动模型评分可以作为专家评估页面的“AI 建议值”，专家仍需确认或修改。

### 5.4 权限与审计

ArtFetch 已有 Sa-Token 权限体系、角色、权限码、菜单/按钮可见性和审计日志。

可结合事项：

- 新增模型训练、模型版本、自动评估、训练数据导出等权限码。
- 训练数据导出、模型训练、模型发布、批量自动评估都应记录审计日志。
- 模型推理读取图片必须走鉴权后的服务端流程，不能暴露裸图片 URL 或本地文件路径。

### 5.5 任务与批处理

ArtFetch 已有抓取任务、图片补充任务、高清图迁移任务等后台任务模式。

可结合事项：

- 模型训练应作为异步任务管理，展示状态、进度、日志和错误。
- 批量自动评估也应作为异步任务，支持按评估项目或艺术品筛选条件执行。
- 可复用任务状态模型：`PENDING`、`RUNNING`、`COMPLETED`、`FAILED`、`CANCELLED`。

### 5.6 导出能力

ArtFetch 已支持 Excel 导出。

可结合事项：

- 导出训练样本清单。
- 导出自动评分结果与专家评分对比。
- 导出模型版本评估报告。
- 后续可扩展 PDF 评估报告，但第一阶段不是必需范围。

## 6. 功能范围

### 6.1 本期范围

第一期只做“训练数据闭环”，不接入模型训练和自动推理。

1. 系统内置并只读维护书画模型训练标注模板。
2. 系统内置并只读维护 5 个训练指标定义。
3. 专家评估支持结构化最终估值金额。
4. 支持从审核通过且已完成的评估项目创建训练数据集草稿。
5. 支持管理员选择汇总策略：所有专家平均值，或整个数据集统一指定某一位专家。
6. 支持管理员人工选择作品子集。
7. 支持只检查已选作品，允许部分样本跳过。
8. 支持异步生成训练数据包 zip。
9. 训练包内包含 `annotations.json`、`manifest.json`、`skipped-samples.json` 和 `images/`。
10. 支持下载训练数据包，并记录审计日志。
11. 增加必要权限、审计和数据范围控制。

### 6.2 暂不包含

- 自动判定真伪。
- 自动替代专家审核。
- 大模型 Backbone 的完整训练。
- 后端调度 `ArtEvaluationSystem.exe` 训练模型。
- 复杂机器学习实验平台能力。
- 模型在线 A/B 测试。
- 自动推理、专家页 AI 建议和 AI/专家评分对比。
- 自动从外部市场行情实时修正估值。
- 面向公众开放的估价服务。

## 7. 核心对象

### 7.1 训练数据集 Auto Evaluation Dataset

训练数据集表示一次用于训练模型的样本集合。

字段建议：

| 字段 | 说明 |
|---|---|
| `id` | 数据集 ID |
| `name` | 数据集名称 |
| `sourceEvaluationId` | 来源评估项目 ID |
| `aggregationStrategy` | 多专家汇总策略，如平均值、中位数、审核最终值 |
| `sampleCount` | 样本数量 |
| `annotationFilePath` | 生成的 `annotations.json` 路径 |
| `imageBasePath` | 训练图片基础目录 |
| `status` | 生成状态 |
| `createdBy` | 创建人 |
| `createdAt` | 创建时间 |

数据集生命周期：

| 状态 | 说明 |
|---|---|
| `DRAFT` | 草稿，可修改名称、策略、指定专家和作品选择 |
| `GENERATING` | 正在异步生成训练包，不可编辑 |
| `READY` | 已生成训练包，包内容不可覆盖 |
| `FAILED` | 生成失败，可修改配置后重试 |
| `ARCHIVED` | 已归档，默认隐藏 |

`READY` 数据集只能归档，不能物理删除；`DRAFT` 和 `FAILED` 可删除。

### 7.2 模型训练任务 Auto Model Training Task

模型训练任务表示一次根据数据集训练预测头的过程。

字段建议：

| 字段 | 说明 |
|---|---|
| `id` | 训练任务 ID |
| `datasetId` | 训练数据集 ID |
| `epochs` | 训练轮数 |
| `status` | 任务状态 |
| `logText` | 训练日志 |
| `lossHistory` | Loss 曲线数据 |
| `modelVersionId` | 训练成功后生成的模型版本 ID |
| `startedAt` | 开始时间 |
| `completedAt` | 完成时间 |
| `errorMessage` | 错误信息 |

### 7.3 模型版本 Auto Evaluation Model Version

模型版本表示一个可用于推理的 `.pth` 文件。

字段建议：

| 字段 | 说明 |
|---|---|
| `id` | 模型版本 ID |
| `name` | 模型名称 |
| `version` | 版本号 |
| `modelFilePath` | `.pth` 文件路径 |
| `datasetId` | 来源数据集 |
| `trainingTaskId` | 来源训练任务 |
| `metricMapping` | 模型输出字段与 ArtFetch 指标的映射 |
| `enabled` | 是否启用 |
| `published` | 是否发布为默认模型 |
| `createdAt` | 创建时间 |

### 7.4 自动评估记录 Auto Evaluation Result

自动评估记录表示某个模型对某件艺术品图片的推理结果。

字段建议：

| 字段 | 说明 |
|---|---|
| `id` | 结果 ID |
| `modelVersionId` | 模型版本 ID |
| `artworkId` | 艺术品 ID |
| `imageSourceType` | 图片来源，如 HD、原图、上传图 |
| `brushScore` | 笔触评分 |
| `compositionScore` | 构图评分 |
| `inkScore` | 墨量/墨色评分 |
| `colorScore` | 用色评分 |
| `techniqueScore` | 技法/画工评分 |
| `valuationLog` | 估值 log 值 |
| `valuationAmount` | 换算后的估值金额 |
| `currency` | 币种 |
| `rawOutput` | 模型原始输出 JSON |
| `status` | 推理状态 |
| `createdAt` | 创建时间 |

## 8. 关键流程

### 8.1 专家标注生成训练数据

1. 管理员创建 ArtFetch 评估项目。
2. 使用系统内置“书画模型训练标注模板”导入指标。
3. 按条件筛选有可用图片的书画作品。
4. 指定专家并发布项目。
5. 专家完成评分和估值。
6. 审核人审核通过评估结果。
7. 管理员创建训练数据集草稿。
8. 管理员选择汇总策略：所有专家平均值，或指定专家。
9. 管理员人工选择作品子集。
10. 后端只校验已选作品是否有训练图片、完整 5 项评分和结构化估值金额。
11. 后端异步生成训练数据包。
12. 数据集进入可下载状态。

### 8.2 训练模型（后续阶段）

1. 管理员选择训练数据集。
2. 设置训练参数，如训练轮数。
3. 后端创建训练任务。
4. 训练服务读取 `annotations.json` 和图片文件。
5. 训练服务生成 `.pth` 模型文件。
6. 后端保存模型版本、训练日志和 Loss 曲线。
7. 管理员检查训练结果并发布模型版本。

### 8.3 使用模型自动评分（后续阶段）

1. 管理员选择模型版本。
2. 选择单件艺术品或批量艺术品范围。
3. 后端读取可用图片。
4. 调用推理服务生成评分和估值。
5. 保存自动评估记录。
6. 在艺术品详情、评估项目详情或专家评估页展示 AI 建议。
7. 专家可接受、修改或忽略 AI 建议。

### 8.4 人工复核反哺模型

1. 专家在 AI 建议基础上提交人工评估。
2. 审核人审核通过。
3. 系统记录 AI 输出与人工最终值差异。
4. 管理员可将新审核结果加入下一轮训练数据集。
5. 模型版本持续迭代。

## 9. 权限要求

建议新增或复用以下权限码：

| 权限码 | 说明 |
|---|---|
| `auto-evaluation:dataset:view` | 查看训练数据集 |
| `auto-evaluation:dataset:create` | 生成训练数据集 |
| `auto-evaluation:dataset:export` | 导出 `annotations.json` |
| `auto-evaluation:model:view` | 查看模型版本 |
| `auto-evaluation:model:train` | 启动模型训练 |
| `auto-evaluation:model:publish` | 发布默认模型 |
| `auto-evaluation:model:disable` | 停用模型版本 |
| `auto-evaluation:result:view` | 查看自动评分结果 |
| `auto-evaluation:result:create` | 执行自动评分 |
| `auto-evaluation:result:compare` | 查看 AI 与专家评分对比 |

权限规则：

- 后端 API 必须使用 `@SaCheckPermission`。
- 专家只能看到自己被分配项目中的 AI 建议和自己的人工评分。
- 管理员可查看项目汇总结果，但不能绕过现有规则代替专家提交人工评分。
- 训练数据导出、模型训练、模型发布、批量推理必须写审计日志。
- 图片读取必须通过服务端鉴权，不能把对象存储私有地址或本地路径直接返回前端。

## 10. 接口建议

第一阶段接口可按以下资源组织：

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/auto-evaluation/datasets` | 从评估项目生成训练数据集 |
| `GET` | `/api/auto-evaluation/datasets` | 查询训练数据集列表 |
| `GET` | `/api/auto-evaluation/datasets/{id}` | 查看数据集详情 |
| `GET` | `/api/auto-evaluation/datasets/{id}/annotations` | 下载或查看 `annotations.json` |
| `POST` | `/api/auto-evaluation/training-tasks` | 创建模型训练任务 |
| `GET` | `/api/auto-evaluation/training-tasks/{id}` | 查看训练任务状态和日志 |
| `GET` | `/api/auto-evaluation/models` | 查询模型版本 |
| `POST` | `/api/auto-evaluation/models/{id}/publish` | 发布模型版本 |
| `POST` | `/api/auto-evaluation/results` | 对单件或批量艺术品执行自动评分 |
| `GET` | `/api/auto-evaluation/results` | 查询自动评分结果 |
| `GET` | `/api/evaluations/{id}/auto-evaluation-comparison` | 查看 AI 与专家评分对比 |

第一期实际 API 聚焦训练数据集：

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/auto-evaluation/datasets` | 查询训练数据集 |
| `GET` | `/api/auto-evaluation/datasets/source-evaluations` | 查询可作为来源的评估项目 |
| `POST` | `/api/auto-evaluation/datasets` | 创建数据集草稿 |
| `PUT` | `/api/auto-evaluation/datasets/{id}` | 更新草稿配置 |
| `GET` | `/api/auto-evaluation/datasets/{id}/artworks` | 分页查询来源项目作品 |
| `POST` | `/api/auto-evaluation/datasets/{id}/selected-artworks` | 批量选择或取消选择作品 |
| `DELETE` | `/api/auto-evaluation/datasets/{id}/selected-artworks` | 清空已选作品 |
| `POST` | `/api/auto-evaluation/datasets/{id}/check` | 检查已选作品 |
| `POST` | `/api/auto-evaluation/datasets/{id}/generate` | 异步生成训练包 |
| `GET` | `/api/auto-evaluation/datasets/{id}/download` | 下载训练包 |

## 11. 与现有评估指标的映射

建议在 ArtFetch 中建立固定的书画模型指标模板：

| 模型字段 | ArtFetch 指标编码 | 指标名称 |
|---|---|---|
| `brush` | `calligraphy_brush` | 笔触/笔墨 |
| `composition` | `calligraphy_composition` | 构图 |
| `ink` | `calligraphy_ink` | 墨量/墨色 |
| `color` | `calligraphy_color` | 用色 |
| `technique` | `calligraphy_technique` | 技法/画工 |
| `valuation_log` | `calligraphy_valuation_log` | 估值 log |

第一期模板只包含 5 个视觉指标，`valuation_log` 不作为专家可编辑指标，而是由结构化最终估值金额计算。

模板要求：

- 模板稳定编码为 `calligraphy_training_annotation`。
- 模板系统内置、只读、不可删除、不可禁用。
- 5 个指标系统内置、只读、不可删除、不可停用。
- 5 个视觉指标固定为 0-10 分，步长 0.1，必填，数值型。
- 生成训练数据集时，评估项目指标必须来自该模板，并再次校验固定 code、范围、步长和必填规则。

估值展示建议：

- 专家侧输入结构化人民币金额，字段为最终估值金额。
- 训练导出时后端统一转换为 `valuation_log = ln(CNY_amount)`。
- 自动推理结果保存 `valuation_log`，同时反算并展示可读金额。
- 一期币种固定为 CNY，金额必须大于 0。

训练包格式：

```text
artfetch-training-dataset-{datasetId}.zip
  annotations.json
  manifest.json
  skipped-samples.json
  images/
    artwork-{artworkId}.{ext}
```

`annotations.json` 必须严格沿用 PPT 的纯数组格式，只包含 `image_path` 和 `features`，其他信息全部写入 `manifest.json` 或 `skipped-samples.json`。

`image_path` 使用包内相对路径，例如 `images/artwork-123.jpg`。

图片规则：

- 高清图优先，原图兜底，不使用预览图。
- 生成训练包时复制图片到独立 `images/` 目录。
- 保留原始格式和尺寸，不做压缩、重采样或转码。
- 缺图样本跳过，记录 `MISSING_TRAINING_IMAGE`。

## 12. 非功能要求

- 训练和推理必须异步执行，避免阻塞 Web 请求。
- 训练任务需记录完整日志和失败原因。
- 模型文件需版本化管理，不能只覆盖固定文件名。
- `annotations.json` 生成过程需可重复，保存来源评估项目、指标映射和汇总策略。
- 批量推理需限制并发，避免大量读取高清图导致存储和带宽压力。
- 所有模型输出都要带模型版本，避免后续无法解释历史结果。
- 自动评分结果需要标注为 AI 建议，不能在 UI 上伪装成人工专家结论。

## 13. 分阶段落地建议

### 阶段一：用 ArtFetch 生成训练数据

- 新增书画模型训练标注模板。
- 从审核通过的评估项目导出 `annotations.json`。
- 支持训练样本图片路径准备和完整性校验。
- 不直接集成训练服务，先保证数据闭环正确。

### 阶段二：接入本地训练服务

- 后端以任务方式调用现有 `ArtEvaluationSystem` 能力。
- 保存训练日志、Loss 和 `.pth` 模型文件。
- 增加模型版本管理页面。

### 阶段三：接入自动推理

- 对单件艺术品执行自动评分。
- 对评估项目内艺术品批量评分。
- 在专家评估页展示 AI 建议。

### 阶段四：模型迭代与质量评估

- 对比 AI 评分与专家最终评分。
- 统计每个指标误差。
- 支持将审核后的新样本加入下一轮训练。
- 形成模型版本质量报告。

## 14. 待确认问题

1. 评分范围是 0-10 还是 1-10，是否允许小数。
2. `valuation_log` 的原始金额来源是专家估价、拍卖估价、成交价，还是三者之一的优先级策略。
3. 估值金额币种是否固定为人民币。
4. 多专家评分用于训练时采用平均值、中位数、审核人确认值，还是只取某个专家。
5. 训练服务是否必须继续使用 `.exe`，还是可以拆成 Python 服务/命令行，方便后端调度和部署。
6. 模型推理是否要求在生产服务器运行，还是允许离线本地运行后上传结果。
7. 训练图片是否必须使用高清图，缺少高清图时是否允许原图或预览图降级。
8. 现有 DINOv2 权重和 `.pth` 模型文件的存储位置、备份策略和访问权限。
