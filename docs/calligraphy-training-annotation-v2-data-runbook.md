# 书画模型训练标注模板 V2 数据制作操作记录

## 1. 本次产物

本记录说明如何制作“书画模型训练标注模板V2”的训练数据包：管理员使用内置 V2 模板创建评估项目，选择 20 个作品，模拟 2 位专家评分，审核通过后生成训练数据集，下载 zip，并解压校验 `annotations.json`。

本次成功产物：

- 运行时间：2026-07-11 17:28:03 CST
- 评估项目 ID：`5`
- 训练数据集 ID：`5`
- 训练包：`/Users/weiyi/code/ArtFetch/docs/generated/calligraphy-training-annotation-v2-20260711-172803/artfetch-training-dataset-5.zip`
- 解压目录：`/Users/weiyi/code/ArtFetch/docs/generated/calligraphy-training-annotation-v2-20260711-172803/unzipped`
- 标注文件：`/Users/weiyi/code/ArtFetch/docs/generated/calligraphy-training-annotation-v2-20260711-172803/unzipped/annotations.json`
- 执行报告：`/Users/weiyi/code/ArtFetch/docs/generated/calligraphy-training-annotation-v2-20260711-172803/scenario-report.md`

校验结果：

- `annotations.json` 样本数：20
- `images/` 图片数：20
- `manifest.json` 样本数：20
- `skipped-samples.json` 跳过样本数：0

## 2. 前置条件

后端服务必须已经运行，并且数据库中已经初始化内置模板：

- 模板编码：`calligraphy_training_annotation_v2`
- 模板名称：`书画模型训练标注模板V2`
- 指标数量：11

11 个指标的导出字段如下：

| 中文指标 | 指标 code | 导出字段 exportField |
| --- | --- | --- |
| 画工 | `calligraphy_v2_craftsmanship` | `craftsmanship` |
| 构图 | `calligraphy_v2_composition` | `composition` |
| 笔墨 | `calligraphy_v2_ink_brushwork` | `ink_brushwork` |
| 用色 | `calligraphy_v2_color` | `color` |
| 题材 | `calligraphy_v2_subject` | `subject` |
| 尺寸 | `calligraphy_v2_size` | `size` |
| 提拔 | `calligraphy_v2_inscription` | `inscription` |
| 来源著录 | `calligraphy_v2_provenance` | `provenance` |
| 稀缺性 | `calligraphy_v2_rarity` | `rarity` |
| 品相 | `calligraphy_v2_condition` | `condition` |
| 裱工 | `calligraphy_v2_mounting` | `mounting` |

训练导出还会额外生成 `valuation_log`，它不是专家评分指标，而是由专家提交的估值金额计算得到的训练目标字段。

## 3. 为什么先补图

训练数据集导出 zip 时需要把每个样本对应的图片一起打包。第一次检查数据库时，虽然库里有 541 个作品，但只有 1 个作品存在可用训练图片路径：

```bash
docker exec artfetch-postgres psql -U artfetch -d artfetch -c \
"select count(*) as total,
        count(*) filter (
          where coalesce(hd_image_object_key,'') <> ''
             or coalesce(hd_image_path,'') <> ''
             or coalesce(original_image_path,'') <> ''
        ) as with_image_path,
        count(*) filter (where coalesce(original_image_path,'') <> '') as with_original_path,
        count(*) filter (
          where coalesce(hd_image_path,'') <> ''
             or coalesce(hd_image_object_key,'') <> ''
        ) as with_hd_path
 from artworks;"
```

当时结果：

```text
 total | with_image_path | with_original_path | with_hd_path
-------+-----------------+--------------------+--------------
   541 |               1 |                  1 |            0
```

所以如果直接跑 20 样本导出，数据集检查会因为可用图片不足而失败。解决办法是先创建“补充原始图片任务”，让系统把至少 20 个作品的原图下载到 `storage/original-images`，并写回 `artworks.original_image_path`。

注意：测试环境不要直接请求 HD 图片字节接口，例如 `/api/artworks/{id}/hd-image` 或 `/api/artworks/{id}/hd-image-v2`。本次只运行原图补充任务，不读取 HD 图片流。

## 4. 登录管理员

后续 API 操作需要管理员 token。本地默认账号密码：

```bash
admin_payload=$(jq -nc \
  --arg username "${ARTFETCH_ADMIN_USERNAME:-admin}" \
  --arg password "${ARTFETCH_ADMIN_PASSWORD:-12345678}" \
  '{username:$username,password:$password}')

token=$(curl -sS -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d "$admin_payload" | jq -r '.tokenValue // empty')
```

如果 `token` 为空，先确认管理员密码和后端服务状态。

## 5. 创建并启动原图补充任务

本次已有检索任务：

- 检索任务 ID：`1`
- 检索任务名称：`赵云`
- 作品数：541

创建原图补充任务：

```bash
payload=$(jq -nc \
  '{name:"赵云 原图补充 - 20样本准备", taskType:"ORIGINAL_IMAGE", targetTaskId:1}')

create=$(curl -sS -X POST http://localhost:8080/api/tasks \
  -H "Authorization: Bearer $token" \
  -H 'Content-Type: application/json' \
  -d "$payload")

echo "$create" | jq '.'
task_id=$(echo "$create" | jq -r '.id')
```

启动任务：

```bash
curl -sS -X POST "http://localhost:8080/api/tasks/$task_id/start" \
  -H "Authorization: Bearer $token" | jq '.'
```

本次创建出的补图任务 ID 是 `2`。

## 6. 等待至少 20 张图片可用

轮询任务状态和数据库图片数量：

```bash
for i in $(seq 1 30); do
  task=$(curl -sS "http://localhost:8080/api/tasks/$task_id" \
    -H "Authorization: Bearer $token")

  imgs=$(docker exec artfetch-postgres psql -U artfetch -d artfetch -tAc \
    "select count(*)
       from artworks
      where coalesce(hd_image_object_key,'') <> ''
         or coalesce(hd_image_path,'') <> ''
         or coalesce(original_image_path,'') <> '';")

  echo "poll=$i status=$(echo "$task" | jq -r '.status') fetched=$(echo "$task" | jq -r '.totalFetched') images=$imgs"

  if [ "$imgs" -ge 20 ]; then
    break
  fi

  sleep 5
done
```

本次第一次轮询时已经达到 23 张可用图片，因此可以继续导出 20 样本训练包。

## 7. 运行 V2 场景脚本生成 20 样本训练包

脚本路径：

```bash
/Users/weiyi/code/ArtFetch/scripts/run-calligraphy-training-annotation-v2-scenario.sh
```

默认样本数就是 20，因此直接运行：

```bash
cd /Users/weiyi/code/ArtFetch
./scripts/run-calligraphy-training-annotation-v2-scenario.sh
```

脚本会自动完成以下步骤：

1. 管理员登录。
2. 验证 11 个内置 V2 指标存在且 `exportField` 正确。
3. 验证内置模板 `书画模型训练标注模板V2` 存在且模板项完整。
4. 创建 2 个临时专家和 1 个临时审核人。
5. 选择 20 个具备训练图片的作品。
6. 使用 V2 模板创建评估项目。
7. 两位专家分别给 20 个作品提交 11 项 0-10 分打分和估值金额。
8. 管理员提交项目审核。
9. 审核人审核通过项目。
10. 创建训练数据集。
11. 选择 20 个作品进入数据集。
12. 调用数据集检查接口确认 `selected=20`、`samples=20`、`skipped=0`。
13. 生成训练 zip。
14. 通过下载接口下载 zip。
15. 解压 zip。
16. 校验 `annotations.json`、`manifest.json`、`skipped-samples.json` 和图片数量。
17. 禁用临时专家和审核人。
18. 保留 READY 状态训练数据集，便于手动下载。

本次脚本输出：

```text
书画 V2 训练标注场景执行完成。
Markdown 报告：/Users/weiyi/code/ArtFetch/docs/generated/calligraphy-training-annotation-v2-20260711-172803/scenario-report.md
JSON 报告：/Users/weiyi/code/ArtFetch/docs/generated/calligraphy-training-annotation-v2-20260711-172803/scenario-report.json
训练包：/Users/weiyi/code/ArtFetch/docs/generated/calligraphy-training-annotation-v2-20260711-172803/artfetch-training-dataset-5.zip
```

## 8. 校验训练包

查看报告：

```bash
sed -n '1,220p' \
  /Users/weiyi/code/ArtFetch/docs/generated/calligraphy-training-annotation-v2-20260711-172803/scenario-report.md
```

关键通过项：

```text
选择 20 件作品：通过
40 条专家评估已提交：通过
训练数据集检查通过：selected=20, samples=20, skipped=0
训练包生成完成：status=READY
训练 zip 与 annotations.json 校验：{"annotations": 20, "images": 20, "manifestSamples": 20}
```

查看 zip 内容：

```bash
zipinfo -1 \
  /Users/weiyi/code/ArtFetch/docs/generated/calligraphy-training-annotation-v2-20260711-172803/artfetch-training-dataset-5.zip
```

统计解压后的标注和图片数量：

```bash
echo annotations=$(jq 'length' \
  /Users/weiyi/code/ArtFetch/docs/generated/calligraphy-training-annotation-v2-20260711-172803/unzipped/annotations.json)

echo images=$(find \
  /Users/weiyi/code/ArtFetch/docs/generated/calligraphy-training-annotation-v2-20260711-172803/unzipped/images \
  -type f | wc -l | tr -d ' ')
```

本次结果：

```text
annotations=20
images=20
```

检查第一条样本字段：

```bash
jq '.[0].features | keys' \
  /Users/weiyi/code/ArtFetch/docs/generated/calligraphy-training-annotation-v2-20260711-172803/unzipped/annotations.json
```

应该包含：

```json
[
  "color",
  "composition",
  "condition",
  "craftsmanship",
  "ink_brushwork",
  "inscription",
  "mounting",
  "provenance",
  "rarity",
  "size",
  "subject",
  "valuation_log"
]
```

## 9. 停止临时补图任务

达到 20 张可用图片并成功导出后，为避免继续下载更多图片占用空间，可以取消原图补充任务：

```bash
curl -sS -X POST "http://localhost:8080/api/tasks/$task_id/cancel" \
  -H "Authorization: Bearer $token" | jq '{id,name,status,totalFetched,currentPage,errorMessage}'
```

本次取消结果：

```json
{
  "id": 2,
  "name": "赵云 原图补充 - 20样本准备",
  "status": "CANCELLED",
  "totalFetched": 75,
  "currentPage": 75,
  "errorMessage": null
}
```

取消任务不会删除已经下载并登记到作品表里的原图。

## 10. 手动下载

脚本生成的数据集会保留为 READY 状态。本次数据集 ID 是 `5`，后续可以通过下载接口手动下载：

```bash
curl -sS -L \
  -o /Users/weiyi/code/ArtFetch/docs/generated/artfetch-training-dataset-5.zip \
  -H "Authorization: Bearer $token" \
  http://localhost:8080/api/auto-evaluation/datasets/5/download
```

也可以在前端“训练数据集”页面找到数据集 `5` 并点击下载。

## 11. 常见问题

### 11.1 为什么只有 1 个图？

因为训练包只能导出有可用训练图片路径的作品。数据库中只有 1 条 `original_image_path` 或 HD 图片路径时，20 样本导出无法成立。需要先跑原图补充任务，等可用图片数达到 20 后再运行 V2 场景脚本。

### 11.2 脚本可以临时只导出 1 个样本吗？

可以，但只用于验证链路，不满足本测试用例目标：

```bash
ARTFETCH_SAMPLE_SIZE=1 ./scripts/run-calligraphy-training-annotation-v2-scenario.sh
```

正式 V2 测试应使用默认 20 个样本。

### 11.3 数据集检查失败怎么办？

查看脚本报告中的 `skipped-samples.json` 和失败原因。常见原因包括：

- 作品缺少训练图片。
- 专家评分缺失。
- 专家估值金额缺失。
- 导出字段 `exportField` 缺失或重复。

### 11.4 训练包生成成功但下载接口 500 怎么办？

下载接口会写审计日志，因此服务方法不能使用只读事务。当前实现已修复为可写事务。若再次出现 500，优先查看后端日志：

```bash
docker logs artfetch-backend --tail 200
```

