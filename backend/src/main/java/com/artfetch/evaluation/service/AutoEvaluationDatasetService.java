package com.artfetch.evaluation.service;

import com.artfetch.auth.dto.CurrentUserDto;
import com.artfetch.auth.service.AuditLogService;
import com.artfetch.auth.service.CurrentUserService;
import com.artfetch.auth.service.DataScopeService;
import com.artfetch.auth.support.PermissionCodes;
import com.artfetch.config.AppProperties;
import com.artfetch.dto.PageResult;
import com.artfetch.entity.Artwork;
import com.artfetch.entity.ObjectStorageConfig;
import com.artfetch.evaluation.dto.*;
import com.artfetch.evaluation.entity.*;
import com.artfetch.evaluation.repository.*;
import com.artfetch.repository.ArtworkRepository;
import com.artfetch.service.HdImageObjectStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class AutoEvaluationDatasetService {

    private static final Set<ExpertReviewStatus> COMPLETED_REVIEW_STATUSES =
            EnumSet.of(ExpertReviewStatus.SUBMITTED, ExpertReviewStatus.RESUBMITTED);
    private static final String VALUATION_LOG_FIELD = "valuation_log";

    private final AutoEvaluationDatasetRepository datasetRepository;
    private final AutoEvaluationDatasetArtworkRepository selectionRepository;
    private final EvaluationProjectRepository projectRepository;
    private final EvaluationArtworkRepository evaluationArtworkRepository;
    private final EvaluationProjectExpertRepository projectExpertRepository;
    private final EvaluationProjectMetricRepository metricRepository;
    private final EvaluationMetricTemplateRepository templateRepository;
    private final ExpertReviewRepository reviewRepository;
    private final ExpertReviewScoreRepository scoreRepository;
    private final ArtworkRepository artworkRepository;
    private final EvaluationAccessService evaluationAccessService;
    private final CurrentUserService currentUserService;
    private final DataScopeService dataScopeService;
    private final AuditLogService auditLogService;
    private final AppProperties appProperties;
    private final HdImageObjectStorageService objectStorageService;
    private final ExecutorService taskExecutor;
    private final PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public PageResult<AutoEvaluationDatasetDto> list(int page, int size) {
        dataScopeService.requirePermission(PermissionCodes.AUTO_EVALUATION_DATASET_VIEW);
        return PageResult.of(datasetRepository.findByStatusNotOrderByCreatedAtDesc(
                AutoEvaluationDatasetStatus.ARCHIVED, PageRequest.of(page, size)), AutoEvaluationDatasetDto::from);
    }

    @Transactional(readOnly = true)
    public AutoEvaluationDatasetDto get(Long id) {
        AutoEvaluationDataset dataset = requireDataset(id);
        requireDatasetView(dataset);
        return AutoEvaluationDatasetDto.from(dataset);
    }

    @Transactional(readOnly = true)
    public PageResult<AutoEvaluationSourceProjectDto> listSourceProjects(String keyword, int page, int size) {
        dataScopeService.requirePermission(PermissionCodes.AUTO_EVALUATION_DATASET_CREATE);
        String normalized = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<EvaluationProject> candidates = projectRepository.findByStatusInAndDeletedAtIsNull(List.of(EvaluationProjectStatus.COMPLETED)).stream()
                .filter(project -> project.getAuditResult() == EvaluationAuditResult.APPROVED)
                .filter(project -> normalized.isBlank() || project.getName().toLowerCase(Locale.ROOT).contains(normalized))
                .filter(this::hasTrainingTemplateMetrics)
                .filter(project -> canViewProject(project))
                .sorted(Comparator.comparing(EvaluationProject::getCompletedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return page(candidates.stream().map(AutoEvaluationSourceProjectDto::from).toList(), page, size);
    }

    @Transactional
    public AutoEvaluationDatasetDto create(CreateAutoEvaluationDatasetRequest request) {
        dataScopeService.requirePermission(PermissionCodes.AUTO_EVALUATION_DATASET_CREATE);
        EvaluationProject project = requireSourceProject(request.sourceEvaluationId());
        EvaluationMetricTemplate template = resolveSourceTemplate(project.getId()).orElse(null);
        AutoEvaluationAggregationStrategy strategy = parseStrategy(request.aggregationStrategy());
        EvaluationProjectExpert selectedExpert = resolveSelectedExpert(project.getId(), strategy, request.selectedExpertId());
        CurrentUserDto currentUser = currentUserService.currentUser();

        AutoEvaluationDataset dataset = new AutoEvaluationDataset();
        dataset.setName(request.name().trim());
        dataset.setSourceEvaluationId(project.getId());
        dataset.setSourceEvaluationName(project.getName());
        if (template != null) {
            dataset.setTemplateId(template.getId());
            dataset.setTemplateCode(template.getCode());
        }
        dataset.setAggregationStrategy(strategy);
        if (selectedExpert != null) {
            dataset.setSelectedExpertId(selectedExpert.getExpertId());
            dataset.setSelectedExpertName(selectedExpert.getExpertName());
        }
        dataset.setStatus(AutoEvaluationDatasetStatus.DRAFT);
        dataset.setCreatedBy(currentUser.id());
        dataset.setCreatedByName(currentUser.displayName());
        AutoEvaluationDataset saved = datasetRepository.save(dataset);
        refreshSelectionStats(saved);
        auditLogService.recordSuccess("auto-evaluation.dataset.create", "AUTO_EVALUATION_DATASET",
                String.valueOf(saved.getId()), "创建训练数据集草稿 " + saved.getName());
        return AutoEvaluationDatasetDto.from(saved);
    }

    @Transactional
    public AutoEvaluationDatasetDto update(Long id, UpdateAutoEvaluationDatasetRequest request) {
        dataScopeService.requirePermission(PermissionCodes.AUTO_EVALUATION_DATASET_CREATE);
        AutoEvaluationDataset dataset = requireDataset(id);
        requireEditable(dataset);
        requireDatasetView(dataset);
        EvaluationProject project = requireSourceProject(dataset.getSourceEvaluationId());
        AutoEvaluationAggregationStrategy strategy = parseStrategy(request.aggregationStrategy());
        EvaluationProjectExpert selectedExpert = resolveSelectedExpert(project.getId(), strategy, request.selectedExpertId());
        dataset.setName(request.name().trim());
        dataset.setAggregationStrategy(strategy);
        dataset.setSelectedExpertId(selectedExpert == null ? null : selectedExpert.getExpertId());
        dataset.setSelectedExpertName(selectedExpert == null ? null : selectedExpert.getExpertName());
        dataset.setErrorMessage(null);
        refreshSelectionStats(dataset);
        return AutoEvaluationDatasetDto.from(datasetRepository.save(dataset));
    }

    @Transactional(readOnly = true)
    public PageResult<AutoEvaluationArtworkCandidateDto> listCandidateArtworks(Long datasetId,
                                                                               String keyword,
                                                                               Boolean selectedOnly,
                                                                               int page,
                                                                               int size) {
        AutoEvaluationDataset dataset = requireDataset(datasetId);
        requireDatasetView(dataset);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<EvaluationArtwork> evaluationArtworks = normalizedKeyword.isBlank()
                ? evaluationArtworkRepository.findByEvaluationIdOrderByIdAsc(dataset.getSourceEvaluationId(), pageRequest)
                : evaluationArtworkRepository.searchByEvaluationId(dataset.getSourceEvaluationId(), normalizedKeyword, pageRequest);
        List<Long> artworkIds = evaluationArtworks.getContent().stream().map(EvaluationArtwork::getArtworkId).toList();
        Map<Long, Artwork> artworks = artworkRepository.findByIdInOrderByIdAsc(artworkIds).stream()
                .collect(Collectors.toMap(Artwork::getId, Function.identity()));
        Set<Long> selectedIds = selectionRepository.findByDatasetIdAndArtworkIdIn(datasetId, artworkIds).stream()
                .map(AutoEvaluationDatasetArtwork::getArtworkId)
                .collect(Collectors.toSet());
        List<AutoEvaluationArtworkCandidateDto> items = evaluationArtworks.getContent().stream()
                .map(item -> toCandidateDto(artworks.get(item.getArtworkId()), selectedIds.contains(item.getArtworkId())))
                .filter(Objects::nonNull)
                .filter(item -> selectedOnly == null || !selectedOnly || item.selected())
                .toList();
        PageResult<AutoEvaluationArtworkCandidateDto> result = new PageResult<>();
        result.setItems(items);
        result.setTotal(evaluationArtworks.getTotalElements());
        result.setPage(evaluationArtworks.getNumber());
        result.setSize(evaluationArtworks.getSize());
        result.setTotalPages(evaluationArtworks.getTotalPages());
        return result;
    }

    @Transactional
    public AutoEvaluationDatasetDto updateSelection(Long datasetId, UpdateDatasetArtworkSelectionRequest request) {
        dataScopeService.requirePermission(PermissionCodes.AUTO_EVALUATION_DATASET_CREATE);
        AutoEvaluationDataset dataset = requireDataset(datasetId);
        requireEditable(dataset);
        requireDatasetView(dataset);
        List<Long> artworkIds = request.artworkIds() == null ? List.of() : request.artworkIds().stream().distinct().toList();
        if (artworkIds.isEmpty()) {
            return AutoEvaluationDatasetDto.from(dataset);
        }
        Set<Long> validArtworkIds = evaluationArtworkRepository
                .findByEvaluationIdAndArtworkIdInOrderByIdAsc(dataset.getSourceEvaluationId(), artworkIds).stream()
                .map(EvaluationArtwork::getArtworkId)
                .collect(Collectors.toSet());
        if (validArtworkIds.size() != artworkIds.size()) {
            throw new IllegalArgumentException("选择中包含不属于来源评估项目的作品");
        }
        if (request.selected()) {
            for (Long artworkId : artworkIds) {
                if (!selectionRepository.existsByDatasetIdAndArtworkId(datasetId, artworkId)) {
                    AutoEvaluationDatasetArtwork selected = new AutoEvaluationDatasetArtwork();
                    selected.setDatasetId(datasetId);
                    selected.setArtworkId(artworkId);
                    selectionRepository.save(selected);
                }
            }
        } else {
            for (Long artworkId : artworkIds) {
                selectionRepository.deleteByDatasetIdAndArtworkId(datasetId, artworkId);
            }
        }
        refreshSelectionStats(dataset);
        return AutoEvaluationDatasetDto.from(datasetRepository.save(dataset));
    }

    @Transactional
    public AutoEvaluationDatasetDto clearSelection(Long datasetId) {
        dataScopeService.requirePermission(PermissionCodes.AUTO_EVALUATION_DATASET_CREATE);
        AutoEvaluationDataset dataset = requireDataset(datasetId);
        requireEditable(dataset);
        requireDatasetView(dataset);
        selectionRepository.deleteByDatasetId(datasetId);
        refreshSelectionStats(dataset);
        return AutoEvaluationDatasetDto.from(datasetRepository.save(dataset));
    }

    @Transactional(readOnly = true)
    public CheckAutoEvaluationDatasetResponse checkSelected(Long datasetId) {
        AutoEvaluationDataset dataset = requireDataset(datasetId);
        requireDatasetView(dataset);
        CheckResult result = buildCheckResult(dataset);
        return result.response();
    }

    @Transactional
    public AutoEvaluationDatasetDto startGeneration(Long datasetId) {
        dataScopeService.requirePermission(PermissionCodes.AUTO_EVALUATION_DATASET_CREATE);
        AutoEvaluationDataset dataset = requireDataset(datasetId);
        requireEditable(dataset);
        requireDatasetView(dataset);
        CheckResult check = buildCheckResult(dataset);
        if (check.samples().isEmpty()) {
            throw new IllegalStateException("没有可生成的训练样本");
        }
        if (check.response().exceedsMobileHardLimit()) {
            throw new IllegalStateException("所选图片预计超过训练包硬上限，请减少样本数量");
        }
        dataset.setStatus(AutoEvaluationDatasetStatus.GENERATING);
        dataset.setErrorMessage(null);
        dataset.setSampleCount(check.samples().size());
        dataset.setSkippedCount(check.skipped().size());
        datasetRepository.save(dataset);

        taskExecutor.submit(() -> generatePackage(datasetId));
        auditLogService.recordSuccess("auto-evaluation.dataset.generate.start", "AUTO_EVALUATION_DATASET",
                String.valueOf(datasetId), "启动训练数据集生成任务");
        return AutoEvaluationDatasetDto.from(dataset);
    }

    @Transactional
    public void delete(Long datasetId) {
        dataScopeService.requirePermission(PermissionCodes.AUTO_EVALUATION_DATASET_CREATE);
        AutoEvaluationDataset dataset = requireDataset(datasetId);
        requireDatasetView(dataset);
        if (dataset.getStatus() != AutoEvaluationDatasetStatus.DRAFT && dataset.getStatus() != AutoEvaluationDatasetStatus.FAILED) {
            throw new IllegalStateException("只有草稿或失败的数据集可以删除");
        }
        selectionRepository.deleteByDatasetId(datasetId);
        datasetRepository.delete(dataset);
        auditLogService.recordSuccess("auto-evaluation.dataset.delete", "AUTO_EVALUATION_DATASET",
                String.valueOf(datasetId), "删除训练数据集 " + dataset.getName());
    }

    @Transactional
    public AutoEvaluationDatasetDto archive(Long datasetId) {
        dataScopeService.requirePermission(PermissionCodes.AUTO_EVALUATION_DATASET_CREATE);
        AutoEvaluationDataset dataset = requireDataset(datasetId);
        requireDatasetView(dataset);
        if (dataset.getStatus() != AutoEvaluationDatasetStatus.READY) {
            throw new IllegalStateException("只有已生成的数据集可以归档");
        }
        dataset.setStatus(AutoEvaluationDatasetStatus.ARCHIVED);
        dataset.setArchivedAt(LocalDateTime.now());
        auditLogService.recordSuccess("auto-evaluation.dataset.archive", "AUTO_EVALUATION_DATASET",
                String.valueOf(datasetId), "归档训练数据集 " + dataset.getName());
        return AutoEvaluationDatasetDto.from(datasetRepository.save(dataset));
    }

    @Transactional
    public Resource downloadZip(Long datasetId) {
        dataScopeService.requirePermission(PermissionCodes.AUTO_EVALUATION_DATASET_EXPORT);
        AutoEvaluationDataset dataset = requireDataset(datasetId);
        requireDatasetView(dataset);
        if (dataset.getStatus() != AutoEvaluationDatasetStatus.READY || dataset.getZipFilePath() == null) {
            throw new IllegalStateException("训练数据集尚未生成");
        }
        Path zip = Paths.get(dataset.getZipFilePath());
        if (!Files.exists(zip)) {
            throw new IllegalStateException("训练数据包文件不存在");
        }
        auditLogService.recordSuccess("auto-evaluation.dataset.download", "AUTO_EVALUATION_DATASET",
                String.valueOf(datasetId),
                "下载训练数据集 " + dataset.getName() + ", size=" + dataset.getZipFileSize() + ", sha256=" + dataset.getZipSha256());
        return new FileSystemResource(zip);
    }

    public String zipFilename(Long datasetId) {
        return "artfetch-training-dataset-" + datasetId + ".zip";
    }

    private void generatePackage(Long datasetId) {
        try {
            AutoEvaluationDataset dataset = datasetRepository.findById(datasetId).orElseThrow();
            CheckResult check = buildCheckResult(dataset, false);
            Path root = Paths.get(appProperties.getAutoEvaluation().getDatasetStoragePath()).toAbsolutePath().normalize();
            Path datasetDir = root.resolve("dataset-" + datasetId);
            Path workingDir = root.resolve("working-" + datasetId + "-" + System.currentTimeMillis());
            Path imagesDir = workingDir.resolve("images");
            Files.createDirectories(imagesDir);

            List<Map<String, Object>> annotations = new ArrayList<>();
            List<Map<String, Object>> manifestSamples = new ArrayList<>();
            for (SampleBuild sample : check.samples()) {
                String extension = extensionFor(sample.artwork());
                String imagePath = "images/artwork-" + sample.artwork().getId() + extension;
                Path target = workingDir.resolve(imagePath);
                copyTrainingImage(sample.artwork(), target);
                annotations.add(Map.of(
                        "image_path", imagePath,
                        "features", sample.features()
                ));
                manifestSamples.add(sampleManifest(sample, imagePath));
            }

            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("datasetId", dataset.getId());
            manifest.put("datasetName", dataset.getName());
            manifest.put("generatedAt", LocalDateTime.now().toString());
            manifest.put("sourceEvaluationId", dataset.getSourceEvaluationId());
            manifest.put("sourceEvaluationName", dataset.getSourceEvaluationName());
            manifest.put("templateCode", dataset.getTemplateCode());
            manifest.put("aggregationStrategy", dataset.getAggregationStrategy().name());
            manifest.put("selectedExpertId", dataset.getSelectedExpertId());
            manifest.put("valuationFormula", "ln(CNY_amount)");
            manifest.put("sampleCount", check.samples().size());
            manifest.put("skippedCount", check.skipped().size());
            manifest.put("excludedByUserCount", dataset.getExcludedByUserCount());
            manifest.put("samples", manifestSamples);

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(workingDir.resolve("annotations.json").toFile(), annotations);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(workingDir.resolve("manifest.json").toFile(), manifest);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(workingDir.resolve("skipped-samples.json").toFile(), check.skipped());

            Path zip = workingDir.resolve(zipFilename(datasetId));
            zipDirectory(workingDir, zip);
            String sha256 = sha256(zip);
            long zipSize = Files.size(zip);

            deleteRecursively(datasetDir);
            Files.createDirectories(root);
            Files.move(workingDir, datasetDir, StandardCopyOption.ATOMIC_MOVE);
            Path finalZip = datasetDir.resolve(zip.getFileName());
            updateGenerationSuccess(datasetId, datasetDir, finalZip, zipSize, sha256, check.samples().size(), check.skipped().size());
        } catch (Exception e) {
            updateGenerationFailure(datasetId, e);
        }
    }

    private void updateGenerationSuccess(Long datasetId, Path datasetDir, Path finalZip, long zipSize, String sha256, int sampleCount, int skippedCount) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            AutoEvaluationDataset dataset = datasetRepository.findById(datasetId).orElseThrow();
            dataset.setStatus(AutoEvaluationDatasetStatus.READY);
            dataset.setStoragePath(datasetDir.toString());
            dataset.setZipFilePath(finalZip.toString());
            dataset.setZipFileSize(zipSize);
            dataset.setZipSha256(sha256);
            dataset.setSampleCount(sampleCount);
            dataset.setSkippedCount(skippedCount);
            dataset.setGeneratedAt(LocalDateTime.now());
            dataset.setErrorMessage(null);
            datasetRepository.save(dataset);
        });
    }

    private void updateGenerationFailure(Long datasetId, Exception e) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            AutoEvaluationDataset dataset = datasetRepository.findById(datasetId).orElseThrow();
            dataset.setStatus(AutoEvaluationDatasetStatus.FAILED);
            dataset.setErrorMessage(e.getMessage());
            datasetRepository.save(dataset);
        });
    }

    private CheckResult buildCheckResult(AutoEvaluationDataset dataset) {
        return buildCheckResult(dataset, true);
    }

    private CheckResult buildCheckResult(AutoEvaluationDataset dataset, boolean enforceAccess) {
        requireSourceProject(dataset.getSourceEvaluationId(), enforceAccess);
        List<Long> selectedArtworkIds = selectionRepository.findByDatasetIdOrderByIdAsc(dataset.getId()).stream()
                .map(AutoEvaluationDatasetArtwork::getArtworkId)
                .toList();
        Map<Long, Artwork> artworks = artworkRepository.findByIdInOrderByIdAsc(selectedArtworkIds).stream()
                .collect(Collectors.toMap(Artwork::getId, Function.identity()));
        Map<Long, List<ExpertReview>> reviewsByArtwork = reviewRepository.findByEvaluationIdOrderByArtworkIdAscExpertNameAsc(dataset.getSourceEvaluationId()).stream()
                .filter(review -> selectedArtworkIds.contains(review.getArtworkId()))
                .collect(Collectors.groupingBy(ExpertReview::getArtworkId));
        List<Long> reviewIds = reviewsByArtwork.values().stream().flatMap(List::stream).map(ExpertReview::getId).toList();
        Map<Long, List<ExpertReviewScore>> scoresByReview = reviewIds.isEmpty()
                ? Map.of()
                : scoreRepository.findByReviewIdIn(reviewIds).stream().collect(Collectors.groupingBy(ExpertReviewScore::getReviewId));
        List<EvaluationProjectMetric> trainingMetrics = exportableMetrics(dataset.getSourceEvaluationId());

        List<SampleBuild> samples = new ArrayList<>();
        List<AutoEvaluationDatasetSkippedSampleDto> skipped = new ArrayList<>();
        for (Long artworkId : selectedArtworkIds) {
            Artwork artwork = artworks.get(artworkId);
            if (artwork == null) {
                continue;
            }
            SampleOrSkip evaluated = evaluateSample(dataset, artwork, reviewsByArtwork.getOrDefault(artworkId, List.of()), scoresByReview, trainingMetrics);
            if (evaluated.sample() != null) {
                samples.add(evaluated.sample());
            } else {
                skipped.add(evaluated.skipped());
            }
        }
        long estimatedSize = samples.stream().mapToLong(sample -> imageSize(sample.artwork())).sum();
        boolean soft = estimatedSize > appProperties.getAutoEvaluation().getDatasetMobileSoftSizeLimitMb() * 1024L * 1024L;
        boolean hard = estimatedSize > appProperties.getAutoEvaluation().getDatasetMobileHardSizeLimitMb() * 1024L * 1024L;
        CheckAutoEvaluationDatasetResponse response = new CheckAutoEvaluationDatasetResponse(
                dataset.getId(),
                selectedArtworkIds.size(),
                samples.size(),
                skipped.size(),
                estimatedSize,
                soft,
                hard,
                samples.stream().map(this::toSamplePreview).toList(),
                skipped
        );
        return new CheckResult(samples, skipped, response);
    }

    private SampleOrSkip evaluateSample(AutoEvaluationDataset dataset,
                                        Artwork artwork,
                                        List<ExpertReview> reviews,
                                        Map<Long, List<ExpertReviewScore>> scoresByReview,
                                        List<EvaluationProjectMetric> trainingMetrics) {
        List<String> reasons = new ArrayList<>();
        List<String> missingMetricCodes = new ArrayList<>();
        if (imageSourceType(artwork).equals("NONE")) {
            reasons.add("MISSING_TRAINING_IMAGE");
        }
        List<ExpertReview> eligibleReviews = reviews.stream()
                .filter(review -> COMPLETED_REVIEW_STATUSES.contains(review.getStatus()))
                .filter(review -> dataset.getAggregationStrategy() == AutoEvaluationAggregationStrategy.AVERAGE_ALL_EXPERTS
                        || Objects.equals(review.getExpertId(), dataset.getSelectedExpertId()))
                .toList();
        if (eligibleReviews.isEmpty()) {
            reasons.add(dataset.getAggregationStrategy() == AutoEvaluationAggregationStrategy.SELECTED_EXPERT
                    ? "MISSING_SELECTED_EXPERT_REVIEW"
                    : "MISSING_COMPLETED_EXPERT_REVIEW");
        }
        List<BigDecimal> amounts = eligibleReviews.stream()
                .map(ExpertReview::getFinalEstimateAmount)
                .filter(Objects::nonNull)
                .filter(amount -> amount.signum() > 0)
                .toList();
        if (amounts.size() != eligibleReviews.size() || amounts.isEmpty()) {
            reasons.add("MISSING_FINAL_ESTIMATE_AMOUNT");
        }

        Map<String, List<Double>> valuesByExportField = new LinkedHashMap<>();
        for (EvaluationProjectMetric metric : trainingMetrics) {
            List<Double> values = eligibleReviews.stream()
                    .map(review -> scoresByReview.getOrDefault(review.getId(), List.of()).stream()
                            .filter(score -> Objects.equals(score.getProjectMetricId(), metric.getId()))
                            .map(ExpertReviewScore::getScore)
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElse(null))
                    .filter(Objects::nonNull)
                    .toList();
            if (values.size() != eligibleReviews.size() || values.isEmpty()) {
                missingMetricCodes.add(metric.getCode());
            } else {
                valuesByExportField.put(metric.getExportField(), values);
            }
        }
        if (!missingMetricCodes.isEmpty()) {
            reasons.add("MISSING_REQUIRED_METRIC_SCORE");
        }
        if (!reasons.isEmpty()) {
            return new SampleOrSkip(null, new AutoEvaluationDatasetSkippedSampleDto(
                    artwork.getId(),
                    artwork.getTitle(),
                    artwork.getLotNumber(),
                    artwork.getArtist(),
                    reasons,
                    eligibleReviews.stream().map(ExpertReview::getId).toList(),
                    missingMetricCodes,
                    dataset.getSelectedExpertId()
            ));
        }

        Map<String, Double> features = new LinkedHashMap<>();
        for (EvaluationProjectMetric metric : trainingMetrics) {
            features.put(metric.getExportField(), average(valuesByExportField.get(metric.getExportField())));
        }
        BigDecimal averageAmount = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(amounts.size()), 6, java.math.RoundingMode.HALF_UP);
        features.put(VALUATION_LOG_FIELD, amounts.stream()
                .mapToDouble(amount -> Math.log(amount.doubleValue()))
                .average()
                .orElseThrow());
        return new SampleOrSkip(new SampleBuild(artwork, imageSourceType(artwork), eligibleReviews, averageAmount, features), null);
    }

    private EvaluationProject requireSourceProject(Long evaluationId) {
        return requireSourceProject(evaluationId, true);
    }

    private EvaluationProject requireSourceProject(Long evaluationId, boolean enforceAccess) {
        EvaluationProject project = projectRepository.findById(evaluationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "来源评估项目不存在"));
        if (enforceAccess) {
            evaluationAccessService.requireProjectView(project);
        }
        if (project.getDeletedAt() != null
                || project.getStatus() != EvaluationProjectStatus.COMPLETED
                || project.getAuditResult() != EvaluationAuditResult.APPROVED) {
            throw new IllegalStateException("只能从审核通过且已完成的评估项目生成训练数据集");
        }
        validateExportableMetrics(project.getId());
        return project;
    }

    private boolean canViewProject(EvaluationProject project) {
        try {
            evaluationAccessService.requireProjectView(project);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasTrainingTemplateMetrics(EvaluationProject project) {
        try {
            validateExportableMetrics(project.getId());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void validateExportableMetrics(Long evaluationId) {
        exportableMetrics(evaluationId);
    }

    private List<EvaluationProjectMetric> exportableMetrics(Long evaluationId) {
        List<EvaluationProjectMetric> metrics = metricRepository.findByEvaluationIdOrderBySortOrderAscIdAsc(evaluationId).stream()
                .filter(this::isNumericMetric)
                .toList();
        if (metrics.isEmpty()) {
            throw new IllegalStateException("评估项目没有可导出的数值型指标");
        }
        Set<String> exportFields = new LinkedHashSet<>();
        for (EvaluationProjectMetric metric : metrics) {
            String exportField = nullToEmpty(metric.getExportField()).trim();
            if (exportField.isBlank()) {
                throw new IllegalStateException("评估指标缺少导出字段: " + metric.getCode());
            }
            if (VALUATION_LOG_FIELD.equals(exportField)) {
                throw new IllegalStateException("评估指标导出字段不能使用保留字段: " + VALUATION_LOG_FIELD);
            }
            if (!exportFields.add(exportField)) {
                throw new IllegalStateException("评估项目存在重复导出字段: " + exportField);
            }
        }
        return metrics;
    }

    private Optional<EvaluationMetricTemplate> resolveSourceTemplate(Long evaluationId) {
        List<Long> templateIds = exportableMetrics(evaluationId).stream()
                .map(EvaluationProjectMetric::getSourceTemplateId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (templateIds.size() != 1) {
            return Optional.empty();
        }
        return templateRepository.findById(templateIds.get(0));
    }

    private boolean isNumericMetric(EvaluationProjectMetric metric) {
        String scoreType = nullToEmpty(metric.getScoreType());
        String inputComponent = nullToEmpty(metric.getInputComponent());
        return "numeric".equalsIgnoreCase(scoreType)
                || "number".equalsIgnoreCase(scoreType)
                || "input-number".equalsIgnoreCase(inputComponent);
    }

    private EvaluationProjectExpert resolveSelectedExpert(Long evaluationId,
                                                          AutoEvaluationAggregationStrategy strategy,
                                                          Long selectedExpertId) {
        if (strategy == AutoEvaluationAggregationStrategy.AVERAGE_ALL_EXPERTS) {
            return null;
        }
        if (selectedExpertId == null) {
            throw new IllegalArgumentException("指定专家策略必须选择专家");
        }
        return projectExpertRepository.findByEvaluationIdAndExpertId(evaluationId, selectedExpertId)
                .orElseThrow(() -> new IllegalArgumentException("所选专家不属于该评估项目"));
    }

    private AutoEvaluationAggregationStrategy parseStrategy(String value) {
        if (value == null || value.isBlank()) {
            return AutoEvaluationAggregationStrategy.AVERAGE_ALL_EXPERTS;
        }
        return AutoEvaluationAggregationStrategy.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private AutoEvaluationDataset requireDataset(Long id) {
        return datasetRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "训练数据集不存在"));
    }

    private void requireDatasetView(AutoEvaluationDataset dataset) {
        dataScopeService.requirePermission(PermissionCodes.AUTO_EVALUATION_DATASET_VIEW);
        EvaluationProject project = projectRepository.findById(dataset.getSourceEvaluationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "来源评估项目不存在"));
        evaluationAccessService.requireProjectView(project);
    }

    private void requireEditable(AutoEvaluationDataset dataset) {
        if (dataset.getStatus() != AutoEvaluationDatasetStatus.DRAFT && dataset.getStatus() != AutoEvaluationDatasetStatus.FAILED) {
            throw new IllegalStateException("只有草稿或失败的数据集可以编辑");
        }
    }

    private void refreshSelectionStats(AutoEvaluationDataset dataset) {
        List<Long> selectedIds = selectionRepository.findByDatasetIdOrderByIdAsc(dataset.getId()).stream()
                .map(AutoEvaluationDatasetArtwork::getArtworkId)
                .toList();
        dataset.setSelectedCount(selectedIds.size());
        Map<Long, Artwork> artworks = artworkRepository.findByIdInOrderByIdAsc(selectedIds).stream()
                .collect(Collectors.toMap(Artwork::getId, Function.identity()));
        dataset.setEstimatedSelectedImageSize(selectedIds.stream()
                .map(artworks::get)
                .filter(Objects::nonNull)
                .mapToLong(this::imageSize)
                .sum());
        dataset.setExcludedByUserCount(Math.max(0, projectRepository.findById(dataset.getSourceEvaluationId())
                .map(EvaluationProject::getArtworkCount)
                .orElse(0) - selectedIds.size()));
    }

    private AutoEvaluationArtworkCandidateDto toCandidateDto(Artwork artwork, boolean selected) {
        if (artwork == null) {
            return null;
        }
        return new AutoEvaluationArtworkCandidateDto(
                artwork.getId(),
                artwork.getTitle(),
                artwork.getArtist(),
                artwork.getLotNumber(),
                artwork.getImageUrl(),
                artwork.getAuctionDate(),
                imageSourceType(artwork),
                imageSize(artwork),
                selected
        );
    }

    private String imageSourceType(Artwork artwork) {
        if (hasText(artwork.getHdImageObjectKey()) || hasText(artwork.getHdImagePath())) {
            return "HD";
        }
        if (hasText(artwork.getOriginalImagePath())) {
            return "ORIGINAL";
        }
        return "NONE";
    }

    private long imageSize(Artwork artwork) {
        if (hasText(artwork.getHdImageObjectKey()) && artwork.getHdImageObjectSize() != null) {
            return artwork.getHdImageObjectSize();
        }
        if (hasText(artwork.getHdImagePath()) && artwork.getHdImageSize() != null) {
            return artwork.getHdImageSize();
        }
        if (hasText(artwork.getOriginalImagePath()) && artwork.getOriginalImageSize() != null) {
            return artwork.getOriginalImageSize();
        }
        return 0L;
    }

    private AutoEvaluationDatasetSamplePreviewDto toSamplePreview(SampleBuild sample) {
        String imagePath = "images/artwork-" + sample.artwork().getId() + extensionFor(sample.artwork());
        return new AutoEvaluationDatasetSamplePreviewDto(
                sample.artwork().getId(),
                sample.artwork().getTitle(),
                imagePath,
                sample.imageSourceType(),
                imageSize(sample.artwork()),
                sample.reviews().stream().map(ExpertReview::getId).toList(),
                sample.finalEstimateAmount(),
                sample.features()
        );
    }

    private Map<String, Object> sampleManifest(SampleBuild sample, String imagePath) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("artworkId", sample.artwork().getId());
        result.put("imagePath", imagePath);
        result.put("imageSourceType", sample.imageSourceType());
        result.put("sourceImageSize", imageSize(sample.artwork()));
        result.put("sourceImageEtag", sample.artwork().getHdImageObjectEtag());
        result.put("expertReviewIds", sample.reviews().stream().map(ExpertReview::getId).toList());
        result.put("finalEstimateAmountCny", sample.finalEstimateAmount());
        result.put("features", sample.features());
        return result;
    }

    private void copyTrainingImage(Artwork artwork, Path target) throws Exception {
        Files.createDirectories(target.getParent());
        if (hasText(artwork.getHdImageObjectKey())) {
            ObjectStorageConfig config = artwork.getHdImageObjectConfigId() == null
                    ? objectStorageService.activeConfigForRead()
                    : objectStorageService.loadConfig(artwork.getHdImageObjectConfigId());
            try (HdImageObjectStorageService.StoredObject object = objectStorageService.loadObject(config, artwork.getHdImageObjectKey());
                 InputStream inputStream = object.inputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return;
        }
        if (hasText(artwork.getHdImagePath())) {
            Files.copy(resolveStoredImagePath(artwork.getHdImagePath()), target, StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        if (hasText(artwork.getOriginalImagePath())) {
            Files.copy(resolveStoredImagePath(artwork.getOriginalImagePath()), target, StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        throw new IllegalStateException("作品缺少训练图片: " + artwork.getId());
    }

    private Path resolveStoredImagePath(String value) {
        Path path = Paths.get(value);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return Paths.get(appProperties.getImage().getStoragePath()).toAbsolutePath().normalize()
                .resolve(path)
                .normalize();
    }

    private String extensionFor(Artwork artwork) {
        String path = imageSourceType(artwork).equals("HD") && hasText(artwork.getHdImagePath())
                ? artwork.getHdImagePath()
                : artwork.getOriginalImagePath();
        String ext = extensionFromPath(path);
        if (ext != null) {
            return ext;
        }
        String contentType = imageSourceType(artwork).equals("HD") ? artwork.getHdImageContentType() : artwork.getOriginalImageContentType();
        if (contentType != null) {
            if (contentType.contains("png")) return ".png";
            if (contentType.contains("webp")) return ".webp";
            if (contentType.contains("jpeg") || contentType.contains("jpg")) return ".jpg";
        }
        return ".jpg";
    }

    private String extensionFromPath(String value) {
        if (!hasText(value)) {
            return null;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        for (String ext : List.of(".jpeg", ".jpg", ".png", ".webp")) {
            if (lower.endsWith(ext)) {
                return ext;
            }
        }
        return null;
    }

    private void zipDirectory(Path directory, Path zip) throws Exception {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) {
                    if (path.equals(zip)) {
                        continue;
                    }
                    ZipEntry entry = new ZipEntry(directory.relativize(path).toString().replace("\\", "/"));
                    output.putNextEntry(entry);
                    Files.copy(path, output);
                    output.closeEntry();
                }
            }
        }
    }

    private String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream inputStream = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void deleteRecursively(Path path) throws Exception {
        if (!Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            for (Path item : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(item);
            }
        }
    }

    private double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private <T> PageResult<T> page(List<T> items, int requestedPage, int requestedSize) {
        int page = Math.max(0, requestedPage);
        int size = Math.max(1, Math.min(100, requestedSize));
        int start = Math.min(items.size(), page * size);
        int end = Math.min(items.size(), start + size);
        PageResult<T> result = new PageResult<>();
        result.setItems(items.subList(start, end));
        result.setTotal(items.size());
        result.setPage(page);
        result.setSize(size);
        result.setTotalPages((items.size() + size - 1) / size);
        return result;
    }

    private record CheckResult(List<SampleBuild> samples,
                               List<AutoEvaluationDatasetSkippedSampleDto> skipped,
                               CheckAutoEvaluationDatasetResponse response) {
    }

    private record SampleOrSkip(SampleBuild sample, AutoEvaluationDatasetSkippedSampleDto skipped) {
    }

    private record SampleBuild(Artwork artwork,
                               String imageSourceType,
                               List<ExpertReview> reviews,
                               BigDecimal finalEstimateAmount,
                               Map<String, Double> features) {
    }
}
