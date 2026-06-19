package com.artfetch.auth.support;

public final class PermissionCodes {
    public static final String TASK_VIEW = "task:view";
    public static final String TASK_CREATE = "task:create";
    public static final String TASK_START = "task:start";
    public static final String TASK_PAUSE = "task:pause";
    public static final String TASK_RESUME = "task:resume";
    public static final String TASK_CANCEL = "task:cancel";
    public static final String TASK_DELETE = "task:delete";
    public static final String TASK_FAILURE_VIEW = "task:failure:view";
    public static final String TASK_FAILURE_RETRY = "task:failure:retry";

    public static final String ARTWORK_VIEW = "artwork:view";
    public static final String ARTWORK_IMAGE_VIEW = "artwork:image:view";
    public static final String ARTWORK_IMAGE_REDOWNLOAD = "artwork:image:redownload";
    public static final String ARTWORK_TRANSACTION_PRICE_SUPPLEMENT = "artwork:transaction-price:supplement";
    public static final String ARTWORK_EXPORT = "artwork:export";

    public static final String SETTINGS_OBJECT_STORAGE_VIEW = "settings:object-storage:view";
    public static final String SETTINGS_OBJECT_STORAGE_MANAGE = "settings:object-storage:manage";
    public static final String HD_IMAGE_MIGRATION_VIEW = "hd-image:migration:view";
    public static final String HD_IMAGE_MIGRATION_MANAGE = "hd-image:migration:manage";

    public static final String EVALUATION_METRIC_VIEW = "evaluation-metric:view";
    public static final String EVALUATION_METRIC_CREATE = "evaluation-metric:create";
    public static final String EVALUATION_METRIC_UPDATE = "evaluation-metric:update";
    public static final String EVALUATION_METRIC_DISABLE = "evaluation-metric:disable";
    public static final String EVALUATION_TEMPLATE_VIEW = "evaluation-template:view";
    public static final String EVALUATION_TEMPLATE_CREATE = "evaluation-template:create";
    public static final String EVALUATION_TEMPLATE_UPDATE = "evaluation-template:update";
    public static final String EVALUATION_TEMPLATE_DISABLE = "evaluation-template:disable";
    public static final String EVALUATION_VIEW = "evaluation:view";
    public static final String EVALUATION_CREATE = "evaluation:create";
    public static final String EVALUATION_UPDATE = "evaluation:update";
    public static final String EVALUATION_DELETE = "evaluation:delete";
    public static final String EVALUATION_PUBLISH = "evaluation:publish";
    public static final String EVALUATION_SUBMIT_REVIEW = "evaluation:submit-review";
    public static final String EVALUATION_RESULT_VIEW = "evaluation:result:view";
    public static final String EVALUATION_REVIEW_ASSIGNED_VIEW = "evaluation-review:assigned:view";
    public static final String EVALUATION_REVIEW_OWN_VIEW = "evaluation-review:own:view";
    public static final String EVALUATION_REVIEW_OWN_SAVE = "evaluation-review:own:save";
    public static final String EVALUATION_REVIEW_OWN_SUBMIT = "evaluation-review:own:submit";
    public static final String EVALUATION_REVIEW_OWN_RESUBMIT = "evaluation-review:own:resubmit";
    public static final String EVALUATION_AUDIT_VIEW = "evaluation-audit:view";
    public static final String EVALUATION_AUDIT_APPROVE = "evaluation-audit:approve";
    public static final String EVALUATION_AUDIT_REJECT_REVIEW = "evaluation-audit:reject-review";
    public static final String EVALUATION_AUDIT_HISTORY_VIEW = "evaluation-audit:history:view";

    public static final String AUTO_EVALUATION_DATASET_VIEW = "auto-evaluation:dataset:view";
    public static final String AUTO_EVALUATION_DATASET_CREATE = "auto-evaluation:dataset:create";
    public static final String AUTO_EVALUATION_DATASET_EXPORT = "auto-evaluation:dataset:export";

    public static final String USER_VIEW = "user:view";
    public static final String USER_CREATE = "user:create";
    public static final String USER_UPDATE = "user:update";
    public static final String USER_DISABLE = "user:disable";
    public static final String ROLE_VIEW = "role:view";
    public static final String ROLE_CREATE = "role:create";
    public static final String ROLE_UPDATE = "role:update";
    public static final String ROLE_DISABLE = "role:disable";
    public static final String AUDIT_LOG_VIEW = "audit-log:view";

    private PermissionCodes() {
    }
}
