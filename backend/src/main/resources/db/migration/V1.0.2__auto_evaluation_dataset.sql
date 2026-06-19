alter table evaluation_metric_definitions
    add column if not exists built_in boolean not null default false;

alter table evaluation_metric_templates
    add column if not exists code varchar(100),
    add column if not exists built_in boolean not null default false;

create unique index if not exists uk_evaluation_metric_template_code
    on evaluation_metric_templates(code)
    where code is not null;

alter table expert_reviews
    add column if not exists final_estimate_amount numeric(19, 2);

create table if not exists auto_evaluation_datasets (
    id bigserial primary key,
    name varchar(255) not null,
    source_evaluation_id bigint not null,
    source_evaluation_name varchar(255) not null,
    template_id bigint not null,
    template_code varchar(100) not null,
    aggregation_strategy varchar(50) not null,
    selected_expert_id bigint,
    selected_expert_name varchar(100),
    status varchar(30) not null,
    selected_count integer not null default 0,
    sample_count integer not null default 0,
    skipped_count integer not null default 0,
    excluded_by_user_count integer not null default 0,
    estimated_selected_image_size bigint not null default 0,
    storage_path text,
    zip_file_path text,
    zip_file_size bigint,
    zip_sha256 varchar(64),
    error_message text,
    created_by bigint,
    created_by_name varchar(100),
    generated_at timestamp(6),
    archived_at timestamp(6),
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    constraint auto_evaluation_datasets_status_check check (status in ('DRAFT', 'GENERATING', 'READY', 'FAILED', 'ARCHIVED')),
    constraint auto_evaluation_datasets_strategy_check check (aggregation_strategy in ('AVERAGE_ALL_EXPERTS', 'SELECTED_EXPERT'))
);

create index if not exists idx_auto_eval_dataset_status on auto_evaluation_datasets(status);
create index if not exists idx_auto_eval_dataset_source_eval on auto_evaluation_datasets(source_evaluation_id);
create index if not exists idx_auto_eval_dataset_created_at on auto_evaluation_datasets(created_at);

create table if not exists auto_evaluation_dataset_artworks (
    id bigserial primary key,
    dataset_id bigint not null references auto_evaluation_datasets(id) on delete cascade,
    artwork_id bigint not null,
    created_at timestamp(6) not null,
    constraint uk_auto_eval_dataset_artwork unique (dataset_id, artwork_id)
);

create index if not exists idx_auto_eval_dataset_artworks_dataset_id on auto_evaluation_dataset_artworks(dataset_id);
create index if not exists idx_auto_eval_dataset_artworks_artwork_id on auto_evaluation_dataset_artworks(artwork_id);
