package com.artfetch.evaluation.service;

import com.artfetch.auth.service.CurrentUserService;
import com.artfetch.dto.PageResult;
import com.artfetch.evaluation.dto.CreateEvaluationMetricDefinitionRequest;
import com.artfetch.evaluation.dto.EvaluationMetricDefinitionDto;
import com.artfetch.evaluation.dto.UpdateEvaluationMetricDefinitionRequest;
import com.artfetch.evaluation.entity.EvaluationMetricDefinition;
import com.artfetch.evaluation.repository.EvaluationMetricDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class EvaluationMetricDefinitionService {
    private static final Pattern EXPORT_FIELD_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");

    private final EvaluationMetricDefinitionRepository repository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public PageResult<EvaluationMetricDefinitionDto> list(String keyword, int page, int size) {
        if (keyword == null || keyword.isBlank()) {
            return PageResult.of(repository.findAll(PageRequest.of(page, size)), EvaluationMetricDefinitionDto::from);
        }
        return PageResult.of(
                repository.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCaseOrderBySortOrderAscIdAsc(
                        keyword.trim(), keyword.trim(), PageRequest.of(page, size)),
                EvaluationMetricDefinitionDto::from
        );
    }

    @Transactional(readOnly = true)
    public List<EvaluationMetricDefinitionDto> listEnabled() {
        return repository.findAllByEnabledTrueOrderBySortOrderAscIdAsc().stream()
                .map(EvaluationMetricDefinitionDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public EvaluationMetricDefinitionDto get(Long id) {
        return EvaluationMetricDefinitionDto.from(requireEntity(id));
    }

    @Transactional
    public EvaluationMetricDefinitionDto create(CreateEvaluationMetricDefinitionRequest request) {
        String code = request.code().trim();
        if (repository.findByCode(code).isPresent()) {
            throw new IllegalArgumentException("指标编码已存在");
        }
        String exportField = normalizeExportField(request.exportField());
        ensureExportFieldUnique(exportField, null);
        EvaluationMetricDefinition item = new EvaluationMetricDefinition();
        item.setCode(code);
        item.setCreatedBy(currentUserService.currentUser().displayName());
        apply(item, exportField, request.name(), request.description(), request.category(), request.applicableArtworkTypes(),
                request.scoreType(), request.minScore(), request.maxScore(), request.scoreStep(), request.defaultWeight(),
                request.required(), request.inputComponent(), request.optionValues(), request.scoringGuide(), request.scoringRubric(), request.unit(),
                request.tags(), true, request.sortOrder(), false);
        return EvaluationMetricDefinitionDto.from(repository.save(item));
    }

    @Transactional
    public EvaluationMetricDefinitionDto update(Long id, UpdateEvaluationMetricDefinitionRequest request) {
        EvaluationMetricDefinition item = requireEntity(id);
        if (item.isBuiltIn()) {
            throw new IllegalStateException("系统内置评估指标不能编辑");
        }
        String exportField = normalizeExportField(request.exportField());
        ensureExportFieldUnique(exportField, id);
        apply(item, exportField, request.name(), request.description(), request.category(), request.applicableArtworkTypes(),
                request.scoreType(), request.minScore(), request.maxScore(), request.scoreStep(), request.defaultWeight(),
                request.required(), request.inputComponent(), request.optionValues(), request.scoringGuide(), request.scoringRubric(), request.unit(),
                request.tags(), request.enabled(), request.sortOrder(), true);
        return EvaluationMetricDefinitionDto.from(repository.save(item));
    }

    @Transactional
    public void delete(Long id) {
        EvaluationMetricDefinition item = requireEntity(id);
        if (item.isBuiltIn()) {
            throw new IllegalStateException("系统内置评估指标不能删除");
        }
        repository.delete(item);
    }

    private EvaluationMetricDefinition requireEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "评估指标不存在"));
    }

    private void apply(EvaluationMetricDefinition item,
                       String exportField,
                       String name,
                       String description,
                       String category,
                       String applicableArtworkTypes,
                       String scoreType,
                       Double minScore,
                       Double maxScore,
                       Double scoreStep,
                       Double defaultWeight,
                       Boolean required,
                       String inputComponent,
                       String optionValues,
                       String scoringGuide,
                       String scoringRubric,
                       String unit,
                       String tags,
                       Boolean enabled,
                       Integer sortOrder,
                       boolean bumpVersion) {
        item.setExportField(exportField);
        item.setName(name.trim());
        item.setDescription(blankToNull(description));
        item.setCategory(blankToNull(category));
        item.setApplicableArtworkTypes(blankToNull(applicableArtworkTypes));
        item.setScoreType(blankToNull(scoreType));
        item.setMinScore(minScore);
        item.setMaxScore(maxScore);
        item.setScoreStep(scoreStep);
        item.setDefaultWeight(defaultWeight);
        item.setRequired(Boolean.TRUE.equals(required));
        item.setInputComponent(blankToNull(inputComponent));
        item.setOptionValues(blankToNull(optionValues));
        item.setScoringGuide(blankToNull(scoringGuide));
        item.setScoringRubric(blankToNull(scoringRubric));
        item.setUnit(blankToNull(unit));
        item.setTags(blankToNull(tags));
        item.setEnabled(enabled == null || enabled);
        item.setSortOrder(sortOrder == null ? 0 : sortOrder);
        if (bumpVersion) {
            item.setVersion(item.getVersion() + 1);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeExportField(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("导出字段不能为空");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!EXPORT_FIELD_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("导出字段只能使用英文小写字母、数字和下划线，且必须以字母开头");
        }
        return normalized;
    }

    private void ensureExportFieldUnique(String exportField, Long currentId) {
        repository.findByExportField(exportField).ifPresent(existing -> {
            if (currentId == null || !existing.getId().equals(currentId)) {
                throw new IllegalArgumentException("导出字段已存在");
            }
        });
    }
}
