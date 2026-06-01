-- ArtFetch V1.0.0 baseline schema.

create table if not exists search_tasks (
    id bigserial primary key,
    name varchar(255) not null,
    keyword varchar(255) not null,
    task_type varchar(255),
    parent_task_id bigint,
    target_task_id bigint,
    status varchar(255) not null,
    current_page integer not null default 0,
    total_pages integer not null default 0,
    total_fetched integer not null default 0,
    error_message text,
    detail_fetch_concurrency integer not null default 1,
    detail_request_count bigint not null default 0,
    detail_success_count bigint not null default 0,
    detail_failure_count bigint not null default 0,
    avg_detail_latency_ms bigint not null default 0,
    p95_detail_latency_ms bigint not null default 0,
    max_detail_latency_ms bigint not null default 0,
    last_page_duration_ms bigint not null default 0,
    last_page_items_per_minute double precision not null default 0,
    detail_failure_rate double precision not null default 0,
    concurrency_advice text,
    created_at timestamp(6) not null default now(),
    updated_at timestamp(6),
    constraint search_tasks_status_check check (status in ('PENDING', 'RUNNING', 'PAUSED', 'COMPLETED', 'FAILED', 'CANCELLED')),
    constraint search_tasks_task_type_check check (task_type in ('SEARCH', 'SEARCH_BATCH', 'ORIGINAL_IMAGE', 'HD_IMAGE', 'TRANSACTION_PRICE', 'DESCRIPTION'))
);

create table if not exists artworks (
    id bigserial primary key,
    task_id bigint not null references search_tasks(id),
    external_id varchar(255),
    title text not null,
    lot_number varchar(255),
    artist varchar(255),
    medium varchar(255),
    format varchar(255),
    dimensions varchar(255),
    description text,
    image_url text,
    original_image_source_url text,
    original_image_status varchar(255),
    original_image_path text,
    original_image_content_type varchar(255),
    original_image_size bigint,
    original_image_downloaded_at timestamp(6),
    original_image_last_error text,
    hd_image_source_url text,
    hd_image_status varchar(255),
    hd_image_path text,
    hd_image_storage_type varchar(255) not null default 'LOCAL',
    hd_image_object_config_id bigint,
    hd_image_object_bucket varchar(255),
    hd_image_object_key text,
    hd_image_object_etag varchar(255),
    hd_image_object_size bigint,
    hd_image_object_uploaded_at timestamp(6),
    hd_image_migration_status varchar(255) not null default 'NOT_MIGRATED',
    hd_image_migration_last_error text,
    hd_image_migration_updated_at timestamp(6),
    hd_image_content_type varchar(255),
    hd_image_size bigint,
    hd_image_downloaded_at timestamp(6),
    hd_image_last_error text,
    source_url text,
    valuation text,
    transaction_price text,
    transaction_price_note text,
    auction_house varchar(255),
    auction_name varchar(255),
    auction_session varchar(255),
    auction_date varchar(255),
    auction_location varchar(255),
    preview_time varchar(255),
    preview_location varchar(255),
    extra_data text,
    created_at timestamp(6) not null default now()
);

create index if not exists idx_artwork_task_id on artworks(task_id);
create index if not exists idx_artwork_external_id on artworks(external_id);
create unique index if not exists uk_artworks_task_external_id on artworks(task_id, external_id) where external_id is not null;
create index if not exists idx_artworks_hd_storage_type on artworks(hd_image_storage_type);
create index if not exists idx_artworks_hd_migration_status on artworks(hd_image_migration_status);
create index if not exists idx_artworks_hd_object_key on artworks(hd_image_object_key);

create table if not exists fetch_failures (
    id bigserial primary key,
    task_id bigint not null references search_tasks(id),
    failure_type varchar(255) not null,
    failure_key varchar(255) not null unique,
    page_number integer not null default 0,
    external_id varchar(255),
    request_url text,
    source_url text,
    error_type varchar(255),
    error_message text,
    failure_count integer not null default 0,
    resolved boolean not null default false,
    first_occurred_at timestamp(6) not null default now(),
    last_occurred_at timestamp(6) not null default now(),
    last_retried_at timestamp(6),
    resolved_at timestamp(6)
);

create index if not exists idx_fetch_failure_task_id on fetch_failures(task_id);
create index if not exists idx_fetch_failure_resolved on fetch_failures(resolved);
create unique index if not exists idx_fetch_failure_failure_key on fetch_failures(failure_key);

create table if not exists object_storage_configs (
    id bigserial primary key,
    name varchar(255) not null,
    provider varchar(255) not null,
    endpoint text not null,
    region varchar(255),
    bucket varchar(255) not null,
    path_prefix varchar(255),
    access_key text not null,
    secret_key_encrypted text not null,
    public_base_url text,
    sdk_mode varchar(255) not null default 'VOLCENGINE_TOS_SDK',
    network_type varchar(255) not null default 'PUBLIC',
    enabled boolean not null default false,
    upload_enabled boolean not null default false,
    migrate_enabled boolean not null default false,
    last_test_status varchar(255),
    last_test_message text,
    last_test_at timestamp(6),
    created_by bigint,
    updated_by bigint,
    created_at timestamp(6) not null default now(),
    updated_at timestamp(6)
);

create unique index if not exists uk_object_storage_configs_active on object_storage_configs(enabled) where enabled = true;

create table if not exists hd_image_migration_tasks (
    id bigserial primary key,
    name varchar(255) not null,
    config_id bigint not null,
    mode varchar(255) not null,
    scope_type varchar(255) not null,
    target_task_id bigint,
    status varchar(255) not null,
    total_count integer not null default 0,
    processed_count integer not null default 0,
    success_count integer not null default 0,
    skipped_count integer not null default 0,
    failed_count integer not null default 0,
    current_artwork_id bigint,
    upload_concurrency integer not null default 4,
    error_message text,
    started_at timestamp(6),
    completed_at timestamp(6),
    created_by bigint,
    created_at timestamp(6) not null default now(),
    updated_at timestamp(6)
);

create index if not exists idx_hd_image_migration_tasks_status on hd_image_migration_tasks(status);
create index if not exists idx_hd_image_migration_tasks_target_task on hd_image_migration_tasks(target_task_id);

create table if not exists hd_image_migration_items (
    id bigserial primary key,
    migration_task_id bigint not null,
    artwork_id bigint not null,
    local_path text,
    object_key text,
    status varchar(255) not null,
    file_size bigint,
    uploaded_size bigint,
    etag varchar(255),
    error_message text,
    attempt_count integer not null default 0,
    started_at timestamp(6),
    completed_at timestamp(6),
    created_at timestamp(6) not null default now(),
    updated_at timestamp(6),
    constraint uk_hd_image_migration_items_task_artwork unique (migration_task_id, artwork_id)
);

create index if not exists idx_hd_image_migration_items_status on hd_image_migration_items(migration_task_id, status);

create table if not exists auth_users (
    id bigserial primary key,
    username varchar(100) not null unique,
    password_hash text not null,
    display_name varchar(100) not null,
    email varchar(255),
    phone varchar(50),
    status varchar(30) not null,
    last_login_at timestamp(6),
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null
);

create index if not exists idx_auth_users_status on auth_users(status);

create table if not exists auth_roles (
    id bigserial primary key,
    code varchar(100) not null unique,
    name varchar(100) not null,
    description text,
    enabled boolean not null default true,
    built_in boolean not null default false,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null
);

create index if not exists idx_auth_roles_enabled on auth_roles(enabled);

create table if not exists auth_permissions (
    id bigserial primary key,
    code varchar(150) not null unique,
    name varchar(100) not null,
    module varchar(80) not null,
    resource_type varchar(50) not null,
    description text,
    enabled boolean not null default true,
    built_in boolean not null default false,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null
);

create index if not exists idx_auth_permissions_module on auth_permissions(module);
create index if not exists idx_auth_permissions_enabled on auth_permissions(enabled);

create table if not exists auth_user_roles (
    user_id bigint not null references auth_users(id),
    role_id bigint not null references auth_roles(id),
    constraint uk_auth_user_roles_user_role unique (user_id, role_id)
);

create table if not exists auth_role_permissions (
    role_id bigint not null references auth_roles(id),
    permission_id bigint not null references auth_permissions(id),
    constraint uk_auth_role_permissions_role_permission unique (role_id, permission_id)
);

create table if not exists auth_audit_logs (
    id bigserial primary key,
    user_id bigint,
    username varchar(100),
    action varchar(100) not null,
    resource_type varchar(80),
    resource_id varchar(100),
    description text,
    ip_address varchar(80),
    user_agent text,
    success boolean not null default true,
    error_message text,
    created_at timestamp(6) not null
);

create index if not exists idx_auth_audit_logs_user_id on auth_audit_logs(user_id);
create index if not exists idx_auth_audit_logs_action on auth_audit_logs(action);
create index if not exists idx_auth_audit_logs_created_at on auth_audit_logs(created_at);
create index if not exists idx_auth_audit_logs_resource on auth_audit_logs(resource_type, resource_id);

create table if not exists evaluation_metric_definitions (
    id bigserial primary key,
    code varchar(100) not null unique,
    name varchar(100) not null,
    description text,
    category varchar(100),
    applicable_artwork_types text,
    score_type varchar(50),
    min_score double precision,
    max_score double precision,
    score_step double precision,
    default_weight double precision,
    required boolean not null default false,
    input_component varchar(50),
    option_values text,
    scoring_guide text,
    scoring_rubric text,
    unit varchar(50),
    tags text,
    enabled boolean not null default true,
    sort_order integer not null default 0,
    version integer not null default 1,
    created_by varchar(100),
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null
);

create index if not exists idx_evaluation_metric_definition_enabled on evaluation_metric_definitions(enabled);
create index if not exists idx_evaluation_metric_definition_code on evaluation_metric_definitions(code);

create table if not exists evaluation_metric_templates (
    id bigserial primary key,
    name varchar(150) not null,
    description text,
    enabled boolean not null default true,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null
);

create index if not exists idx_evaluation_metric_template_enabled on evaluation_metric_templates(enabled);

create table if not exists evaluation_metric_template_items (
    id bigserial primary key,
    template_id bigint not null,
    metric_definition_id bigint,
    metric_definition_version integer,
    code_snapshot varchar(100),
    name_snapshot varchar(100) not null,
    description_snapshot text,
    category_snapshot varchar(100),
    score_type varchar(50),
    min_score double precision,
    max_score double precision,
    score_step double precision,
    weight double precision,
    required boolean not null default false,
    input_component varchar(50),
    option_values text,
    scoring_guide text,
    scoring_rubric text,
    sort_order integer not null default 0,
    enabled boolean not null default true,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null
);

create index if not exists idx_evaluation_metric_template_item_template_id on evaluation_metric_template_items(template_id);

create table if not exists evaluation_projects (
    id bigserial primary key,
    name varchar(255) not null,
    description text,
    status varchar(30) not null,
    config_locked_at timestamp(6),
    deleted_at timestamp(6),
    auditor_id bigint,
    auditor_name varchar(100),
    criteria_snapshot text,
    artwork_count integer not null default 0,
    expert_count integer not null default 0,
    expected_review_count integer not null default 0,
    completed_count integer not null default 0,
    rejected_review_count integer not null default 0,
    submitted_for_review_at timestamp(6),
    reviewed_at timestamp(6),
    audit_result varchar(20),
    audit_comment text,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    completed_at timestamp(6),
    constraint evaluation_projects_status_check check (status in ('DRAFT', 'PENDING', 'PUBLISHED', 'IN_PROGRESS', 'READY_FOR_REVIEW', 'IN_REVIEW', 'REVIEW_REJECTED', 'COMPLETED', 'CANCELLED'))
);

create index if not exists idx_evaluation_project_status on evaluation_projects(status);
create index if not exists idx_evaluation_project_auditor_id on evaluation_projects(auditor_id);
create index if not exists idx_evaluation_project_deleted_at on evaluation_projects(deleted_at);

create table if not exists evaluation_project_metrics (
    id bigserial primary key,
    evaluation_id bigint not null,
    source_metric_definition_id bigint,
    source_template_id bigint,
    source_version integer,
    code varchar(100) not null,
    name varchar(100) not null,
    description text,
    category varchar(100),
    score_type varchar(50),
    min_score double precision,
    max_score double precision,
    score_step double precision,
    weight double precision,
    required boolean not null default false,
    input_component varchar(50),
    option_values text,
    scoring_guide text,
    scoring_rubric text,
    sort_order integer not null default 0,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null
);

create index if not exists idx_evaluation_project_metric_evaluation_id on evaluation_project_metrics(evaluation_id);

create table if not exists evaluation_project_experts (
    id bigserial primary key,
    evaluation_id bigint not null,
    expert_id bigint not null,
    expert_name varchar(100) not null,
    status varchar(30),
    assigned_at timestamp(6),
    completed_count integer not null default 0,
    total_count integer not null default 0,
    rejected_count integer not null default 0,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null
);

create index if not exists idx_evaluation_project_expert_evaluation_id on evaluation_project_experts(evaluation_id);
create index if not exists idx_evaluation_project_expert_expert_id on evaluation_project_experts(expert_id);
create unique index if not exists uk_evaluation_project_expert_unique on evaluation_project_experts(evaluation_id, expert_id);

create table if not exists evaluation_artworks (
    id bigserial primary key,
    evaluation_id bigint not null,
    artwork_id bigint not null,
    status varchar(30),
    review_page_generated boolean not null default false,
    review_page_generated_at timestamp(6),
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null
);

create index if not exists idx_evaluation_artwork_evaluation_id on evaluation_artworks(evaluation_id);
create unique index if not exists uk_evaluation_artwork_unique on evaluation_artworks(evaluation_id, artwork_id);

create table if not exists expert_reviews (
    id bigserial primary key,
    evaluation_id bigint not null,
    artwork_id bigint not null,
    expert_id bigint not null,
    expert_name varchar(100) not null,
    final_estimate text,
    final_estimate_currency varchar(20),
    comment text,
    status varchar(30) not null,
    rejected_reason text,
    rejected_at timestamp(6),
    resubmitted_at timestamp(6),
    submitted_at timestamp(6),
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null
);

create index if not exists idx_expert_review_evaluation_id on expert_reviews(evaluation_id);
create index if not exists idx_expert_review_expert_id on expert_reviews(expert_id);
create unique index if not exists uk_expert_review_unique on expert_reviews(evaluation_id, artwork_id, expert_id);

create table if not exists expert_review_scores (
    id bigserial primary key,
    review_id bigint not null,
    project_metric_id bigint not null,
    score double precision,
    option_value text,
    text_value text,
    comment text
);

create index if not exists idx_expert_review_score_review_id on expert_review_scores(review_id);
create unique index if not exists uk_expert_review_score_unique on expert_review_scores(review_id, project_metric_id);

create table if not exists evaluation_audit_records (
    id bigserial primary key,
    evaluation_id bigint not null,
    expert_review_id bigint,
    artwork_id bigint,
    expert_id bigint,
    expert_name varchar(100),
    auditor_id bigint,
    auditor_name varchar(100),
    result varchar(20),
    comment text,
    action varchar(50),
    previous_status varchar(30),
    next_status varchar(30),
    created_at timestamp(6) not null
);

create index if not exists idx_evaluation_audit_record_evaluation_id on evaluation_audit_records(evaluation_id);
create index if not exists idx_evaluation_audit_record_review_id on evaluation_audit_records(expert_review_id);

create table if not exists app_maintenance_flags (
    flag_key varchar(128) primary key,
    applied_at timestamp with time zone not null default now()
);
