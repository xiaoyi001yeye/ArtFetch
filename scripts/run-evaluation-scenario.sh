#!/usr/bin/env bash

set -u
set -o pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

CURL_BIN="${CURL_BIN:-$(command -v curl || true)}"
JQ_BIN="${JQ_BIN:-$(command -v jq || true)}"

BASE_URL="${ARTFETCH_BASE_URL:-http://localhost:8080/api}"
ADMIN_USERNAME="${ARTFETCH_ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ARTFETCH_ADMIN_PASSWORD:-12345678}"
SCENARIO_ARTIST="${ARTFETCH_SCENARIO_ARTIST:-周春芽}"
REPORT_OUTPUT_DIR="${ARTFETCH_REPORT_OUTPUT_DIR:-$REPO_ROOT/docs/generated}"

RUN_ID="$(date '+%Y%m%d-%H%M%S')"
RUN_AT="$(date '+%Y-%m-%d %H:%M:%S %Z')"

LAST_RESPONSE_CODE=""
LAST_RESPONSE_BODY=""

ADMIN_TOKEN=""
EXPERT1_TOKEN=""
EXPERT2_TOKEN=""
AUDITOR_TOKEN=""

EXPERT1_ID=""
EXPERT2_ID=""
AUDITOR_ID=""
METRIC1_ID=""
METRIC2_ID=""
METRIC3_ID=""
TEMPLATE_ID=""
PROJECT_ID=""

PROJECT_STATUS_INITIAL=""
PROJECT_STATUS_AFTER_DRAFT=""
PROJECT_STATUS_READY=""
PROJECT_STATUS_IN_REVIEW_1=""
PROJECT_STATUS_REJECTED=""
PROJECT_STATUS_IN_REVIEW_2=""
PROJECT_STATUS_COMPLETED=""
PROJECT_STATUS_AFTER_DELETE_ATTEMPT=""

ARTWORK1_ID=""
ARTWORK2_ID=""
ARTWORK1_TITLE=""
ARTWORK2_TITLE=""

ASSIGNED1_COUNT=0
ASSIGNED2_COUNT=0

DRAFT_STATUS=""
ADMIN_DRAFT_STATUSES=""
AUDITOR_DRAFT_REVIEW_COUNT=0
LOCKED_UPDATE_CODE=0
LOCKED_UPDATE_ERROR=""
REJECT_REASON=""
RESUBMIT_STATUS=""
AUDIT_RESULT=""
AUDIT_RECORD_ACTIONS=""
AUDIT_LOG_ACTIONS=""
COMPLETED_DELETE_CODE=0
COMPLETED_DELETE_ERROR=""

CHECK_NAMES=()
CHECK_RESULTS=()
CHECK_DETAILS=()
FAILURES=()
WARNINGS=()
CLEANUP_NOTES=()

SCENARIO_ABORTED=false
SCENARIO_ERROR_STEP=""
SCENARIO_ERROR_MESSAGE=""

JSON_REPORT_PATH=""
MARKDOWN_REPORT_PATH=""

usage() {
  cat <<EOF
用法：
  ./scripts/run-evaluation-scenario.sh

可选环境变量：
  ARTFETCH_BASE_URL            后端 API 地址，默认 http://localhost:8080/api
  ARTFETCH_ADMIN_USERNAME      管理员用户名，默认 admin
  ARTFETCH_ADMIN_PASSWORD      管理员密码，默认 12345678
  ARTFETCH_SCENARIO_ARTIST     场景选取艺术品时使用的作者关键词，默认 周春芽
  ARTFETCH_REPORT_OUTPUT_DIR   报告输出目录，默认 docs/generated

输出：
  1. 一份 JSON 证据文件
  2. 一份 Markdown 评估报告

说明：
  - 脚本会自动创建临时专家、临时审核人、临时指标和模板
  - 已完成项目不会自动删除，因为系统规则已禁止删除完成态项目
  - 临时用户会在脚本末尾自动禁用，临时指标和模板会自动删除
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

append_failure() {
  FAILURES+=("$1")
}

append_warning() {
  WARNINGS+=("$1")
}

append_cleanup_note() {
  CLEANUP_NOTES+=("$1")
}

record_check() {
  CHECK_NAMES+=("$1")
  CHECK_RESULTS+=("$2")
  CHECK_DETAILS+=("$3")
}

bool_result() {
  if [ "$1" = "true" ]; then
    printf '通过'
  else
    printf '失败'
  fi
}

csv_contains() {
  local csv="$1"
  local needle="$2"
  case ",$csv," in
    *",$needle,"*) return 0 ;;
    *) return 1 ;;
  esac
}

abort_scenario() {
  SCENARIO_ABORTED=true
  SCENARIO_ERROR_STEP="$1"
  SCENARIO_ERROR_MESSAGE="$2"
  append_failure "$1：$2"
  return 1
}

ensure_success_code() {
  local expected="$1"
  local step="$2"
  if [ "$LAST_RESPONSE_CODE" -ne "$expected" ]; then
    abort_scenario "$step" "HTTP ${LAST_RESPONSE_CODE}，响应：${LAST_RESPONSE_BODY}"
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

refresh_project_status() {
  if [ -z "$PROJECT_ID" ] || [ -z "$ADMIN_TOKEN" ]; then
    return 0
  fi
  http_request GET "/evaluations/$PROJECT_ID" "$ADMIN_TOKEN"
  if [ "$LAST_RESPONSE_CODE" -eq 200 ]; then
    PROJECT_STATUS_AFTER_DELETE_ATTEMPT="$(json_value '.status // empty')"
  fi
}

cleanup_resources() {
  if [ -n "$PROJECT_ID" ] && [ "$PROJECT_STATUS_COMPLETED" != "COMPLETED" ]; then
    http_request DELETE "/evaluations/$PROJECT_ID" "$ADMIN_TOKEN"
    if [ "$LAST_RESPONSE_CODE" -eq 200 ]; then
      append_cleanup_note "已删除未完成的临时项目：$PROJECT_ID"
    elif [ "$LAST_RESPONSE_CODE" -eq 404 ]; then
      append_cleanup_note "临时项目已不存在：$PROJECT_ID"
    else
      append_cleanup_note "临时项目未自动删除：$PROJECT_ID（HTTP $LAST_RESPONSE_CODE）"
    fi
  elif [ -n "$PROJECT_ID" ] && [ "$PROJECT_STATUS_COMPLETED" = "COMPLETED" ]; then
    append_cleanup_note "完成态项目已保留用于审计留痕：$PROJECT_ID"
  fi

  if [ -n "$TEMPLATE_ID" ] && [ -n "$ADMIN_TOKEN" ]; then
    http_request DELETE "/evaluation-metric-templates/$TEMPLATE_ID" "$ADMIN_TOKEN"
    if [ "$LAST_RESPONSE_CODE" -eq 200 ]; then
      append_cleanup_note "已删除临时指标模板：$TEMPLATE_ID"
    else
      append_cleanup_note "临时指标模板删除失败：$TEMPLATE_ID（HTTP $LAST_RESPONSE_CODE）"
    fi
  fi

  if [ -n "$METRIC1_ID" ] && [ -n "$ADMIN_TOKEN" ]; then
    http_request DELETE "/evaluation-metrics/$METRIC1_ID" "$ADMIN_TOKEN"
    [ "$LAST_RESPONSE_CODE" -eq 200 ] && append_cleanup_note "已删除临时指标：$METRIC1_ID"
  fi
  if [ -n "$METRIC2_ID" ] && [ -n "$ADMIN_TOKEN" ]; then
    http_request DELETE "/evaluation-metrics/$METRIC2_ID" "$ADMIN_TOKEN"
    [ "$LAST_RESPONSE_CODE" -eq 200 ] && append_cleanup_note "已删除临时指标：$METRIC2_ID"
  fi
  if [ -n "$METRIC3_ID" ] && [ -n "$ADMIN_TOKEN" ]; then
    http_request DELETE "/evaluation-metrics/$METRIC3_ID" "$ADMIN_TOKEN"
    [ "$LAST_RESPONSE_CODE" -eq 200 ] && append_cleanup_note "已删除临时指标：$METRIC3_ID"
  fi

  if [ -n "$EXPERT1_ID" ] && [ -n "$ADMIN_TOKEN" ]; then
    http_request PUT "/users/$EXPERT1_ID/status" "$ADMIN_TOKEN" '{"status":"DISABLED"}'
    [ "$LAST_RESPONSE_CODE" -eq 200 ] && append_cleanup_note "已禁用临时专家甲：$EXPERT1_ID"
  fi
  if [ -n "$EXPERT2_ID" ] && [ -n "$ADMIN_TOKEN" ]; then
    http_request PUT "/users/$EXPERT2_ID/status" "$ADMIN_TOKEN" '{"status":"DISABLED"}'
    [ "$LAST_RESPONSE_CODE" -eq 200 ] && append_cleanup_note "已禁用临时专家乙：$EXPERT2_ID"
  fi
  if [ -n "$AUDITOR_ID" ] && [ -n "$ADMIN_TOKEN" ]; then
    http_request PUT "/users/$AUDITOR_ID/status" "$ADMIN_TOKEN" '{"status":"DISABLED"}'
    [ "$LAST_RESPONSE_CODE" -eq 200 ] && append_cleanup_note "已禁用临时审核人：$AUDITOR_ID"
  fi
}

evaluate_checks() {
  local assigned_visible=false
  local draft_hidden=false
  local config_locked=false
  local ready_for_review=false
  local reject_and_resubmit=false
  local approve_flow=false
  local audit_records_complete=false
  local audit_logs_complete=false
  local completed_delete_blocked=false

  if [ "$ASSIGNED1_COUNT" -ge 1 ] && [ "$ASSIGNED2_COUNT" -ge 1 ]; then
    assigned_visible=true
  fi
  record_check "专家可见分配项目" "$assigned_visible" "专家甲=${ASSIGNED1_COUNT}，专家乙=${ASSIGNED2_COUNT}"
  [ "$assigned_visible" = "false" ] && append_failure "至少有一位专家看不到分配给自己的评估项目"

  if [ "$AUDITOR_DRAFT_REVIEW_COUNT" -eq 0 ]; then
    draft_hidden=true
  fi
  record_check "审核人不可见草稿" "$draft_hidden" "审核人草稿可见条数=$AUDITOR_DRAFT_REVIEW_COUNT"
  [ "$draft_hidden" = "false" ] && append_failure "审核人在项目送审前看到了专家草稿"

  if [ "$LOCKED_UPDATE_CODE" -eq 400 ]; then
    config_locked=true
  fi
  record_check "进行中项目配置锁定" "$config_locked" "HTTP=${LOCKED_UPDATE_CODE}，错误=${LOCKED_UPDATE_ERROR}"
  [ "$config_locked" = "false" ] && append_failure "项目进入进行中后仍可修改艺术品、专家或指标"

  if [ "$PROJECT_STATUS_READY" = "READY_FOR_REVIEW" ]; then
    ready_for_review=true
  fi
  record_check "全部专家提交后进入待审核" "$ready_for_review" "项目状态=$PROJECT_STATUS_READY"
  [ "$ready_for_review" = "false" ] && append_failure "全部专家提交后项目没有进入 READY_FOR_REVIEW"

  if [ "$PROJECT_STATUS_REJECTED" = "REVIEW_REJECTED" ] && [ -n "$REJECT_REASON" ] && [ "$RESUBMIT_STATUS" = "RESUBMITTED" ]; then
    reject_and_resubmit=true
  fi
  record_check "驳回后可重提" "$reject_and_resubmit" "驳回状态=${PROJECT_STATUS_REJECTED}，重提状态=${RESUBMIT_STATUS}"
  [ "$reject_and_resubmit" = "false" ] && append_failure "驳回重提链路未按预期完成"

  if [ "$PROJECT_STATUS_COMPLETED" = "COMPLETED" ] && [ "$AUDIT_RESULT" = "APPROVED" ]; then
    approve_flow=true
  fi
  record_check "审核通过后项目完成" "$approve_flow" "项目状态=${PROJECT_STATUS_COMPLETED}，审核结果=${AUDIT_RESULT}"
  [ "$approve_flow" = "false" ] && append_failure "审核通过后项目没有保持在 COMPLETED"

  if csv_contains "$AUDIT_RECORD_ACTIONS" "REJECT_REVIEW" && csv_contains "$AUDIT_RECORD_ACTIONS" "APPROVE_PROJECT"; then
    audit_records_complete=true
  fi
  record_check "业务审核记录完整" "$audit_records_complete" "审核记录动作=$AUDIT_RECORD_ACTIONS"
  [ "$audit_records_complete" = "false" ] && append_failure "业务审核记录缺少 REJECT_REVIEW 或 APPROVE_PROJECT"

  if csv_contains "$AUDIT_LOG_ACTIONS" "evaluation.audit.reject-review" && csv_contains "$AUDIT_LOG_ACTIONS" "evaluation.audit.approve"; then
    audit_logs_complete=true
  fi
  record_check "系统审计日志完整" "$audit_logs_complete" "系统审计动作=$AUDIT_LOG_ACTIONS"
  [ "$audit_logs_complete" = "false" ] && append_failure "系统审计日志缺少审核通过或驳回动作"

  if [ "$COMPLETED_DELETE_CODE" -eq 400 ] && [ "$PROJECT_STATUS_AFTER_DELETE_ATTEMPT" = "COMPLETED" ]; then
    completed_delete_blocked=true
  fi
  record_check "已完成项目不可删除" "$completed_delete_blocked" "HTTP=${COMPLETED_DELETE_CODE}，项目状态=${PROJECT_STATUS_AFTER_DELETE_ATTEMPT}"
  [ "$completed_delete_blocked" = "false" ] && append_failure "完成态项目仍可删除或删除后状态被污染"

  if csv_contains "$ADMIN_DRAFT_STATUSES" "NOT_STARTED"; then
    append_warning "管理员查看草稿汇总时仍包含 NOT_STARTED 占位评估，结果页会混入未开始记录。"
  fi
}

generate_json_report() {
  mkdir -p "$REPORT_OUTPUT_DIR"
  JSON_REPORT_PATH="$REPORT_OUTPUT_DIR/evaluation-scenario-report-$RUN_ID.json"

  local failure_lines warning_lines cleanup_lines
  failure_lines="$(printf '%s\n' "${FAILURES[@]:-}" | sed '/^$/d' | "$JQ_BIN" -R . | "$JQ_BIN" -s .)"
  warning_lines="$(printf '%s\n' "${WARNINGS[@]:-}" | sed '/^$/d' | "$JQ_BIN" -R . | "$JQ_BIN" -s .)"
  cleanup_lines="$(printf '%s\n' "${CLEANUP_NOTES[@]:-}" | sed '/^$/d' | "$JQ_BIN" -R . | "$JQ_BIN" -s .)"

  "$JQ_BIN" -nc \
    --arg runId "$RUN_ID" \
    --arg runAt "$RUN_AT" \
    --arg baseUrl "$BASE_URL" \
    --arg artist "$SCENARIO_ARTIST" \
    --arg projectId "$PROJECT_ID" \
    --arg artwork1Id "$ARTWORK1_ID" \
    --arg artwork2Id "$ARTWORK2_ID" \
    --arg artwork1Title "$ARTWORK1_TITLE" \
    --arg artwork2Title "$ARTWORK2_TITLE" \
    --arg projectInitial "$PROJECT_STATUS_INITIAL" \
    --arg projectAfterDraft "$PROJECT_STATUS_AFTER_DRAFT" \
    --arg projectReady "$PROJECT_STATUS_READY" \
    --arg projectInReview1 "$PROJECT_STATUS_IN_REVIEW_1" \
    --arg projectRejected "$PROJECT_STATUS_REJECTED" \
    --arg projectInReview2 "$PROJECT_STATUS_IN_REVIEW_2" \
    --arg projectCompleted "$PROJECT_STATUS_COMPLETED" \
    --arg projectAfterDelete "$PROJECT_STATUS_AFTER_DELETE_ATTEMPT" \
    --arg draftStatus "$DRAFT_STATUS" \
    --arg adminDraftStatuses "$ADMIN_DRAFT_STATUSES" \
    --arg rejectReason "$REJECT_REASON" \
    --arg resubmitStatus "$RESUBMIT_STATUS" \
    --arg auditResult "$AUDIT_RESULT" \
    --arg auditRecordActions "$AUDIT_RECORD_ACTIONS" \
    --arg auditLogActions "$AUDIT_LOG_ACTIONS" \
    --arg lockedUpdateError "$LOCKED_UPDATE_ERROR" \
    --arg completedDeleteError "$COMPLETED_DELETE_ERROR" \
    --arg scenarioAborted "$SCENARIO_ABORTED" \
    --arg errorStep "$SCENARIO_ERROR_STEP" \
    --arg errorMessage "$SCENARIO_ERROR_MESSAGE" \
    --argjson assigned1Count "$ASSIGNED1_COUNT" \
    --argjson assigned2Count "$ASSIGNED2_COUNT" \
    --argjson auditorDraftReviewCount "$AUDITOR_DRAFT_REVIEW_COUNT" \
    --argjson lockedUpdateCode "$LOCKED_UPDATE_CODE" \
    --argjson completedDeleteCode "$COMPLETED_DELETE_CODE" \
    --argjson failures "$failure_lines" \
    --argjson warnings "$warning_lines" \
    --argjson cleanupNotes "$cleanup_lines" \
    '{
      runId:$runId,
      runAt:$runAt,
      baseUrl:$baseUrl,
      scenario:{artist:$artist},
      project:{
        id:($projectId | tonumber?),
        initialStatus:$projectInitial,
        afterDraftStatus:$projectAfterDraft,
        readyStatus:$projectReady,
        inReviewStatus1:$projectInReview1,
        rejectedStatus:$projectRejected,
        inReviewStatus2:$projectInReview2,
        completedStatus:$projectCompleted,
        afterDeleteAttemptStatus:$projectAfterDelete
      },
      artworks:[
        {id:($artwork1Id | tonumber?), title:$artwork1Title},
        {id:($artwork2Id | tonumber?), title:$artwork2Title}
      ],
      evidence:{
        assigned1Count:$assigned1Count,
        assigned2Count:$assigned2Count,
        draftStatus:$draftStatus,
        adminDraftStatuses:$adminDraftStatuses,
        auditorDraftReviewCount:$auditorDraftReviewCount,
        lockedUpdateCode:$lockedUpdateCode,
        lockedUpdateError:$lockedUpdateError,
        rejectReason:$rejectReason,
        resubmitStatus:$resubmitStatus,
        auditResult:$auditResult,
        auditRecordActions:$auditRecordActions,
        auditLogActions:$auditLogActions,
        completedDeleteCode:$completedDeleteCode,
        completedDeleteError:$completedDeleteError
      },
      scenarioAborted:($scenarioAborted == "true"),
      scenarioError:{step:$errorStep, message:$errorMessage},
      failures:$failures,
      warnings:$warnings,
      cleanupNotes:$cleanupNotes
    }' > "$JSON_REPORT_PATH"
}

generate_markdown_report() {
  MARKDOWN_REPORT_PATH="$REPORT_OUTPUT_DIR/evaluation-scenario-report-$RUN_ID.md"

  local overall_status
  overall_status="通过"
  if [ "${#FAILURES[@]}" -gt 0 ]; then
    overall_status="失败"
  elif [ "${#WARNINGS[@]}" -gt 0 ]; then
    overall_status="通过（含告警）"
  fi

  {
    cat <<EOF
# 模拟评估流程执行报告

执行时间：$RUN_AT  
执行结果：$overall_status  
后端地址：$BASE_URL  
证据文件：[evaluation-scenario-report-$RUN_ID.json](./evaluation-scenario-report-$RUN_ID.json)

## 场景基础情况

- 作者筛选关键词：\`$SCENARIO_ARTIST\`
- 样本艺术品 1：\`$ARTWORK1_ID\` / $ARTWORK1_TITLE
- 样本艺术品 2：\`$ARTWORK2_ID\` / $ARTWORK2_TITLE
- 项目 ID：\`$PROJECT_ID\`
- 初始状态：\`$PROJECT_STATUS_INITIAL\`

## 执行摘要

| 检查项 | 结果 | 说明 |
| --- | --- | --- |
EOF

    local i
    for ((i = 0; i < ${#CHECK_NAMES[@]}; i++)); do
      printf '| %s | %s | %s |\n' "${CHECK_NAMES[$i]}" "$(bool_result "${CHECK_RESULTS[$i]}")" "${CHECK_DETAILS[$i]}"
    done

    cat <<EOF

## 关键状态流转

- 草稿保存后项目状态：\`$PROJECT_STATUS_AFTER_DRAFT\`
- 全部专家提交后项目状态：\`$PROJECT_STATUS_READY\`
- 首次送审后项目状态：\`$PROJECT_STATUS_IN_REVIEW_1\`
- 驳回后项目状态：\`$PROJECT_STATUS_REJECTED\`
- 重提后再次送审状态：\`$PROJECT_STATUS_IN_REVIEW_2\`
- 审核通过后项目状态：\`$PROJECT_STATUS_COMPLETED\`
- 完成态删除尝试后项目状态：\`$PROJECT_STATUS_AFTER_DELETE_ATTEMPT\`

## 审计留痕

- 业务审核记录动作：\`$AUDIT_RECORD_ACTIONS\`
- 系统审计日志动作：\`$AUDIT_LOG_ACTIONS\`
- 驳回原因：$REJECT_REASON

EOF

    if [ "${#FAILURES[@]}" -gt 0 ]; then
      echo "## 发现的问题"
      echo
      local failure
      for failure in "${FAILURES[@]}"; do
        printf -- '- %s\n' "$failure"
      done
      echo
    fi

    if [ "${#WARNINGS[@]}" -gt 0 ]; then
      echo "## 告警"
      echo
      local warning
      for warning in "${WARNINGS[@]}"; do
        printf -- '- %s\n' "$warning"
      done
      echo
    fi

    echo "## 清理说明"
    echo
    if [ "${#CLEANUP_NOTES[@]}" -eq 0 ]; then
      echo "- 本次没有产生可清理的临时资源。"
    else
      local note
      for note in "${CLEANUP_NOTES[@]}"; do
        printf -- '- %s\n' "$note"
      done
    fi
    echo

    if [ "$SCENARIO_ABORTED" = "true" ]; then
      echo "## 中断信息"
      echo
      echo "- 中断步骤：$SCENARIO_ERROR_STEP"
      echo "- 中断原因：$SCENARIO_ERROR_MESSAGE"
      echo
    fi
  } > "$MARKDOWN_REPORT_PATH"
}

run_scenario() {
  local payload response

  login_user "$ADMIN_USERNAME" "$ADMIN_PASSWORD" "管理员登录" || return 1
  ADMIN_TOKEN="$(json_value '.tokenValue // empty')"
  [ -z "$ADMIN_TOKEN" ] && abort_scenario "管理员登录" "未拿到 token" && return 1

  payload="$("$JQ_BIN" -nc --arg username "codex_scene_exp1_$RUN_ID" --arg password "Expert12345" --arg displayName "场景专家甲" '{username:$username,password:$password,displayName:$displayName,roles:["EXPERT"]}')"
  http_request POST "/users" "$ADMIN_TOKEN" "$payload"
  ensure_success_code 200 "创建临时专家甲" || return 1
  EXPERT1_ID="$(json_value '.id // empty')"
  local expert1_username
  expert1_username="$(json_value '.username // empty')"

  payload="$("$JQ_BIN" -nc --arg username "codex_scene_exp2_$RUN_ID" --arg password "Expert22345" --arg displayName "场景专家乙" '{username:$username,password:$password,displayName:$displayName,roles:["EXPERT"]}')"
  http_request POST "/users" "$ADMIN_TOKEN" "$payload"
  ensure_success_code 200 "创建临时专家乙" || return 1
  EXPERT2_ID="$(json_value '.id // empty')"
  local expert2_username
  expert2_username="$(json_value '.username // empty')"

  payload="$("$JQ_BIN" -nc --arg username "codex_scene_aud_$RUN_ID" --arg password "Auditor12345" --arg displayName "场景审核人" '{username:$username,password:$password,displayName:$displayName,roles:["AUDITOR"]}')"
  http_request POST "/users" "$ADMIN_TOKEN" "$payload"
  ensure_success_code 200 "创建临时审核人" || return 1
  AUDITOR_ID="$(json_value '.id // empty')"
  local auditor_username
  auditor_username="$(json_value '.username // empty')"

  payload="$("$JQ_BIN" -nc --arg artist "$SCENARIO_ARTIST" '{criteria:[{fieldName:"artist",fieldLabel:"作者",operator:"contains",value:$artist,valueType:"text"}],page:0,size:5}')"
  http_request POST "/evaluations/preview-artworks" "$ADMIN_TOKEN" "$payload"
  ensure_success_code 200 "预览候选艺术品" || return 1
  if [ "$(json_value '.items | length')" -lt 2 ]; then
    abort_scenario "预览候选艺术品" "可用于模拟评估的艺术品不足 2 件"
    return 1
  fi
  ARTWORK1_ID="$(json_value '.items[0].id // empty')"
  ARTWORK2_ID="$(json_value '.items[1].id // empty')"
  ARTWORK1_TITLE="$(json_value '.items[0].title // empty')"
  ARTWORK2_TITLE="$(json_value '.items[1].title // empty')"

  payload='{"code":"SCENE_MARKET_'$RUN_ID'","name":"市场热度","description":"关注同类作品市场活跃度","category":"市场","scoreType":"NUMBER","minScore":1,"maxScore":10,"scoreStep":1,"defaultWeight":0.4,"required":true,"inputComponent":"input-number","scoringGuide":"1-10分，分值越高表示市场热度越高","sortOrder":1}'
  http_request POST "/evaluation-metrics" "$ADMIN_TOKEN" "$payload"
  ensure_success_code 200 "创建临时指标 1" || return 1
  METRIC1_ID="$(json_value '.id // empty')"
  local metric1_version
  metric1_version="$(json_value '.version // 1')"

  payload='{"code":"SCENE_RISK_'$RUN_ID'","name":"真伪及流通风险","description":"评估真伪、来源与流通风险","category":"风险","scoreType":"OPTION","defaultWeight":0.3,"required":true,"inputComponent":"select","optionValues":"LOW|低风险\nMEDIUM|中风险\nHIGH|高风险","scoringGuide":"选择整体风险等级","sortOrder":2}'
  http_request POST "/evaluation-metrics" "$ADMIN_TOKEN" "$payload"
  ensure_success_code 200 "创建临时指标 2" || return 1
  METRIC2_ID="$(json_value '.id // empty')"
  local metric2_version
  metric2_version="$(json_value '.version // 1')"

  payload='{"code":"SCENE_COMMENT_'$RUN_ID'","name":"专家核心意见","description":"专家形成最终结论的核心判断","category":"结论","scoreType":"TEXT","defaultWeight":0.3,"required":true,"inputComponent":"textarea","scoringGuide":"至少给出一句完整判断","sortOrder":3}'
  http_request POST "/evaluation-metrics" "$ADMIN_TOKEN" "$payload"
  ensure_success_code 200 "创建临时指标 3" || return 1
  METRIC3_ID="$(json_value '.id // empty')"
  local metric3_version
  metric3_version="$(json_value '.version // 1')"

  payload="$("$JQ_BIN" -nc \
    --arg name "春拍油画估值模板-$RUN_ID" \
    --arg desc "用于自动化评估场景回归" \
    --argjson metric1Id "$METRIC1_ID" \
    --argjson metric1Version "$metric1_version" \
    --argjson metric2Id "$METRIC2_ID" \
    --argjson metric2Version "$metric2_version" \
    --argjson metric3Id "$METRIC3_ID" \
    --argjson metric3Version "$metric3_version" \
    '{
      name:$name,
      description:$desc,
      enabled:true,
      items:[
        {sourceMetricDefinitionId:$metric1Id,sourceVersion:$metric1Version,code:"MARKET",name:"市场热度",category:"市场",scoreType:"NUMBER",minScore:1,maxScore:10,scoreStep:1,weight:0.4,required:true,inputComponent:"input-number",scoringGuide:"1-10分",sortOrder:1},
        {sourceMetricDefinitionId:$metric2Id,sourceVersion:$metric2Version,code:"RISK",name:"真伪及流通风险",category:"风险",scoreType:"OPTION",weight:0.3,required:true,inputComponent:"select",optionValues:"LOW|低风险\nMEDIUM|中风险\nHIGH|高风险",scoringGuide:"选择风险等级",sortOrder:2},
        {sourceMetricDefinitionId:$metric3Id,sourceVersion:$metric3Version,code:"COMMENT",name:"专家核心意见",category:"结论",scoreType:"TEXT",weight:0.3,required:true,inputComponent:"textarea",scoringGuide:"写核心结论",sortOrder:3}
      ]
    }')"
  http_request POST "/evaluation-metric-templates" "$ADMIN_TOKEN" "$payload"
  ensure_success_code 200 "创建临时指标模板" || return 1
  TEMPLATE_ID="$(json_value '.id // empty')"

  payload="$("$JQ_BIN" -nc \
    --arg name "自动化模拟评估-$RUN_ID" \
    --arg desc "用于命令化回归评估主流程" \
    --arg artist "$SCENARIO_ARTIST" \
    --argjson auditorId "$AUDITOR_ID" \
    --argjson artwork1 "$ARTWORK1_ID" \
    --argjson artwork2 "$ARTWORK2_ID" \
    --argjson expert1 "$EXPERT1_ID" \
    --argjson expert2 "$EXPERT2_ID" \
    --argjson metric1Id "$METRIC1_ID" \
    --argjson metric1Version "$metric1_version" \
    --argjson metric2Id "$METRIC2_ID" \
    --argjson metric2Version "$metric2_version" \
    --argjson metric3Id "$METRIC3_ID" \
    --argjson metric3Version "$metric3_version" \
    '{
      name:$name,
      description:$desc,
      auditorId:$auditorId,
      criteria:[{fieldName:"artist",fieldLabel:"作者",operator:"contains",value:$artist,valueType:"text"}],
      artworkIds:[$artwork1,$artwork2],
      expertIds:[$expert1,$expert2],
      metrics:[
        {sourceMetricDefinitionId:$metric1Id,sourceVersion:$metric1Version,code:"MARKET",name:"市场热度",category:"市场",scoreType:"NUMBER",minScore:1,maxScore:10,scoreStep:1,weight:0.4,required:true,inputComponent:"input-number",scoringGuide:"1-10分",sortOrder:1},
        {sourceMetricDefinitionId:$metric2Id,sourceVersion:$metric2Version,code:"RISK",name:"真伪及流通风险",category:"风险",scoreType:"OPTION",weight:0.3,required:true,inputComponent:"select",optionValues:"LOW|低风险\nMEDIUM|中风险\nHIGH|高风险",scoringGuide:"选择风险等级",sortOrder:2},
        {sourceMetricDefinitionId:$metric3Id,sourceVersion:$metric3Version,code:"COMMENT",name:"专家核心意见",category:"结论",scoreType:"TEXT",weight:0.3,required:true,inputComponent:"textarea",scoringGuide:"写核心结论",sortOrder:3}
      ]
    }')"
  http_request POST "/evaluations" "$ADMIN_TOKEN" "$payload"
  ensure_success_code 200 "创建评估项目" || return 1
  PROJECT_ID="$(json_value '.id // empty')"
  PROJECT_STATUS_INITIAL="$(json_value '.status // empty')"

  http_request GET "/evaluations/$PROJECT_ID" "$ADMIN_TOKEN"
  ensure_success_code 200 "查询项目详情" || return 1
  local pm1 pm2 pm3
  pm1="$(json_value '.metrics[0].id // 0')"
  pm2="$(json_value '.metrics[1].id // 0')"
  pm3="$(json_value '.metrics[2].id // 0')"

  login_user "$expert1_username" "Expert12345" "专家甲登录" || return 1
  EXPERT1_TOKEN="$(json_value '.tokenValue // empty')"
  login_user "$expert2_username" "Expert22345" "专家乙登录" || return 1
  EXPERT2_TOKEN="$(json_value '.tokenValue // empty')"
  login_user "$auditor_username" "Auditor12345" "审核人登录" || return 1
  AUDITOR_TOKEN="$(json_value '.tokenValue // empty')"

  http_request GET "/evaluations/assigned?page=0&size=20" "$EXPERT1_TOKEN"
  ensure_success_code 200 "专家甲查看分配项目" || return 1
  ASSIGNED1_COUNT="$(json_value '.total // 0')"

  http_request GET "/evaluations/assigned?page=0&size=20" "$EXPERT2_TOKEN"
  ensure_success_code 200 "专家乙查看分配项目" || return 1
  ASSIGNED2_COUNT="$(json_value '.total // 0')"

  payload="$("$JQ_BIN" -nc --argjson m1 "$pm1" --argjson m2 "$pm2" --argjson m3 "$pm3" '{finalEstimate:null,finalEstimateCurrency:null,comment:"先记录初步判断，待补充成交比对。",scores:[{projectMetricId:$m1,score:8},{projectMetricId:$m2,optionValue:"MEDIUM"},{projectMetricId:$m3,textValue:"作品面貌成熟，初判市场接受度较高。"}]}')"
  http_request PUT "/evaluations/$PROJECT_ID/artworks/$ARTWORK1_ID/my-review" "$EXPERT1_TOKEN" "$payload"
  ensure_success_code 200 "专家甲保存草稿" || return 1
  DRAFT_STATUS="$(json_value '.status // empty')"

  http_request GET "/evaluations/$PROJECT_ID" "$ADMIN_TOKEN"
  ensure_success_code 200 "管理员刷新项目状态" || return 1
  PROJECT_STATUS_AFTER_DRAFT="$(json_value '.status // empty')"

  http_request GET "/evaluations/$PROJECT_ID/artworks/$ARTWORK1_ID/reviews" "$ADMIN_TOKEN"
  ensure_success_code 200 "管理员查看评估汇总" || return 1
  ADMIN_DRAFT_STATUSES="$(json_value '[.reviews[].status] | join(",")')"

  http_request GET "/evaluations/$PROJECT_ID/artworks/$ARTWORK1_ID/reviews" "$AUDITOR_TOKEN"
  ensure_success_code 200 "审核人查看草稿汇总" || return 1
  AUDITOR_DRAFT_REVIEW_COUNT="$(json_value '.reviews | length')"

  payload="$("$JQ_BIN" -nc --argjson auditorId "$AUDITOR_ID" --arg artist "$SCENARIO_ARTIST" --argjson artwork1 "$ARTWORK1_ID" --argjson expert1 "$EXPERT1_ID" '{name:"锁定校验项目",description:"测试项目配置锁定",auditorId:$auditorId,criteria:[{fieldName:"artist",fieldLabel:"作者",operator:"contains",value:$artist,valueType:"text"}],artworkIds:[$artwork1],expertIds:[$expert1],metrics:[{code:"TMP",name:"临时指标",required:true,sortOrder:1}]}' )"
  http_request PUT "/evaluations/$PROJECT_ID" "$ADMIN_TOKEN" "$payload"
  LOCKED_UPDATE_CODE="$LAST_RESPONSE_CODE"
  LOCKED_UPDATE_ERROR="$(printf '%s' "$LAST_RESPONSE_BODY" | "$JQ_BIN" -r '.error // .message // empty')"

  payload="$("$JQ_BIN" -nc --arg est "RMB 420000-480000" --arg curr "CNY" --arg comment "建议作为中高端当代油画标的。" --argjson m1 "$pm1" --argjson m2 "$pm2" --argjson m3 "$pm3" '{finalEstimate:$est,finalEstimateCurrency:$curr,comment:$comment,scores:[{projectMetricId:$m1,score:8},{projectMetricId:$m2,optionValue:"MEDIUM"},{projectMetricId:$m3,textValue:"作品题材和市场识别度都较好，估值区间相对稳健。"}]}')"
  http_request POST "/evaluations/$PROJECT_ID/artworks/$ARTWORK1_ID/my-review/submit" "$EXPERT1_TOKEN" "$payload"
  ensure_success_code 200 "专家甲提交作品 1" || return 1

  payload="$("$JQ_BIN" -nc --arg est "RMB 260000-320000" --arg curr "CNY" --arg comment "尺幅较小，但题材完整。" --argjson m1 "$pm1" --argjson m2 "$pm2" --argjson m3 "$pm3" '{finalEstimate:$est,finalEstimateCurrency:$curr,comment:$comment,scores:[{projectMetricId:$m1,score:7},{projectMetricId:$m2,optionValue:"LOW"},{projectMetricId:$m3,textValue:"第二件作品流通风险较低，适合稳健估值。"}]}')"
  http_request POST "/evaluations/$PROJECT_ID/artworks/$ARTWORK2_ID/my-review/submit" "$EXPERT1_TOKEN" "$payload"
  ensure_success_code 200 "专家甲提交作品 2" || return 1

  payload="$("$JQ_BIN" -nc --arg est "RMB 500000-560000" --arg curr "CNY" --arg comment "市场热度高于均值，可适当上探。" --argjson m1 "$pm1" --argjson m2 "$pm2" --argjson m3 "$pm3" '{finalEstimate:$est,finalEstimateCurrency:$curr,comment:$comment,scores:[{projectMetricId:$m1,score:9},{projectMetricId:$m2,optionValue:"LOW"},{projectMetricId:$m3,textValue:"第一件作品展现度强，可给予更积极预期。"}]}')"
  http_request POST "/evaluations/$PROJECT_ID/artworks/$ARTWORK1_ID/my-review/submit" "$EXPERT2_TOKEN" "$payload"
  ensure_success_code 200 "专家乙提交作品 1" || return 1

  payload="$("$JQ_BIN" -nc --arg est "RMB 240000-300000" --arg curr "CNY" --arg comment "估值宜保持克制。" --argjson m1 "$pm1" --argjson m2 "$pm2" --argjson m3 "$pm3" '{finalEstimate:$est,finalEstimateCurrency:$curr,comment:$comment,scores:[{projectMetricId:$m1,score:6},{projectMetricId:$m2,optionValue:"MEDIUM"},{projectMetricId:$m3,textValue:"第二件作品题材普通，建议控制上限。"}]}')"
  http_request POST "/evaluations/$PROJECT_ID/artworks/$ARTWORK2_ID/my-review/submit" "$EXPERT2_TOKEN" "$payload"
  ensure_success_code 200 "专家乙提交作品 2" || return 1

  http_request GET "/evaluations/$PROJECT_ID" "$ADMIN_TOKEN"
  ensure_success_code 200 "管理员刷新待审核状态" || return 1
  PROJECT_STATUS_READY="$(json_value '.status // empty')"

  http_request POST "/evaluations/$PROJECT_ID/submit-review" "$ADMIN_TOKEN"
  ensure_success_code 200 "管理员首次送审" || return 1
  PROJECT_STATUS_IN_REVIEW_1="$(json_value '.status // empty')"

  http_request GET "/evaluations/$PROJECT_ID/artworks/$ARTWORK1_ID/reviews" "$AUDITOR_TOKEN"
  ensure_success_code 200 "审核人查看已提交汇总" || return 1
  local review_to_reject
  review_to_reject="$(json_value '.reviews[] | select(.expertName=="场景专家乙") | .id' | head -n 1)"
  [ -z "$review_to_reject" ] && abort_scenario "审核人查看已提交汇总" "未找到专家乙的评估记录" && return 1

  http_request POST "/evaluations/$PROJECT_ID/expert-reviews/$review_to_reject/audit/reject" "$AUDITOR_TOKEN" '{"reason":"估值上沿偏高，缺少足够成交对比支撑，请补充说明后重提。"}'
  ensure_success_code 200 "审核人驳回专家评估" || return 1

  http_request GET "/evaluations/$PROJECT_ID" "$ADMIN_TOKEN"
  ensure_success_code 200 "管理员刷新驳回状态" || return 1
  PROJECT_STATUS_REJECTED="$(json_value '.status // empty')"

  http_request GET "/evaluations/$PROJECT_ID/artworks/$ARTWORK1_ID/my-review" "$EXPERT2_TOKEN"
  ensure_success_code 200 "专家乙查看驳回原因" || return 1
  REJECT_REASON="$(json_value '.review.rejectedReason // empty')"

  http_request POST "/evaluations/$PROJECT_ID/submit-review" "$ADMIN_TOKEN"
  if [ "$LAST_RESPONSE_CODE" -eq 200 ]; then
    abort_scenario "管理员在驳回后直接送审" "接口未拦截未重提的送审动作"
    return 1
  fi

  payload="$("$JQ_BIN" -nc --arg est "RMB 460000-520000" --arg curr "CNY" --arg comment "补充同类近三年成交比对后，下调上沿。" --argjson m1 "$pm1" --argjson m2 "$pm2" --argjson m3 "$pm3" '{finalEstimate:$est,finalEstimateCurrency:$curr,comment:$comment,scores:[{projectMetricId:$m1,score:8},{projectMetricId:$m2,optionValue:"LOW"},{projectMetricId:$m3,textValue:"补充横向成交比对后，估值区间更贴近近期市场。"}]}')"
  http_request POST "/evaluations/$PROJECT_ID/artworks/$ARTWORK1_ID/my-review/submit" "$EXPERT2_TOKEN" "$payload"
  ensure_success_code 200 "专家乙重提评估" || return 1
  RESUBMIT_STATUS="$(json_value '.status // empty')"

  http_request POST "/evaluations/$PROJECT_ID/submit-review" "$ADMIN_TOKEN"
  ensure_success_code 200 "管理员二次送审" || return 1
  PROJECT_STATUS_IN_REVIEW_2="$(json_value '.status // empty')"

  http_request POST "/evaluations/$PROJECT_ID/audit/approve" "$AUDITOR_TOKEN" '{"comment":"双专家意见已趋同，风险说明充分，同意结项。"}'
  ensure_success_code 200 "审核人通过项目" || return 1
  PROJECT_STATUS_COMPLETED="$(json_value '.status // empty')"
  AUDIT_RESULT="$(json_value '.auditResult // empty')"

  http_request GET "/evaluations/$PROJECT_ID/audit-records" "$AUDITOR_TOKEN"
  ensure_success_code 200 "读取审核历史" || return 1
  AUDIT_RECORD_ACTIONS="$(json_value '[.[].action] | join(",")')"

  http_request GET "/audit-logs?page=0&size=200" "$ADMIN_TOKEN"
  ensure_success_code 200 "读取系统审计日志" || return 1
  AUDIT_LOG_ACTIONS="$(printf '%s' "$LAST_RESPONSE_BODY" | "$JQ_BIN" -r --arg pid "$PROJECT_ID" '[.items[] | select(.resourceType=="EVALUATION" and .resourceId==$pid)] | map(.action) | join(",")')"

  http_request DELETE "/evaluations/$PROJECT_ID" "$ADMIN_TOKEN"
  COMPLETED_DELETE_CODE="$LAST_RESPONSE_CODE"
  COMPLETED_DELETE_ERROR="$(printf '%s' "$LAST_RESPONSE_BODY" | "$JQ_BIN" -r '.error // .message // empty')"

  http_request GET "/evaluations/$PROJECT_ID" "$ADMIN_TOKEN"
  if [ "$LAST_RESPONSE_CODE" -eq 200 ]; then
    PROJECT_STATUS_AFTER_DELETE_ATTEMPT="$(json_value '.status // empty')"
  fi

  return 0
}

main() {
  if [ "${1:-}" = "--help" ]; then
    usage
    exit 0
  fi

  require_command "$CURL_BIN" "curl"
  require_command "$JQ_BIN" "jq"

  run_scenario || true
  evaluate_checks
  cleanup_resources
  generate_json_report
  generate_markdown_report

  echo "评估场景执行完成。"
  echo "Markdown 报告：$MARKDOWN_REPORT_PATH"
  echo "JSON 证据：$JSON_REPORT_PATH"

  if [ "${#FAILURES[@]}" -gt 0 ]; then
    exit 1
  fi
  exit 0
}

main "$@"
