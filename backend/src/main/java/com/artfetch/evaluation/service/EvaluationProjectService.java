package com.artfetch.evaluation.service;

import com.artfetch.auth.entity.AuthUser;
import com.artfetch.auth.entity.UserStatus;
import com.artfetch.auth.repository.AuthUserRepository;
import com.artfetch.auth.service.AuditLogService;
import com.artfetch.dto.ArtworkDto;
import com.artfetch.dto.PageResult;
import com.artfetch.entity.Artwork;
import com.artfetch.evaluation.dto.*;
import com.artfetch.evaluation.entity.*;
import com.artfetch.evaluation.repository.*;
import com.artfetch.repository.ArtworkRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EvaluationProjectService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<CriterionItemDto>> CRITERIA_TYPE = new TypeReference<>() {};
    private static final List<ExpertReviewStatus> COMPLETED_REVIEW_STATUSES = List.of(
            ExpertReviewStatus.SUBMITTED, ExpertReviewStatus.RESUBMITTED
    );

    private final EvaluationProjectRepository projectRepository;
    private final EvaluationProjectMetricRepository metricRepository;
    private final EvaluationArtworkRepository evaluationArtworkRepository;
    private final EvaluationProjectExpertRepository projectExpertRepository;
    private final ExpertReviewRepository expertReviewRepository;
    private final ExpertReviewScoreRepository expertReviewScoreRepository;
    private final EvaluationAuditRecordRepository auditRecordRepository;
    private final EvaluationMetricDefinitionRepository metricDefinitionRepository;
    private final ArtworkRepository artworkRepository;
    private final AuthUserRepository authUserRepository;
    private final EvaluationAccessService accessService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public PageResult<EvaluationProjectListItemDto> list(String scope, int page, int size) {
        boolean assignedOnly = "assigned".equalsIgnoreCase(scope);
        Page<EvaluationProject> projectPage;
        if (assignedOnly) {
            Long currentUserId = accessService.currentUserId();
            List<Long> ids = projectExpertRepository.findByExpertIdOrderByAssignedAtDesc(currentUserId).stream()
                    .map(EvaluationProjectExpert::getEvaluationId)
                    .distinct()
                    .toList();
            if (ids.isEmpty()) {
                return emptyPage(page, size);
            }
            projectPage = projectRepository.findByIdInAndStatusNotInAndDeletedAtIsNullOrderByCreatedAtDesc(
                    ids,
                    List.of(EvaluationProjectStatus.DRAFT, EvaluationProjectStatus.PENDING),
                    PageRequest.of(page, size)
            );
        } else {
            projectPage = projectRepository.findByDeletedAtIsNullOrderByCreatedAtDesc(PageRequest.of(page, size));
        }

        List<Long> ids = projectPage.getContent().stream().map(EvaluationProject::getId).toList();
        Map<Long, List<String>> expertNames = ids.stream()
                .collect(Collectors.toMap(Function.identity(),
                        id -> projectExpertRepository.findByEvaluationIdOrderByExpertNameAscIdAsc(id).stream()
                                .map(EvaluationProjectExpert::getExpertName)
                                .toList()));
        return PageResult.of(projectPage, project -> EvaluationProjectListItemDto.from(project, expertNames.getOrDefault(project.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public EvaluationProjectDto get(Long id) {
        EvaluationProject project = requireProject(id);
        accessService.requireProjectView(project);
        return buildProjectDto(project);
    }

    @Transactional(readOnly = true)
    public PageResult<ArtworkPreviewDto> previewArtworks(PreviewArtworksRequest request) {
        int page = request.page() == null ? 0 : Math.max(0, request.page());
        int size = request.size() == null ? 20 : Math.max(1, Math.min(100, request.size()));
        Page<Artwork> artworks = artworkRepository.findAll(buildArtworkCriteriaSpec(request.criteria()), PageRequest.of(page, size));
        return PageResult.of(artworks, art -> ArtworkPreviewDto.from(ArtworkDto.from(art)));
    }

    @Transactional
    public EvaluationProjectDto create(CreateEvaluationProjectRequest request) {
        accessService.requireProjectCreate();
        EvaluationProject project = new EvaluationProject();
        project.setName(request.name().trim());
        project.setDescription(blankToNull(request.description()));
        AuthUser auditor = requireAuditorUser(request.auditorId());
        project.setAuditorId(auditor.getId());
        project.setAuditorName(auditor.getDisplayName());
        project.setCriteriaSnapshot(writeCriteria(request.criteria()));
        project.setStatus(EvaluationProjectStatus.PENDING);
        EvaluationProject saved = projectRepository.save(project);
        applyAssociations(saved, request.artworkIds(), request.expertIds(), request.metrics());
        recalculateProjectState(saved.getId());
        auditLogService.recordSuccess("evaluation.create", "EVALUATION", String.valueOf(saved.getId()), "创建评估项目 " + saved.getName());
        return get(saved.getId());
    }

    @Transactional
    public EvaluationProjectDto update(Long id, UpdateEvaluationProjectRequest request) {
        accessService.requireProjectManage();
        EvaluationProject project = requireProject(id);
        if (!isProjectEditable(project)) {
            throw new IllegalStateException("评估项目发布后，不能修改项目数据");
        }
        project.setName(request.name().trim());
        project.setDescription(blankToNull(request.description()));
        AuthUser auditor = requireAuditorUser(request.auditorId());
        project.setAuditorId(auditor.getId());
        project.setAuditorName(auditor.getDisplayName());
        project.setCriteriaSnapshot(writeCriteria(request.criteria()));

        if (request.artworkIds() != null && request.expertIds() != null && request.metrics() != null) {
            applyAssociations(project, request.artworkIds(), request.expertIds(), request.metrics());
        }
        projectRepository.save(project);
        recalculateProjectState(id);
        auditLogService.recordSuccess("evaluation.update", "EVALUATION", String.valueOf(id), "更新评估项目 " + project.getName());
        return get(id);
    }

    @Transactional
    public EvaluationProjectDto publish(Long id) {
        accessService.requireProjectPublish();
        EvaluationProject project = requireProject(id);
        if (!isProjectEditable(project)) {
            throw new IllegalStateException("只有待发布的评估项目才能发布");
        }
        if (project.getArtworkCount() <= 0 || project.getExpertCount() <= 0 || project.getExpectedReviewCount() <= 0) {
            throw new IllegalStateException("评估项目必须配置艺术品、专家和评估记录后才能发布");
        }
        if (metricRepository.findByEvaluationIdOrderBySortOrderAscIdAsc(id).isEmpty()) {
            throw new IllegalStateException("评估项目必须配置评估指标后才能发布");
        }
        project.setStatus(EvaluationProjectStatus.PUBLISHED);
        if (project.getConfigLockedAt() == null) {
            project.setConfigLockedAt(LocalDateTime.now());
        }
        projectRepository.save(project);
        auditLogService.recordSuccess("evaluation.publish", "EVALUATION", String.valueOf(id), "发布评估项目 " + project.getName());
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        accessService.requireProjectDelete();
        EvaluationProject project = requireProject(id);
        if (!isProjectDeleteAllowed(project)) {
            throw new IllegalStateException("只有草稿或未开始的评估项目才能删除");
        }
        project.setDeletedAt(LocalDateTime.now());
        project.setStatus(EvaluationProjectStatus.CANCELLED);
        projectRepository.save(project);
        auditLogService.recordSuccess("evaluation.delete", "EVALUATION", String.valueOf(id), "删除评估项目 " + project.getName());
    }

    @Transactional
    public EvaluationProjectDto submitForReview(Long id) {
        EvaluationProject project = requireProject(id);
        if (!isProjectSubmittedAllowed(project)) {
            throw new IllegalStateException("当前项目状态不允许提交审核");
        }
        long total = expertReviewRepository.countByEvaluationId(id);
        long completed = expertReviewRepository.countByEvaluationIdAndStatusIn(id, COMPLETED_REVIEW_STATUSES);
        if (total == 0 || total != completed) {
            throw new IllegalStateException("必须等待全部专家评估提交后，才能提交审核");
        }
        project.setStatus(EvaluationProjectStatus.IN_REVIEW);
        project.setSubmittedForReviewAt(LocalDateTime.now());
        projectRepository.save(project);
        recalculateProjectState(id);
        auditLogService.recordSuccess("evaluation.submit-review", "EVALUATION", String.valueOf(id), "提交评估项目审核 " + project.getName());
        return get(id);
    }

    private boolean isProjectDeleteAllowed(EvaluationProject project) {
        return project.getStatus() == EvaluationProjectStatus.DRAFT
                || project.getStatus() == EvaluationProjectStatus.PENDING;
    }

    @Transactional(readOnly = true)
    public List<EvaluationProjectExpertDto> listExperts(Long evaluationId) {
        EvaluationProject project = requireProject(evaluationId);
        accessService.requireProjectView(project);
        return projectExpertRepository.findByEvaluationIdOrderByExpertNameAscIdAsc(evaluationId).stream()
                .map(EvaluationProjectExpertDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EvaluationArtworkItemDto> listArtworks(Long evaluationId) {
        EvaluationProject project = requireProject(evaluationId);
        accessService.requireProjectView(project);
        return loadArtworkItems(evaluationId);
    }

    @Transactional(readOnly = true)
    public List<MetricConfigDto> listMetrics(Long evaluationId) {
        EvaluationProject project = requireProject(evaluationId);
        accessService.requireProjectView(project);
        return metricRepository.findByEvaluationIdOrderBySortOrderAscIdAsc(evaluationId).stream()
                .map(MetricConfigDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public EvaluationProject requireProject(Long id) {
        EvaluationProject project = projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "评估项目不存在"));
        if (project.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评估项目不存在");
        }
        return project;
    }

    @Transactional
    public void recalculateProjectState(Long evaluationId) {
        EvaluationProject project = requireProject(evaluationId);
        List<ExpertReview> reviews = expertReviewRepository.findByEvaluationIdOrderByArtworkIdAscExpertNameAsc(evaluationId);
        int total = reviews.size();
        int completed = (int) reviews.stream().filter(r -> COMPLETED_REVIEW_STATUSES.contains(r.getStatus())).count();
        int rejected = (int) reviews.stream().filter(r -> r.getStatus() == ExpertReviewStatus.REVIEW_REJECTED).count();
        boolean hasProgress = reviews.stream().anyMatch(r -> r.getStatus() != ExpertReviewStatus.NOT_STARTED);

        project.setExpectedReviewCount(total);
        project.setCompletedCount(completed);
        project.setRejectedReviewCount(rejected);
        project.setArtworkCount(evaluationArtworkRepository.findByEvaluationIdOrderByIdAsc(evaluationId).size());
        project.setExpertCount(projectExpertRepository.findByEvaluationIdOrderByExpertNameAscIdAsc(evaluationId).size());
        if (project.getConfigLockedAt() == null && hasProgress) {
            project.setConfigLockedAt(LocalDateTime.now());
        }
        if (project.getStatus() != EvaluationProjectStatus.IN_REVIEW
                && project.getStatus() != EvaluationProjectStatus.COMPLETED
                && project.getStatus() != EvaluationProjectStatus.CANCELLED) {
            if (rejected > 0) {
                project.setStatus(EvaluationProjectStatus.REVIEW_REJECTED);
            } else if (total > 0 && completed == total) {
                project.setStatus(EvaluationProjectStatus.READY_FOR_REVIEW);
            } else if (hasProgress) {
                project.setStatus(EvaluationProjectStatus.IN_PROGRESS);
            } else {
                project.setStatus(project.getStatus() == EvaluationProjectStatus.PUBLISHED
                        ? EvaluationProjectStatus.PUBLISHED
                        : EvaluationProjectStatus.PENDING);
            }
        }
        if (project.getStatus() == EvaluationProjectStatus.COMPLETED && project.getCompletedAt() == null) {
            project.setCompletedAt(LocalDateTime.now());
        }
        projectRepository.save(project);
        refreshExpertCounters(evaluationId, reviews);
        refreshArtworkStatuses(evaluationId, reviews);
    }

    @Transactional(readOnly = true)
    public EvaluationProjectDto buildProjectDto(EvaluationProject project) {
        List<EvaluationProjectExpertDto> experts = projectExpertRepository.findByEvaluationIdOrderByExpertNameAscIdAsc(project.getId()).stream()
                .map(EvaluationProjectExpertDto::from)
                .toList();
        List<EvaluationArtworkItemDto> artworks = loadArtworkItems(project.getId());
        List<MetricConfigDto> metrics = metricRepository.findByEvaluationIdOrderBySortOrderAscIdAsc(project.getId()).stream()
                .map(MetricConfigDto::from)
                .toList();
        return EvaluationProjectDto.from(project, readCriteria(project.getCriteriaSnapshot()), experts, artworks, metrics);
    }

    @Transactional(readOnly = true)
    public List<CriterionItemDto> readCriteria(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(snapshot, CRITERIA_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("解析评估条件失败");
        }
    }

    private void applyAssociations(EvaluationProject project, List<Long> artworkIds, List<Long> expertIds, List<MetricConfigRequest> metrics) {
        if (artworkIds == null || artworkIds.isEmpty()) {
            throw new IllegalArgumentException("评估项目至少需要一件艺术品");
        }
        if (expertIds == null || expertIds.isEmpty()) {
            throw new IllegalArgumentException("评估项目至少需要一位专家");
        }
        if (metrics == null || metrics.isEmpty()) {
            throw new IllegalArgumentException("评估项目至少需要一个评估指标");
        }

        List<ExpertReview> existingReviews = expertReviewRepository.findByEvaluationIdOrderByArtworkIdAscExpertNameAsc(project.getId());
        if (!existingReviews.isEmpty()) {
            expertReviewScoreRepository.deleteByReviewIdIn(existingReviews.stream().map(ExpertReview::getId).toList());
        }
        expertReviewRepository.deleteByEvaluationId(project.getId());
        evaluationArtworkRepository.deleteByEvaluationId(project.getId());
        projectExpertRepository.deleteByEvaluationId(project.getId());
        metricRepository.deleteByEvaluationId(project.getId());

        List<Artwork> artworks = artworkRepository.findByIdInOrderByIdAsc(artworkIds);
        Map<Long, Artwork> artworkMap = artworks.stream().collect(Collectors.toMap(Artwork::getId, Function.identity()));
        if (artworkMap.size() != new LinkedHashSet<>(artworkIds).size()) {
            throw new IllegalArgumentException("所选艺术品中存在无效数据");
        }
        List<AuthUser> experts = authUserRepository.findAllById(new LinkedHashSet<>(expertIds));
        Map<Long, AuthUser> expertMap = experts.stream().collect(Collectors.toMap(AuthUser::getId, Function.identity()));
        if (expertMap.size() != new LinkedHashSet<>(expertIds).size()) {
            throw new IllegalArgumentException("所选专家中存在无效账号");
        }
        for (AuthUser expert : experts) {
            if (expert.getStatus() != UserStatus.ENABLED) {
                throw new IllegalArgumentException("专家账号已停用: " + expert.getDisplayName());
            }
        }

        List<EvaluationProjectMetric> metricEntities = new ArrayList<>();
        for (MetricConfigRequest metric : metrics) {
            EvaluationProjectMetric entity = new EvaluationProjectMetric();
            entity.setEvaluationId(project.getId());
            entity.setSourceMetricDefinitionId(metric.sourceMetricDefinitionId());
            entity.setSourceTemplateId(metric.sourceTemplateId());
            entity.setSourceVersion(metric.sourceVersion());
            entity.setCode(metric.code().trim());
            entity.setName(metric.name().trim());
            entity.setDescription(blankToNull(metric.description()));
            entity.setCategory(blankToNull(metric.category()));
            entity.setScoreType(blankToNull(metric.scoreType()));
            entity.setMinScore(metric.minScore());
            entity.setMaxScore(metric.maxScore());
            entity.setScoreStep(metric.scoreStep());
            entity.setWeight(metric.weight());
            entity.setRequired(Boolean.TRUE.equals(metric.required()));
            entity.setInputComponent(blankToNull(metric.inputComponent()));
            entity.setOptionValues(blankToNull(metric.optionValues()));
            entity.setScoringGuide(blankToNull(metric.scoringGuide()));
            entity.setScoringRubric(blankToNull(metric.scoringRubric()));
            entity.setSortOrder(metric.sortOrder() == null ? metricEntities.size() + 1 : metric.sortOrder());
            if (entity.getSourceMetricDefinitionId() != null) {
                metricDefinitionRepository.findById(entity.getSourceMetricDefinitionId())
                        .ifPresent(definition -> entity.setSourceVersion(definition.getVersion()));
            }
            metricEntities.add(entity);
        }
        metricRepository.saveAll(metricEntities);

        List<EvaluationArtwork> evaluationArtworks = new ArrayList<>();
        for (Long artworkId : artworkIds) {
            EvaluationArtwork item = new EvaluationArtwork();
            item.setEvaluationId(project.getId());
            item.setArtworkId(artworkId);
            item.setStatus(ExpertReviewStatus.NOT_STARTED.name());
            item.setReviewPageGenerated(true);
            item.setReviewPageGeneratedAt(LocalDateTime.now());
            evaluationArtworks.add(item);
        }
        evaluationArtworkRepository.saveAll(evaluationArtworks);

        List<EvaluationProjectExpert> projectExperts = new ArrayList<>();
        for (Long expertId : new LinkedHashSet<>(expertIds)) {
            AuthUser expert = expertMap.get(expertId);
            EvaluationProjectExpert item = new EvaluationProjectExpert();
            item.setEvaluationId(project.getId());
            item.setExpertId(expertId);
            item.setExpertName(expert.getDisplayName());
            item.setStatus(ExpertReviewStatus.NOT_STARTED.name());
            item.setTotalCount(artworkIds.size());
            projectExperts.add(item);
        }
        projectExpertRepository.saveAll(projectExperts);

        List<ExpertReview> reviews = new ArrayList<>();
        for (Long artworkId : artworkIds) {
            for (Long expertId : new LinkedHashSet<>(expertIds)) {
                AuthUser expert = expertMap.get(expertId);
                ExpertReview review = new ExpertReview();
                review.setEvaluationId(project.getId());
                review.setArtworkId(artworkId);
                review.setExpertId(expertId);
                review.setExpertName(expert.getDisplayName());
                review.setStatus(ExpertReviewStatus.NOT_STARTED);
                reviews.add(review);
            }
        }
        expertReviewRepository.saveAll(reviews);
    }

    private void refreshExpertCounters(Long evaluationId, List<ExpertReview> reviews) {
        Map<Long, List<ExpertReview>> reviewMap = reviews.stream().collect(Collectors.groupingBy(ExpertReview::getExpertId));
        List<EvaluationProjectExpert> experts = projectExpertRepository.findByEvaluationIdOrderByExpertNameAscIdAsc(evaluationId);
        for (EvaluationProjectExpert item : experts) {
            List<ExpertReview> own = reviewMap.getOrDefault(item.getExpertId(), List.of());
            item.setTotalCount(own.size());
            item.setCompletedCount((int) own.stream().filter(r -> COMPLETED_REVIEW_STATUSES.contains(r.getStatus())).count());
            item.setRejectedCount((int) own.stream().filter(r -> r.getStatus() == ExpertReviewStatus.REVIEW_REJECTED).count());
            item.setStatus(item.getRejectedCount() > 0
                    ? ExpertReviewStatus.REVIEW_REJECTED.name()
                    : item.getCompletedCount() == item.getTotalCount() && item.getTotalCount() > 0
                    ? ExpertReviewStatus.SUBMITTED.name()
                    : own.stream().anyMatch(r -> r.getStatus() != ExpertReviewStatus.NOT_STARTED)
                    ? ExpertReviewStatus.DRAFT.name()
                    : ExpertReviewStatus.NOT_STARTED.name());
        }
        projectExpertRepository.saveAll(experts);
    }

    private void refreshArtworkStatuses(Long evaluationId, List<ExpertReview> reviews) {
        Map<Long, List<ExpertReview>> reviewMap = reviews.stream().collect(Collectors.groupingBy(ExpertReview::getArtworkId));
        List<EvaluationArtwork> artworks = evaluationArtworkRepository.findByEvaluationIdOrderByIdAsc(evaluationId);
        for (EvaluationArtwork item : artworks) {
            List<ExpertReview> own = reviewMap.getOrDefault(item.getArtworkId(), List.of());
            item.setStatus(own.stream().anyMatch(r -> r.getStatus() == ExpertReviewStatus.REVIEW_REJECTED)
                    ? ExpertReviewStatus.REVIEW_REJECTED.name()
                    : own.stream().allMatch(r -> COMPLETED_REVIEW_STATUSES.contains(r.getStatus())) && !own.isEmpty()
                    ? ExpertReviewStatus.SUBMITTED.name()
                    : own.stream().anyMatch(r -> r.getStatus() != ExpertReviewStatus.NOT_STARTED)
                    ? ExpertReviewStatus.DRAFT.name()
                    : ExpertReviewStatus.NOT_STARTED.name());
        }
        evaluationArtworkRepository.saveAll(artworks);
    }

    private List<EvaluationArtworkItemDto> loadArtworkItems(Long evaluationId) {
        List<EvaluationArtwork> items = evaluationArtworkRepository.findByEvaluationIdOrderByIdAsc(evaluationId);
        List<Long> artworkIds = items.stream().map(EvaluationArtwork::getArtworkId).toList();
        Map<Long, ArtworkDto> artworks = artworkRepository.findByIdInOrderByIdAsc(artworkIds).stream()
                .map(ArtworkDto::from)
                .collect(Collectors.toMap(ArtworkDto::getId, Function.identity()));
        return items.stream()
                .map(item -> EvaluationArtworkItemDto.from(item, artworks.get(item.getArtworkId())))
                .toList();
    }

    private boolean isProjectEditable(EvaluationProject project) {
        return switch (project.getStatus()) {
            case DRAFT, PENDING -> true;
            default -> false;
        };
    }

    private boolean isProjectSubmittedAllowed(EvaluationProject project) {
        return project.getStatus() == EvaluationProjectStatus.READY_FOR_REVIEW
                || project.getStatus() == EvaluationProjectStatus.REVIEW_REJECTED;
    }

    private AuthUser requireAuditorUser(Long userId) {
        AuthUser user = authUserRepository.findWithRolesById(userId)
                .orElseThrow(() -> new IllegalArgumentException("审核人不存在"));
        if (user.getStatus() != UserStatus.ENABLED) {
            throw new IllegalArgumentException("审核人账号已停用");
        }
        return user;
    }

    private String writeCriteria(List<CriterionItemDto> criteria) {
        try {
            return OBJECT_MAPPER.writeValueAsString(criteria == null ? List.of() : criteria);
        } catch (Exception e) {
            throw new IllegalArgumentException("保存评估条件失败");
        }
    }

    private Specification<Artwork> buildArtworkCriteriaSpec(List<CriterionItemDto> criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria != null) {
                for (CriterionItemDto item : criteria) {
                    if (item == null || item.fieldName() == null || item.fieldName().isBlank()) {
                        continue;
                    }
                    String fieldName = item.fieldName().trim();
                    String operator = item.operator() == null ? "contains" : item.operator().trim().toLowerCase(Locale.ROOT);
                    String value = item.value();
                    if ("hdImageAvailable".equals(fieldName)) {
                        if ("equals".equals(operator) && value != null && !value.isBlank()) {
                            boolean hasHdImage = Boolean.parseBoolean(value.trim());
                            Predicate hdImageAvailable = cb.and(
                                    cb.isNotNull(root.get("hdImagePath")),
                                    cb.notEqual(root.get("hdImagePath"), "")
                            );
                            predicates.add(hasHdImage ? hdImageAvailable : cb.not(hdImageAvailable));
                        }
                        continue;
                    }
                    Path<?> path;
                    if ("taskId".equals(fieldName)) {
                        path = root.get("task").get("id");
                    } else {
                        path = root.get(fieldName);
                    }
                    switch (operator) {
                        case "equals" -> {
                            if (value != null && !value.isBlank()) {
                                if ("taskId".equals(fieldName)) {
                                    predicates.add(cb.equal(path.as(Long.class), Long.valueOf(value.trim())));
                                } else {
                                    predicates.add(cb.equal(cb.lower(path.as(String.class)), value.trim().toLowerCase(Locale.ROOT)));
                                }
                            }
                        }
                        case "year" -> {
                            if (value != null && !value.isBlank()) {
                                predicates.add(cb.like(path.as(String.class), "%" + value.trim() + "%"));
                            }
                        }
                        case "notempty" -> predicates.add(cb.and(cb.isNotNull(path.as(String.class)), cb.notEqual(path.as(String.class), "")));
                        case "between", "daterange" -> {
                            if (value != null && !value.isBlank() && item.valueTo() != null && !item.valueTo().isBlank()) {
                                predicates.add(cb.between(path.as(String.class), value.trim(), item.valueTo().trim()));
                            }
                        }
                        default -> {
                            if (value != null && !value.isBlank()) {
                                predicates.add(cb.like(cb.lower(path.as(String.class)), "%" + value.trim().toLowerCase(Locale.ROOT) + "%"));
                            }
                        }
                    }
                }
            }
            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private PageResult<EvaluationProjectListItemDto> emptyPage(int page, int size) {
        PageResult<EvaluationProjectListItemDto> result = new PageResult<>();
        result.setItems(List.of());
        result.setPage(page);
        result.setSize(size);
        result.setTotal(0);
        result.setTotalPages(0);
        return result;
    }
}
