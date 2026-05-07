package com.artfetch.config;

import com.artfetch.entity.SearchTask;
import com.artfetch.evaluation.entity.EvaluationProjectStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaMaintenanceService {

    private static final String TASK_TYPE_CONSTRAINT = "search_tasks_task_type_check";
    private static final String EVALUATION_PROJECT_STATUS_CONSTRAINT = "evaluation_projects_status_check";
    private static final String ARTWORK_TASK_EXTERNAL_UNIQUE_INDEX = "uk_artworks_task_external_id";
    private static final String MAINTENANCE_FLAGS_TABLE = "app_maintenance_flags";
    private static final String TIMESTAMP_ALIGNMENT_FLAG = "timestamps_aligned_to_asia_shanghai_v1";

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void syncSchema() {
        ensureMaintenanceFlagsTable();
        String enumValues = enumConstraintValues(SearchTask.TaskType.values());

        jdbcTemplate.execute("alter table search_tasks drop constraint if exists " + TASK_TYPE_CONSTRAINT);
        jdbcTemplate.execute("alter table search_tasks add constraint " + TASK_TYPE_CONSTRAINT
                + " check (task_type in (" + enumValues + "))");
        log.info("数据库约束已同步: table=search_tasks, constraint={}, taskTypes={}",
                TASK_TYPE_CONSTRAINT,
                Arrays.stream(SearchTask.TaskType.values()).map(Enum::name).toList());

        String projectStatusValues = enumConstraintValues(EvaluationProjectStatus.values());
        jdbcTemplate.execute("alter table evaluation_projects drop constraint if exists " + EVALUATION_PROJECT_STATUS_CONSTRAINT);
        jdbcTemplate.execute("alter table evaluation_projects add constraint " + EVALUATION_PROJECT_STATUS_CONSTRAINT
                + " check (status in (" + projectStatusValues + "))");
        log.info("数据库约束已同步: table=evaluation_projects, constraint={}, statuses={}",
                EVALUATION_PROJECT_STATUS_CONSTRAINT,
                Arrays.stream(EvaluationProjectStatus.values()).map(Enum::name).toList());

        alignHistoricalTimestamps();

        int removedDuplicates = jdbcTemplate.update("""
                delete from artworks a
                using artworks b
                where a.task_id = b.task_id
                  and a.external_id = b.external_id
                  and a.external_id is not null
                  and a.id < b.id
                """);
        if (removedDuplicates > 0) {
            log.warn("已清理重复拍品记录: table=artworks, removedRows={}", removedDuplicates);
        }

        jdbcTemplate.execute("""
                create unique index if not exists %s
                on artworks (task_id, external_id)
                where external_id is not null
                """.formatted(ARTWORK_TASK_EXTERNAL_UNIQUE_INDEX));
        log.info("数据库索引已同步: table=artworks, index={}", ARTWORK_TASK_EXTERNAL_UNIQUE_INDEX);
    }

    private String enumConstraintValues(Enum<?>[] values) {
        return Arrays.stream(values)
                .map(Enum::name)
                .map(value -> "'" + value + "'")
                .collect(Collectors.joining(", "));
    }

    private void ensureMaintenanceFlagsTable() {
        jdbcTemplate.execute("""
                create table if not exists app_maintenance_flags (
                    flag_key varchar(128) primary key,
                    applied_at timestamp with time zone not null default now()
                )
                """);
    }

    private void alignHistoricalTimestamps() {
        Integer alreadyApplied = jdbcTemplate.queryForObject(
                "select count(*) from " + MAINTENANCE_FLAGS_TABLE + " where flag_key = ?",
                Integer.class,
                TIMESTAMP_ALIGNMENT_FLAG
        );
        if (alreadyApplied != null && alreadyApplied > 0) {
            return;
        }

        int updatedTaskRows = jdbcTemplate.update("""
                update search_tasks
                set created_at = created_at + interval '8 hours',
                    updated_at = case
                        when updated_at is null then null
                        else updated_at + interval '8 hours'
                    end
                """);
        int updatedArtworkRows = jdbcTemplate.update("""
                update artworks
                set created_at = created_at + interval '8 hours',
                    original_image_downloaded_at = case
                        when original_image_downloaded_at is null then null
                        else original_image_downloaded_at + interval '8 hours'
                    end,
                    hd_image_downloaded_at = case
                        when hd_image_downloaded_at is null then null
                        else hd_image_downloaded_at + interval '8 hours'
                    end
                """);
        int updatedFailureRows = jdbcTemplate.update("""
                update fetch_failures
                set first_occurred_at = first_occurred_at + interval '8 hours',
                    last_occurred_at = last_occurred_at + interval '8 hours',
                    last_retried_at = case
                        when last_retried_at is null then null
                        else last_retried_at + interval '8 hours'
                    end,
                    resolved_at = case
                        when resolved_at is null then null
                        else resolved_at + interval '8 hours'
                    end
                """);

        jdbcTemplate.update(
                "insert into " + MAINTENANCE_FLAGS_TABLE + " (flag_key) values (?) on conflict (flag_key) do nothing",
                TIMESTAMP_ALIGNMENT_FLAG
        );
        log.warn("已对齐历史时间到 Asia/Shanghai: searchTasks={}, artworks={}, fetchFailures={}",
                updatedTaskRows,
                updatedArtworkRows,
                updatedFailureRows);
    }
}
