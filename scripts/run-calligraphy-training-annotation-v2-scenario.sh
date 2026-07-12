#!/usr/bin/env bash

set -u
set -o pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

CURL_BIN="${CURL_BIN:-$(command -v curl || true)}"
JQ_BIN="${JQ_BIN:-$(command -v jq || true)}"
UNZIP_BIN="${UNZIP_BIN:-$(command -v unzip || true)}"
PYTHON_BIN="${PYTHON_BIN:-$(command -v python3 || true)}"

BASE_URL="${ARTFETCH_BASE_URL:-http://localhost:8080/api}"
ADMIN_USERNAME="${ARTFETCH_ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ARTFETCH_ADMIN_PASSWORD:-12345678}"
SCENARIO_ARTIST="${ARTFETCH_SCENARIO_ARTIST:-}"
SAMPLE_SIZE="${ARTFETCH_SAMPLE_SIZE:-20}"
REPORT_OUTPUT_DIR="${ARTFETCH_REPORT_OUTPUT_DIR:-$REPO_ROOT/docs/generated}"
KEEP_RESOURCES="${ARTFETCH_KEEP_RESOURCES:-false}"

RUN_ID="$(date '+%Y%m%d-%H%M%S')"
RUN_AT="$(date '+%Y-%m-%d %H:%M:%S %Z')"
OUTPUT_DIR="$REPORT_OUTPUT_DIR/calligraphy-training-annotation-v2-$RUN_ID"
UNZIP_DIR="$OUTPUT_DIR/unzipped"
REPORT_JSON="$OUTPUT_DIR/scenario-report.json"
REPORT_MD="$OUTPUT_DIR/scenario-report.md"
ZIP_PATH=""

ADMIN_TOKEN=""
EXPERT1_TOKEN=""
EXPERT2_TOKEN=""
AUDITOR_TOKEN=""

EXPERT1_ID=""
EXPERT2_ID=""
AUDITOR_ID=""
PROJECT_ID=""
TEMPLATE_ID=""
DATASET_ID=""

PROJECT_STATUS_AFTER_CREATE=""
PROJECT_STATUS_AFTER_PUBLISH=""
PROJECT_STATUS_AFTER_SUBMIT=""
PROJECT_STATUS_AFTER_APPROVE=""
DATASET_STATUS=""

LAST_RESPONSE_CODE=""
LAST_RESPONSE_BODY=""

ARTWORK_IDS_JSON="[]"
ARTWORK_TITLES_JSON="[]"
METRIC_IDS_JSON="[]"
METRIC_FIELDS_JSON='["craftsmanship","composition","ink_brushwork","color","subject","size","inscription","provenance","rarity","condition","mounting"]'

CHECK_NAMES=()
CHECK_RESULTS=()
CHECK_DETAILS=()
FAILURES=()
WARNINGS=()
CLEANUP_NOTES=()

usage() {
  cat <<EOF
用法：
  ./scripts/run-calligraphy-training-annotation-v2-scenario.sh

可选环境变量：
  ARTFETCH_BASE_URL            后端 API 地址，默认 http://localhost:8080/api
  ARTFETCH_ADMIN_USERNAME      管理员用户名，默认 admin
  ARTFETCH_ADMIN_PASSWORD      管理员密码，默认 12345678
  ARTFETCH_SCENARIO_ARTIST     可选作者筛选关键词
  ARTFETCH_SAMPLE_SIZE         样本数量，默认 20，本用例按 20 断言
  ARTFETCH_REPORT_OUTPUT_DIR   报告输出目录，默认 docs/generated
  ARTFETCH_KEEP_RESOURCES      true 时不清理临时用户/失败数据集

说明：
  - 脚本使用系统内置“书画模型训练标注模板V2”
  - 脚本会创建临时专家和审核人，并在结束后禁用
  - 成功生成的 READY 数据集和本地 zip 证据包会保留
EOF
}

require_command() {
  local cmd_path="$1"
  local label="$2"
  if [ -z "$cmd_path" ]; then
    echo "缺少依赖命令: $label" >&2
    exit 1
  fi
}

record_check() {
  CHECK_NAMES+=("$1")
  CHECK_RESULTS+=("$2")
  CHECK_DETAILS+=("$3")
  if [ "$2" != "true" ]; then
    FAILURES+=("$1：$3")
  fi
}

append_failure() {
  FAILURES+=("$1")
}

append_cleanup_note() {
  CLEANUP_NOTES+=("$1")
}

json_value() {
  local query="$1"
  printf '%s' "$LAST_RESPONSE_BODY" | "$JQ_BIN" -r "$query"
}

http_request() {
  local method="$1"
  local path="$2"
  local token="${3:-}"
  local body="${4:-}"
  local tmp_file
  tmp_file="$(mktemp)"

  if [ -n "$body" ]; then
    if [ -n "$token" ]; then
      LAST_RESPONSE_CODE="$("$CURL_BIN" -sS -o "$tmp_file" -w '%{http_code}' -X "$method" "$BASE_URL$path" -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d "$body")"
    else
      LAST_RESPONSE_CODE="$("$CURL_BIN" -sS -o "$tmp_file" -w '%{http_code}' -X "$method" "$BASE_URL$path" -H 'Content-Type: application/json' -d "$body")"
    fi
  else
    if [ -n "$token" ]; then
      LAST_RESPONSE_CODE="$("$CURL_BIN" -sS -o "$tmp_file" -w '%{http_code}' -X "$method" "$BASE_URL$path" -H "Authorization: Bearer $token")"
    else
      LAST_RESPONSE_CODE="$("$CURL_BIN" -sS -o "$tmp_file" -w '%{http_code}' -X "$method" "$BASE_URL$path")"
    fi
  fi

  LAST_RESPONSE_BODY="$(cat "$tmp_file")"
  rm -f "$tmp_file"
}

ensure_success_code() {
  local expected="$1"
  local step="$2"
  if [ "$LAST_RESPONSE_CODE" -ne "$expected" ]; then
    append_failure "$step：HTTP ${LAST_RESPONSE_CODE}，响应：${LAST_RESPONSE_BODY}"
    return 1
  fi
}

login_user() {
  local username="$1"
  local password="$2"
  local step="$3"
  local payload
  payload="$("$JQ_BIN" -nc --arg username "$username" --arg password "$password" '{username:$username,password:$password}')"
  http_request POST "/auth/login" "" "$payload"
  ensure_success_code 200 "$step" || return 1
}

select_artworks() {
  local tmp_file page code selected
  tmp_file="$(mktemp)"
  page=0
  printf '[]' > "$tmp_file"

  while [ "$("$JQ_BIN" 'length' "$tmp_file")" -lt "$SAMPLE_SIZE" ] && [ "$page" -lt 10 ]; do
    local page_file
    page_file="$(mktemp)"
    if [ -n "$SCENARIO_ARTIST" ]; then
      code="$("$CURL_BIN" -sS -G -o "$page_file" -w '%{http_code}' "$BASE_URL/artworks" \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        --data-urlencode "artist=$SCENARIO_ARTIST" \
        --data-urlencode "page=$page" \
        --data-urlencode "size=100")"
    else
      code="$("$CURL_BIN" -sS -G -o "$page_file" -w '%{http_code}' "$BASE_URL/artworks" \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        --data-urlencode "page=$page" \
        --data-urlencode "size=100")"
    fi
    if [ "$code" -ne 200 ]; then
      append_failure "选择作品：HTTP $code，响应：$(cat "$page_file")"
      rm -f "$tmp_file" "$page_file"
      return 1
    fi
    selected="$("$JQ_BIN" '[.items[] | select(.hdImageAvailable == true or .originalImageAvailable == true) | {id,title}]' "$page_file")"
    "$JQ_BIN" -s 'add | unique_by(.id)' "$tmp_file" <(printf '%s' "$selected") > "$tmp_file.next"
    mv "$tmp_file.next" "$tmp_file"
    rm -f "$page_file"
    page=$((page + 1))
  done

  if [ "$("$JQ_BIN" 'length' "$tmp_file")" -lt "$SAMPLE_SIZE" ]; then
    append_failure "选择作品：具备训练图片元数据的作品不足 $SAMPLE_SIZE 件"
    rm -f "$tmp_file"
    return 1
  fi

  ARTWORK_IDS_JSON="$("$JQ_BIN" --argjson size "$SAMPLE_SIZE" '[.[0:$size][] .id]' "$tmp_file")"
  ARTWORK_TITLES_JSON="$("$JQ_BIN" --argjson size "$SAMPLE_SIZE" '[.[0:$size][] .title]' "$tmp_file")"
  rm -f "$tmp_file"
  return 0
}

assert_builtin_metrics_v2() {
  http_request GET "/evaluation-metrics/enabled" "$ADMIN_TOKEN"
  ensure_success_code 200 "查询启用指标" || return 1
  local ok detail
  ok="$(printf '%s' "$LAST_RESPONSE_BODY" | "$JQ_BIN" -r --argjson fields "$METRIC_FIELDS_JSON" '
    . as $items
    | all($fields[]; . as $field
      | ($items | map(select(.exportField == $field and .builtIn == true and .enabled == true and (.scoreType | ascii_downcase) == "numeric" and .minScore == 0 and .maxScore == 10 and .scoreStep == 0.1 and .required == true)) | length) == 1)
  ')"
  detail="$(printf '%s' "$LAST_RESPONSE_BODY" | "$JQ_BIN" -r --argjson fields "$METRIC_FIELDS_JSON" '[.[] | select(.exportField as $f | $fields | index($f)) | .exportField] | join(",")')"
  record_check "内置 V2 指标完整" "$ok" "匹配字段=$detail"
  [ "$ok" = "true" ]
}

assert_builtin_template_v2() {
  http_request GET "/evaluation-metric-templates?page=0&size=200" "$ADMIN_TOKEN"
  ensure_success_code 200 "查询指标模板" || return 1
  TEMPLATE_ID="$(json_value '.items[] | select(.code=="calligraphy_training_annotation_v2" and .builtIn==true and .enabled==true) | .id' | head -n 1)"
  if [ -z "$TEMPLATE_ID" ]; then
    record_check "内置 V2 模板存在" "false" "未找到 code=calligraphy_training_annotation_v2 的启用内置模板"
    return 1
  fi
  record_check "内置 V2 模板存在" "true" "templateId=$TEMPLATE_ID"

  http_request GET "/evaluation-metric-templates/$TEMPLATE_ID/items" "$ADMIN_TOKEN"
  ensure_success_code 200 "查询 V2 模板项" || return 1
  local ok detail
  ok="$(printf '%s' "$LAST_RESPONSE_BODY" | "$JQ_BIN" -r --argjson fields "$METRIC_FIELDS_JSON" '
    length == ($fields | length)
    and ([.[].exportField] == $fields)
    and all(.[]; (.scoreType | ascii_downcase) == "numeric" and .minScore == 0 and .maxScore == 10 and .scoreStep == 0.1 and .required == true)
  ')"
  detail="$(printf '%s' "$LAST_RESPONSE_BODY" | "$JQ_BIN" -r '[.[].exportField] | join(",")')"
  record_check "内置 V2 模板项完整" "$ok" "字段顺序=$detail"
  [ "$ok" = "true" ]
}

create_users() {
  local payload
  payload="$("$JQ_BIN" -nc --arg username "codex_v2_exp1_$RUN_ID" --arg password "Expert12345" --arg displayName "V2场景专家甲" '{username:$username,password:$password,displayName:$displayName,roles:["EXPERT"]}')"
  http_request POST "/users" "$ADMIN_TOKEN" "$payload"
  ensure_success_code 200 "创建专家甲" || return 1
  EXPERT1_ID="$(json_value '.id')"
  local expert1_username
  expert1_username="$(json_value '.username')"

  payload="$("$JQ_BIN" -nc --arg username "codex_v2_exp2_$RUN_ID" --arg password "Expert22345" --arg displayName "V2场景专家乙" '{username:$username,password:$password,displayName:$displayName,roles:["EXPERT"]}')"
  http_request POST "/users" "$ADMIN_TOKEN" "$payload"
  ensure_success_code 200 "创建专家乙" || return 1
  EXPERT2_ID="$(json_value '.id')"
  local expert2_username
  expert2_username="$(json_value '.username')"

  payload="$("$JQ_BIN" -nc --arg username "codex_v2_aud_$RUN_ID" --arg password "Auditor12345" --arg displayName "V2场景审核人" '{username:$username,password:$password,displayName:$displayName,roles:["AUDITOR"]}')"
  http_request POST "/users" "$ADMIN_TOKEN" "$payload"
  ensure_success_code 200 "创建审核人" || return 1
  AUDITOR_ID="$(json_value '.id')"
  local auditor_username
  auditor_username="$(json_value '.username')"

  login_user "$expert1_username" "Expert12345" "专家甲登录" || return 1
  EXPERT1_TOKEN="$(json_value '.tokenValue // empty')"
  login_user "$expert2_username" "Expert22345" "专家乙登录" || return 1
  EXPERT2_TOKEN="$(json_value '.tokenValue // empty')"
  login_user "$auditor_username" "Auditor12345" "审核人登录" || return 1
  AUDITOR_TOKEN="$(json_value '.tokenValue // empty')"
}

create_project() {
  http_request GET "/evaluation-metric-templates/$TEMPLATE_ID/items" "$ADMIN_TOKEN"
  ensure_success_code 200 "读取 V2 模板项" || return 1
  local metrics_json criteria_json payload
  metrics_json="$LAST_RESPONSE_BODY"
  criteria_json="[]"
  if [ -n "$SCENARIO_ARTIST" ]; then
    criteria_json="$("$JQ_BIN" -nc --arg artist "$SCENARIO_ARTIST" '[{fieldName:"artist",fieldLabel:"作者",operator:"contains",value:$artist,valueType:"text"}]')"
  fi
  payload="$("$JQ_BIN" -nc \
    --arg name "书画模型训练标注V2自动化场景-$RUN_ID" \
    --arg desc "用于命令化回归 V2 训练标注和 zip 导出" \
    --argjson auditorId "$AUDITOR_ID" \
    --argjson criteria "$criteria_json" \
    --argjson artworkIds "$ARTWORK_IDS_JSON" \
    --argjson expert1 "$EXPERT1_ID" \
    --argjson expert2 "$EXPERT2_ID" \
    --argjson metrics "$metrics_json" \
    '{name:$name,description:$desc,auditorId:$auditorId,criteria:$criteria,artworkIds:$artworkIds,expertIds:[$expert1,$expert2],metrics:$metrics}')"
  http_request POST "/evaluations" "$ADMIN_TOKEN" "$payload"
  ensure_success_code 200 "创建 V2 评估项目" || return 1
  PROJECT_ID="$(json_value '.id')"
  PROJECT_STATUS_AFTER_CREATE="$(json_value '.status')"
  record_check "评估项目配置正确" "$([ "$(json_value '.artworkCount')" -eq "$SAMPLE_SIZE" ] && [ "$(json_value '.expertCount')" -eq 2 ] && [ "$(json_value '.expectedReviewCount')" -eq $((SAMPLE_SIZE * 2)) ] && [ "$(json_value '.metrics | length')" -eq 11 ] && echo true || echo false)" "projectId=$PROJECT_ID"

  http_request POST "/evaluations/$PROJECT_ID/publish" "$ADMIN_TOKEN"
  ensure_success_code 200 "发布 V2 评估项目" || return 1
  PROJECT_STATUS_AFTER_PUBLISH="$(json_value '.status')"
  METRIC_IDS_JSON="$(printf '%s' "$LAST_RESPONSE_BODY" | "$JQ_BIN" '[.metrics | sort_by(.sortOrder)[] .id]')"
}

score_payload() {
  local artwork_index="$1"
  local expert_index="$2"
  local amount=$((100000 + artwork_index * 10000 + expert_index * 5000))
  "$JQ_BIN" -nc \
    --arg finalEstimate "RMB $amount" \
    --argjson amount "$amount" \
    --argjson artworkIndex "$artwork_index" \
    --argjson expertIndex "$expert_index" \
    --argjson metricIds "$METRIC_IDS_JSON" \
    '{
      finalEstimate:$finalEstimate,
      finalEstimateAmount:$amount,
      finalEstimateCurrency:"CNY",
      comment:"自动化模拟评分：V2 训练标注链路验证。",
      scores: ($metricIds | to_entries | map({
        projectMetricId:.value,
        score: (((5 + (((($artworkIndex * 7) + (((.key + 1) * 3)) + $expertIndex) % 50) / 10)) * 10) | round / 10)
      }))
    }'
}

submit_scores_for_expert() {
  local token="$1"
  local expert_index="$2"
  local count i artwork_id payload
  count="$("$JQ_BIN" 'length' <<< "$ARTWORK_IDS_JSON")"
  for ((i = 0; i < count; i++)); do
    artwork_id="$("$JQ_BIN" -r ".[$i]" <<< "$ARTWORK_IDS_JSON")"
    payload="$(score_payload "$((i + 1))" "$expert_index")"
    http_request POST "/evaluations/$PROJECT_ID/artworks/$artwork_id/my-review/submit" "$token" "$payload"
    ensure_success_code 200 "专家 $expert_index 提交作品 $artwork_id" || return 1
  done
}

approve_project() {
  submit_scores_for_expert "$EXPERT1_TOKEN" 1 || return 1
  submit_scores_for_expert "$EXPERT2_TOKEN" 2 || return 1

  http_request GET "/evaluations/$PROJECT_ID" "$ADMIN_TOKEN"
  ensure_success_code 200 "刷新待审核状态" || return 1
  local ready
  ready="$(json_value '.status == "READY_FOR_REVIEW"')"
  record_check "$((SAMPLE_SIZE * 2)) 条专家评估已提交" "$ready" "status=$(json_value '.status'), completed=$(json_value '.completedCount')"

  http_request POST "/evaluations/$PROJECT_ID/submit-review" "$ADMIN_TOKEN"
  ensure_success_code 200 "提交项目审核" || return 1
  PROJECT_STATUS_AFTER_SUBMIT="$(json_value '.status')"

  http_request POST "/evaluations/$PROJECT_ID/audit/approve" "$AUDITOR_TOKEN" '{"comment":"V2 自动化训练标注样本完整，同意通过。"}'
  ensure_success_code 200 "审核通过项目" || return 1
  PROJECT_STATUS_AFTER_APPROVE="$(json_value '.status')"
  local approved
  approved="$(json_value '.status == "COMPLETED" and .auditResult == "APPROVED"')"
  record_check "项目审核通过" "$approved" "status=$PROJECT_STATUS_AFTER_APPROVE"
}

create_dataset_and_download_zip() {
  local payload check_ok generate_ok poll status download_code
  payload="$("$JQ_BIN" -nc --arg name "书画模型训练标注模板V2数据集-$RUN_ID" --argjson projectId "$PROJECT_ID" '{name:$name,sourceEvaluationId:$projectId,aggregationStrategy:"AVERAGE_ALL_EXPERTS"}')"
  http_request POST "/auto-evaluation/datasets" "$ADMIN_TOKEN" "$payload"
  ensure_success_code 200 "创建训练数据集" || return 1
  DATASET_ID="$(json_value '.id')"

  payload="$("$JQ_BIN" -nc --argjson artworkIds "$ARTWORK_IDS_JSON" '{selected:true,artworkIds:$artworkIds}')"
  http_request POST "/auto-evaluation/datasets/$DATASET_ID/selected-artworks" "$ADMIN_TOKEN" "$payload"
  ensure_success_code 200 "选择数据集作品" || return 1

  http_request POST "/auto-evaluation/datasets/$DATASET_ID/check" "$ADMIN_TOKEN"
  ensure_success_code 200 "检查训练数据集" || return 1
  check_ok="$(json_value ".selectedCount == $SAMPLE_SIZE and .sampleCount == $SAMPLE_SIZE and .skippedCount == 0 and (.exceedsMobileHardLimit == false)")"
  record_check "训练数据集检查通过" "$check_ok" "selected=$(json_value '.selectedCount'), samples=$(json_value '.sampleCount'), skipped=$(json_value '.skippedCount')"
  [ "$check_ok" = "true" ] || return 1

  http_request POST "/auto-evaluation/datasets/$DATASET_ID/generate" "$ADMIN_TOKEN"
  ensure_success_code 200 "启动训练包生成" || return 1

  generate_ok=false
  for poll in $(seq 1 60); do
    sleep 2
    http_request GET "/auto-evaluation/datasets/$DATASET_ID" "$ADMIN_TOKEN"
    ensure_success_code 200 "轮询训练包状态" || return 1
    status="$(json_value '.status')"
    DATASET_STATUS="$status"
    if [ "$status" = "READY" ]; then
      generate_ok=true
      break
    fi
    if [ "$status" = "FAILED" ]; then
      append_failure "训练包生成失败：$(json_value '.errorMessage // empty')"
      return 1
    fi
  done
  record_check "训练包生成完成" "$generate_ok" "status=$DATASET_STATUS"
  [ "$generate_ok" = "true" ] || return 1

  ZIP_PATH="$OUTPUT_DIR/artfetch-training-dataset-$DATASET_ID.zip"
  local tmp_file
  tmp_file="$(mktemp)"
  download_code="$("$CURL_BIN" -sS -L -o "$tmp_file" -w '%{http_code}' "$BASE_URL/auto-evaluation/datasets/$DATASET_ID/download" -H "Authorization: Bearer $ADMIN_TOKEN")"
  if [ "$download_code" -ne 200 ]; then
    append_failure "下载训练包：HTTP ${download_code}，响应：$(cat "$tmp_file")"
    rm -f "$tmp_file"
    return 1
  fi
  mv "$tmp_file" "$ZIP_PATH"
  mkdir -p "$UNZIP_DIR"
  "$UNZIP_BIN" -q -o "$ZIP_PATH" -d "$UNZIP_DIR"
}

assert_zip_and_annotations() {
  local verify_output verify_code ok
  verify_output="$("$PYTHON_BIN" - "$ZIP_PATH" "$UNZIP_DIR" "$ARTWORK_IDS_JSON" "$METRIC_FIELDS_JSON" "$SAMPLE_SIZE" 2>&1 <<'PY'
import json, math, re, sys, zipfile
from pathlib import Path

zip_path = Path(sys.argv[1])
unzip_dir = Path(sys.argv[2])
artwork_ids = json.loads(sys.argv[3])
fields = json.loads(sys.argv[4])
sample_size = int(sys.argv[5])

with zipfile.ZipFile(zip_path) as zf:
    names = zf.namelist()
    required = {"annotations.json", "manifest.json", "skipped-samples.json"}
    missing = sorted(required - set(names))
    if missing:
        raise SystemExit(f"zip missing files: {missing}")
    image_infos = [info for info in zf.infolist() if info.filename.startswith("images/") and not info.is_dir()]
    if len(image_infos) != sample_size:
        raise SystemExit(f"image count {len(image_infos)} != {sample_size}")
    zero = [info.filename for info in image_infos if info.file_size <= 0]
    if zero:
        raise SystemExit(f"zero-size images: {zero[:3]}")

annotations_path = unzip_dir / "annotations.json"
skipped_path = unzip_dir / "skipped-samples.json"
manifest_path = unzip_dir / "manifest.json"
annotations = json.loads(annotations_path.read_text())
skipped = json.loads(skipped_path.read_text())
manifest = json.loads(manifest_path.read_text())

if not isinstance(annotations, list) or len(annotations) != sample_size:
    raise SystemExit(f"annotation length {len(annotations) if isinstance(annotations, list) else 'not-list'} != {sample_size}")
if skipped:
    raise SystemExit(f"skipped samples not empty: {len(skipped)}")
if manifest.get("sampleCount") != sample_size or manifest.get("skippedCount") != 0:
    raise SystemExit(f"manifest counts invalid: sample={manifest.get('sampleCount')} skipped={manifest.get('skippedCount')}")

by_artwork = {artwork_id: index + 1 for index, artwork_id in enumerate(artwork_ids)}
for item in annotations:
    features = item.get("features")
    if not isinstance(features, dict):
        raise SystemExit("sample features missing")
    for field in fields:
        value = features.get(field)
        if not isinstance(value, (int, float)) or value < 0 or value > 10:
            raise SystemExit(f"invalid feature {field}: {value}")
    value = features.get("valuation_log")
    if not isinstance(value, (int, float)) or value <= 0:
        raise SystemExit(f"invalid valuation_log: {value}")
    extra = set(features) - set(fields) - {"valuation_log"}
    if extra:
        raise SystemExit(f"unexpected feature fields: {sorted(extra)}")

for item in annotations[:3]:
    match = re.search(r"artwork-(\d+)", item.get("image_path", ""))
    if not match:
        raise SystemExit(f"cannot parse artwork id from {item.get('image_path')}")
    artwork_id = int(match.group(1))
    artwork_index = by_artwork[artwork_id]
    features = item["features"]
    for metric_index, field in enumerate(fields, start=1):
        scores = []
        for expert_index in (1, 2):
            score = round(5.0 + (((artwork_index * 7) + (metric_index * 3) + expert_index) % 50) / 10, 1)
            scores.append(score)
        expected = sum(scores) / len(scores)
        actual = features[field]
        if abs(actual - expected) > 1e-6:
            raise SystemExit(f"{field} mismatch for artwork {artwork_id}: {actual} != {expected}")
    amounts = [100000 + artwork_index * 10000 + expert_index * 5000 for expert_index in (1, 2)]
    expected_log = sum(math.log(amount) for amount in amounts) / len(amounts)
    if abs(features["valuation_log"] - expected_log) > 1e-6:
        raise SystemExit(f"valuation_log mismatch for artwork {artwork_id}: {features['valuation_log']} != {expected_log}")

print(json.dumps({"annotations": len(annotations), "images": len(image_infos), "manifestSamples": manifest.get("sampleCount")}, ensure_ascii=False))
PY
)"
  verify_code=$?
  if [ "$verify_code" -eq 0 ]; then
    ok=true
  else
    ok=false
  fi
  record_check "训练 zip 与 annotations.json 校验" "$ok" "$verify_output"
  [ "$ok" = "true" ]
}

cleanup_resources() {
  if [ "$KEEP_RESOURCES" = "true" ]; then
    append_cleanup_note "ARTFETCH_KEEP_RESOURCES=true，已跳过清理。"
    return 0
  fi
  if [ -n "$DATASET_ID" ]; then
    http_request GET "/auto-evaluation/datasets/$DATASET_ID" "$ADMIN_TOKEN"
    if [ "$LAST_RESPONSE_CODE" -eq 200 ]; then
      local status
      status="$(json_value '.status')"
      if [ "$status" = "DRAFT" ] || [ "$status" = "FAILED" ]; then
        http_request DELETE "/auto-evaluation/datasets/$DATASET_ID" "$ADMIN_TOKEN"
        [ "$LAST_RESPONSE_CODE" -eq 204 ] && append_cleanup_note "已删除未完成训练数据集：$DATASET_ID"
      elif [ "$status" = "READY" ]; then
        append_cleanup_note "READY 训练数据集已保留，便于手动下载：$DATASET_ID"
      fi
    fi
  fi
  if [ -n "$EXPERT1_ID" ]; then
    http_request PUT "/users/$EXPERT1_ID/status" "$ADMIN_TOKEN" '{"status":"DISABLED"}'
    [ "$LAST_RESPONSE_CODE" -eq 200 ] && append_cleanup_note "已禁用临时专家甲：$EXPERT1_ID"
  fi
  if [ -n "$EXPERT2_ID" ]; then
    http_request PUT "/users/$EXPERT2_ID/status" "$ADMIN_TOKEN" '{"status":"DISABLED"}'
    [ "$LAST_RESPONSE_CODE" -eq 200 ] && append_cleanup_note "已禁用临时专家乙：$EXPERT2_ID"
  fi
  if [ -n "$AUDITOR_ID" ]; then
    http_request PUT "/users/$AUDITOR_ID/status" "$ADMIN_TOKEN" '{"status":"DISABLED"}'
    [ "$LAST_RESPONSE_CODE" -eq 200 ] && append_cleanup_note "已禁用临时审核人：$AUDITOR_ID"
  fi
}

generate_reports() {
  local checks_json failures_json cleanup_json warnings_json
  checks_json="$(
    for ((i = 0; i < ${#CHECK_NAMES[@]}; i++)); do
      "$JQ_BIN" -nc --arg name "${CHECK_NAMES[$i]}" --argjson passed "${CHECK_RESULTS[$i]}" --arg detail "${CHECK_DETAILS[$i]}" '{name:$name,passed:$passed,detail:$detail}'
    done | "$JQ_BIN" -s .
  )"
  failures_json="$(printf '%s\n' "${FAILURES[@]:-}" | sed '/^$/d' | "$JQ_BIN" -R . | "$JQ_BIN" -s .)"
  cleanup_json="$(printf '%s\n' "${CLEANUP_NOTES[@]:-}" | sed '/^$/d' | "$JQ_BIN" -R . | "$JQ_BIN" -s .)"
  warnings_json="$(printf '%s\n' "${WARNINGS[@]:-}" | sed '/^$/d' | "$JQ_BIN" -R . | "$JQ_BIN" -s .)"

  "$JQ_BIN" -nc \
    --arg runId "$RUN_ID" \
    --arg runAt "$RUN_AT" \
    --arg baseUrl "$BASE_URL" \
    --arg templateId "$TEMPLATE_ID" \
    --arg projectId "$PROJECT_ID" \
    --arg datasetId "$DATASET_ID" \
    --arg zipPath "$ZIP_PATH" \
    --arg unzipDir "$UNZIP_DIR" \
    --argjson artworkIds "$ARTWORK_IDS_JSON" \
    --argjson artworkTitles "$ARTWORK_TITLES_JSON" \
    --argjson checks "$checks_json" \
    --argjson failures "$failures_json" \
    --argjson warnings "$warnings_json" \
    --argjson cleanup "$cleanup_json" \
    '{
      runId:$runId,
      runAt:$runAt,
      baseUrl:$baseUrl,
      templateId:($templateId | tonumber?),
      projectId:($projectId | tonumber?),
      datasetId:($datasetId | tonumber?),
      zipPath:$zipPath,
      unzipDir:$unzipDir,
      artworks: [$artworkIds, $artworkTitles] | transpose | map({id:.[0], title:.[1]}),
      checks:$checks,
      failures:$failures,
      warnings:$warnings,
      cleanupNotes:$cleanup
    }' > "$REPORT_JSON"

  local overall="通过"
  if [ "${#FAILURES[@]}" -gt 0 ]; then
    overall="失败"
  fi
  {
    echo "# 书画模型训练标注模板V2场景报告"
    echo
    echo "- 执行时间：$RUN_AT"
    echo "- 执行结果：$overall"
    echo "- 后端地址：$BASE_URL"
    echo "- 模板 ID：$TEMPLATE_ID"
    echo "- 项目 ID：$PROJECT_ID"
    echo "- 数据集 ID：$DATASET_ID"
    echo "- 训练包：$ZIP_PATH"
    echo "- 解压目录：$UNZIP_DIR"
    echo
    echo "## 检查项"
    echo
    echo "| 检查项 | 结果 | 说明 |"
    echo "| --- | --- | --- |"
    for ((i = 0; i < ${#CHECK_NAMES[@]}; i++)); do
      local result="失败"
      [ "${CHECK_RESULTS[$i]}" = "true" ] && result="通过"
      printf '| %s | %s | %s |\n' "${CHECK_NAMES[$i]}" "$result" "${CHECK_DETAILS[$i]}"
    done
    if [ "${#FAILURES[@]}" -gt 0 ]; then
      echo
      echo "## 失败原因"
      echo
      for failure in "${FAILURES[@]}"; do
        printf -- '- %s\n' "$failure"
      done
    fi
    echo
    echo "## 清理说明"
    echo
    if [ "${#CLEANUP_NOTES[@]}" -eq 0 ]; then
      echo "- 无清理记录。"
    else
      for note in "${CLEANUP_NOTES[@]}"; do
        printf -- '- %s\n' "$note"
      done
    fi
  } > "$REPORT_MD"
}

run_scenario() {
  login_user "$ADMIN_USERNAME" "$ADMIN_PASSWORD" "管理员登录" || return 1
  ADMIN_TOKEN="$(json_value '.tokenValue // empty')"
  [ -z "$ADMIN_TOKEN" ] && append_failure "管理员登录：未拿到 token" && return 1

  assert_builtin_metrics_v2 || return 1
  assert_builtin_template_v2 || return 1
  create_users || return 1
  select_artworks || return 1
  record_check "选择 ${SAMPLE_SIZE} 件作品" "$([ "$("$JQ_BIN" 'length' <<< "$ARTWORK_IDS_JSON")" -eq "$SAMPLE_SIZE" ] && echo true || echo false)" "artworkIds=$(printf '%s' "$ARTWORK_IDS_JSON" | "$JQ_BIN" -c '.')"
  create_project || return 1
  approve_project || return 1
  create_dataset_and_download_zip || return 1
  assert_zip_and_annotations || return 1
}

main() {
  if [ "${1:-}" = "--help" ]; then
    usage
    exit 0
  fi

  require_command "$CURL_BIN" "curl"
  require_command "$JQ_BIN" "jq"
  require_command "$UNZIP_BIN" "unzip"
  require_command "$PYTHON_BIN" "python3"

  mkdir -p "$OUTPUT_DIR"

  run_scenario || true
  cleanup_resources
  generate_reports

  echo "书画 V2 训练标注场景执行完成。"
  echo "Markdown 报告：$REPORT_MD"
  echo "JSON 报告：$REPORT_JSON"
  [ -n "$ZIP_PATH" ] && echo "训练包：$ZIP_PATH"

  if [ "${#FAILURES[@]}" -gt 0 ]; then
    exit 1
  fi
  exit 0
}

main "$@"
