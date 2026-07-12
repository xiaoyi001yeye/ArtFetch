# 书画模型训练标注模板 V2 演示 PPT 制作方案

## 1. 能不能做

可以做。

推荐做法是：使用浏览器实时打开 ArtFetch 前端，重新跑一遍“补图 -> 创建评估项目 -> 专家打分 -> 审核 -> 创建训练数据集 -> 下载 zip -> 校验 JSON”的关键流程，在关键页面截图，然后把截图和说明整理成 PPT。

需要注意两点：

1. 不建议把 40 条专家评估全部手工点完再截图。两位专家、20 个作品、每个作品 11 个指标，人工操作会很慢，也容易出错。更适合用脚本/API 完成重复打分，用浏览器展示关键状态和结果页面。
2. 测试环境不要直接打开 HD 图片字节接口，例如 `/api/artworks/{id}/hd-image` 或 `/api/artworks/{id}/hd-image-v2`。演示只使用原图补充任务、列表缩略图、数据集元数据和导出的 zip 文件校验。

因此实际执行方式是“浏览器截图 + 脚本辅助”的混合流程：

- 浏览器负责展示可视化页面和关键操作：登录、任务、指标、模板、评估项目、审核、训练数据集、下载入口。
- 脚本/API 负责重复且不适合演示时手点的动作：创建临时专家、批量提交 40 条专家评估、生成训练 zip、解压校验。
- PPT 负责把过程串成可讲解的步骤，每页展示一个关键动作或关键证据。

## 2. 目标产物

计划生成以下文件：

- 截图目录：`/Users/weiyi/code/ArtFetch/docs/generated/calligraphy-training-annotation-v2-demo/screenshots/`
- PPT 文件：`/Users/weiyi/code/ArtFetch/docs/generated/calligraphy-training-annotation-v2-demo/书画模型训练标注模板V2数据制作演示.pptx`
- 辅助记录：`/Users/weiyi/code/ArtFetch/docs/generated/calligraphy-training-annotation-v2-demo/demo-run-report.md`

PPT 受众：研发、产品、业务同学。

PPT 目标：让别人看完后知道这份 20 图训练数据是如何从系统里做出来的，并能复现关键步骤。

## 3. 前置条件

本地服务需要可访问：

- 后端 API：`http://localhost:8080/api`
- 前端页面：优先使用 Docker 前端 `http://localhost:3000`；如果使用本地 Vite，则使用 `http://localhost:5173`

管理员账号：

- 用户名：`${ARTFETCH_ADMIN_USERNAME:-admin}`
- 密码：`${ARTFETCH_ADMIN_PASSWORD:-12345678}`

数据库里需要有至少 20 个带训练图片路径的作品。可用下面命令确认：

```bash
docker exec artfetch-postgres psql -U artfetch -d artfetch -c \
"select count(*) as total,
        count(*) filter (
          where coalesce(hd_image_object_key,'') <> ''
             or coalesce(hd_image_path,'') <> ''
             or coalesce(original_image_path,'') <> ''
        ) as with_image_path
 from artworks;"
```

如果 `with_image_path < 20`，需要先创建“补充原始图片任务”，直到图片数达到 20。

## 4. 浏览器截图工具

执行时使用浏览器自动化能力截图，建议用 Playwright 或 Codex 浏览器控制能力。

截图原则：

- 每张图只截一个重点页面或一个重点弹窗。
- 保留浏览器真实 UI 状态，不只截接口返回。
- 截图前等待接口加载完成，避免空表格或 loading 状态。
- 截图文件名按顺序编号，便于 PPT 自动引用。
- 不打开 HD 图片下载/查看接口。

建议截图尺寸：

- 桌面宽屏：`1440x1000`
- 如需展示专家移动端，可额外截 `390x844`

## 5. 演示流程与截图点

### 5.1 登录系统

浏览器打开：

```text
http://localhost:3000/login
```

操作：

1. 输入管理员账号密码。
2. 登录进入系统。

截图：

- `01-login.png`：登录页或登录后的首页。

PPT 说明：

- 管理员进入系统，后续由管理员配置任务、项目和训练数据集。

### 5.2 检查可用图片数量

执行数据库检查命令，确认至少 20 个作品已经有可用训练图片。

如果不足 20 个，则浏览器进入：

```text
http://localhost:3000/tasks
```

操作：

1. 点击“新建任务”。
2. 任务类型选择“补充原始图片任务”。
3. 目标检索任务选择已有检索任务，例如 `赵云`。
4. 创建并启动任务。
5. 等待任务列表显示运行进度。

截图：

- `02-task-create-original-image.png`：新建补图任务弹窗。
- `03-task-original-image-running.png`：补图任务运行中。
- `04-task-original-image-enough-images.png`：图片数量达到 20 后的任务或命令证据。

PPT 说明：

- 训练 zip 需要真实图片文件；如果作品只有元数据、没有本地图片路径，导出会失败或跳过样本。
- 本次通过原图补充任务把至少 20 张图片写入 `storage/original-images`，并登记到 `artworks.original_image_path`。

### 5.3 展示 V2 指标库

浏览器进入：

```text
http://localhost:3000/evaluation-metrics
```

操作：

1. 搜索或定位 V2 指标。
2. 展示 11 个指标：画工、构图、笔墨、用色、题材、尺寸、提拔、来源著录、稀缺性、品相、裱工。
3. 展示 `exportField` 字段，如 `craftsmanship`、`composition` 等。

截图：

- `05-metrics-v2-list.png`：指标库中 V2 11 个指标。
- `06-metric-export-field.png`：某个指标详情或表格中 `exportField` 字段。

PPT 说明：

- 中文指标给专家看。
- `code` 是系统内部稳定编码。
- `exportField` 是训练 JSON 字段名，专家不需要关注。

### 5.4 展示 V2 内置模板

浏览器进入：

```text
http://localhost:3000/evaluation-metric-templates
```

操作：

1. 定位模板 `书画模型训练标注模板V2`。
2. 展示模板内含 11 个指标。
3. 展示模板为内置模板，不建议编辑和删除。

截图：

- `07-template-v2-list.png`：模板列表中 V2 模板。
- `08-template-v2-items.png`：模板项包含 11 个指标和导出字段。

PPT 说明：

- V2 模板用于稳定复现实验口径。
- 后续变更口径应新增 V3 模板，而不是直接改 V2。

### 5.5 创建评估项目

浏览器进入：

```text
http://localhost:3000/evaluations/new
```

操作：

1. 新建评估项目。
2. 导入 `书画模型训练标注模板V2`。
3. 选择 20 个作品。
4. 选择 2 位专家和 1 位审核人。
5. 保存并发布项目。

如果手工创建太慢，可以让脚本创建项目，然后浏览器打开项目详情页截图。

截图：

- `09-evaluation-create-template.png`：创建项目时选择 V2 模板。
- `10-evaluation-create-artworks.png`：选择 20 个作品。
- `11-evaluation-detail-published.png`：项目详情，展示项目状态、作品数、专家、指标。

PPT 说明：

- 评估项目是专家打分的工作单元。
- 这个项目把 20 个作品、2 位专家、1 位审核人和 V2 11 项指标绑定在一起。

### 5.6 专家评分

浏览器可打开专家视角页面：

```text
http://localhost:3000/my-evaluations
```

或具体评分页：

```text
http://localhost:3000/evaluations/{evaluationId}/artworks/{artworkId}/review
```

操作：

1. 使用专家账号登录。
2. 打开一个作品评分页。
3. 展示 11 个 0-10 分数值型评分项。
4. 展示估值金额字段。

实际批量提交建议由脚本/API 完成：

```bash
./scripts/run-calligraphy-training-annotation-v2-scenario.sh
```

截图：

- `12-expert-project-list.png`：专家看到分配项目。
- `13-expert-review-11-metrics.png`：评分页显示 11 个指标。
- `14-evaluation-ready-for-review.png`：40 条专家评估完成，项目进入待审核。

PPT 说明：

- 专家只关注中文指标和打分控件，不需要关心 `exportField`。
- 两位专家各评 20 件作品，共 40 条专家评估。
- 导出时使用平均策略聚合两位专家的评分。

### 5.7 审核项目

浏览器进入：

```text
http://localhost:3000/evaluations/{evaluationId}/audit
```

操作：

1. 审核人查看专家评分结果。
2. 点击审核通过。
3. 项目状态变为 `COMPLETED`，审核结果为 `APPROVED`。

截图：

- `15-evaluation-audit-page.png`：审核页。
- `16-evaluation-approved.png`：项目审核通过后的详情页。

PPT 说明：

- 只有审核通过且已完成的项目，才能作为训练数据集来源。

### 5.8 创建训练数据集

浏览器进入：

```text
http://localhost:3000/auto-evaluation/datasets
```

操作：

1. 新建训练数据集。
2. 选择来源评估项目。
3. 聚合策略选择 `AVERAGE_ALL_EXPERTS`。
4. 保存进入数据集详情。
5. 选择 20 个作品。
6. 点击检查或刷新检查结果。

截图：

- `17-dataset-create-source-project.png`：选择来源评估项目。
- `18-dataset-detail-selected-artworks.png`：数据集详情中已选择 20 个作品。
- `19-dataset-check-pass.png`：检查通过，`selected=20`、`samples=20`、`skipped=0`。

PPT 说明：

- 数据集会再次校验图片、评分、估值金额和导出字段。
- `skipped=0` 表示 20 个作品全部可用于训练。

### 5.9 生成并下载训练包

浏览器在训练数据集详情页操作：

1. 点击生成训练包。
2. 等待状态变为 `READY`。
3. 点击下载。

截图：

- `20-dataset-generating.png`：训练包生成中。
- `21-dataset-ready-download.png`：训练包 READY，下载按钮可用。

PPT 说明：

- 训练包是 zip 文件，包含 `annotations.json`、`manifest.json`、`skipped-samples.json` 和 `images/`。
- 下载接口会记录审计日志，便于追溯。

### 5.10 解压并校验 JSON

终端执行：

```bash
zipinfo -1 /path/to/artfetch-training-dataset-{datasetId}.zip

jq 'length' /path/to/unzipped/annotations.json

find /path/to/unzipped/images -type f | wc -l

jq '.[0].features | keys' /path/to/unzipped/annotations.json
```

截图：

- `22-zip-content.png`：zip 内容列表。
- `23-annotations-json.png`：`annotations.json` 中 20 条样本和 12 个字段。
- `24-final-report.png`：脚本报告全部通过。

PPT 说明：

- 最终训练特征包含 11 个评分字段和 1 个 `valuation_log`。
- 本次目标是 20 个样本，因此必须看到 `annotations=20`、`images=20`。

## 6. PPT 建议结构

建议做 12-14 页，避免太长。

| 页码 | 标题 | 内容 |
| --- | --- | --- |
| 1 | 演示目标 | 制作书画模型训练标注模板 V2 的 20 样本训练包 |
| 2 | 数据链路总览 | 补图 -> 评估项目 -> 专家评分 -> 审核 -> 数据集 -> 下载 zip |
| 3 | 前置数据检查 | 为什么需要先确认至少 20 张训练图片 |
| 4 | 补充原始图片 | 补图任务创建和运行状态截图 |
| 5 | V2 指标库 | 11 个中文指标、英文 code、exportField |
| 6 | V2 内置模板 | 模板列表和模板项 |
| 7 | 创建评估项目 | 选择模板、作品、专家、审核人 |
| 8 | 专家评分 | 11 个 0-10 分指标和估值金额 |
| 9 | 项目审核 | 审核页和审核通过状态 |
| 10 | 创建训练数据集 | 来源项目、20 个作品、平均专家策略 |
| 11 | 检查并生成训练包 | selected=20、samples=20、skipped=0、READY |
| 12 | 下载与解压 | zip 内容和本地解压目录 |
| 13 | JSON 结构 | `annotations.json` 字段说明 |
| 14 | 结论 | 本次产物路径、数据集 ID、可手动下载 |

## 7. 实际执行顺序

推荐执行顺序如下：

1. 启动或确认后端、前端服务可访问。
2. 清理或新建演示输出目录：

   ```bash
   mkdir -p /Users/weiyi/code/ArtFetch/docs/generated/calligraphy-training-annotation-v2-demo/screenshots
   ```

3. 用浏览器登录管理员账号，截图登录和导航。
4. 检查数据库可用图片数。
5. 如果不足 20，浏览器创建原图补充任务，截图任务弹窗和运行状态。
6. 图片数达到 20 后，截图任务状态或数据库检查结果。
7. 浏览器打开指标库和模板页，截图 V2 指标和 V2 模板。
8. 运行 V2 场景脚本，生成一个新的 20 样本评估项目和训练数据集：

   ```bash
   cd /Users/weiyi/code/ArtFetch
   ./scripts/run-calligraphy-training-annotation-v2-scenario.sh
   ```

9. 从脚本输出中记录新的 `projectId`、`datasetId`、训练包路径、报告路径。
10. 浏览器打开项目详情、审核页、训练数据集详情页，截图关键状态。
11. 终端校验 zip、图片数量、`annotations.json` 字段，截图命令输出。
12. 用截图和说明生成 PPT。
13. 打开 PPT 做快速检查，确认截图清晰、路径正确、页序连贯。

## 8. 需要实时重跑还是复用已有数据

有两种方式：

### 方式 A：实时重跑

适合做“真实演示”。会新建一组临时专家、审核人、评估项目和训练数据集。

优点：

- 证明流程当前仍然能跑通。
- 截图里的时间、项目、数据集都是最新的。

代价：

- 会多产生一组演示数据。
- 如果可用图片不足，需要先跑补图任务。

### 方式 B：复用已生成数据

适合快速出 PPT。直接使用本次已生成数据：

- 评估项目 ID：`5`
- 训练数据集 ID：`5`
- 训练包：`/Users/weiyi/code/ArtFetch/docs/generated/calligraphy-training-annotation-v2-20260711-172803/artfetch-training-dataset-5.zip`

优点：

- 更快。
- 不会继续污染数据库。

代价：

- 截图展示的是已有结果，不是重新实时跑出的结果。

如果要给别人演示“具体怎么操作”，推荐方式 A；如果只需要讲清楚流程和结果，方式 B 就够。

## 9. 风险和处理

| 风险 | 处理 |
| --- | --- |
| 前端未启动 | 使用 Docker 前端 `http://localhost:3000`，或在 `frontend/` 运行 `npm run dev` |
| 管理员登录失败 | 检查 `ARTFETCH_ADMIN_USERNAME`、`ARTFETCH_ADMIN_PASSWORD` |
| 可用图片不足 20 | 先创建原图补充任务，达到 20 后取消任务 |
| 训练数据集 skipped 不为 0 | 查看 `skipped-samples.json`，通常是缺图、缺评分、缺估值或导出字段异常 |
| 下载 zip 失败 | 查看后端日志和下载接口权限，确认数据集状态为 `READY` |
| 截图里数据太多不清晰 | 放大浏览器缩放到 110%-125%，或者只截表格/弹窗局部 |

## 10. 下一步实际制作 PPT 时的执行清单

开始实际制作 PPT 时，按以下清单执行：

1. 确认前后端服务可访问。
2. 选择实时重跑或复用已有数据。
3. 打开浏览器，按第 5 节截图点逐张截图。
4. 运行或复用 V2 场景脚本产物。
5. 生成 PPT。
6. 打开 PPT 人工检查：
   - 每页标题明确。
   - 每张截图对应一个操作步骤。
   - 最终页包含训练包路径、数据集 ID、校验结果。
   - 明确写出 `annotations=20`、`images=20`、`skipped=0`。

