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

@Service
@RequiredArgsConstructor
public class EvaluationMetricDefinitionService {

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
        EvaluationMetricDefinition item = new EvaluationMetricDefinition();
        item.setCode(code);
        item.setCreatedBy(currentUserService.currentUser().displayName());
        apply(item, request.name(), request.description(), request.category(), request.applicableArtworkTypes(),
                request.scoreType(), request.minScore(), request.maxScore(), request.scoreStep(), request.defaultWeight(),
                request.required(), request.inputComponent(), request.optionValues(), request.scoringGuide(), request.scoringRubric(), request.unit(),
                request.tags(), true, request.sortOrder(), false);
        return EvaluationMetricDefinitionDto.from(repository.save(item));
    }

    @Transactional
    public EvaluationMetricDefinitionDto update(Long id, UpdateEvaluationMetricDefinitionRequest request) {
        EvaluationMetricDefinition item = requireEntity(id);
        apply(item, request.name(), request.description(), request.category(), request.applicableArtworkTypes(),
                request.scoreType(), request.minScore(), request.maxScore(), request.scoreStep(), request.defaultWeight(),
                request.required(), request.inputComponent(), request.optionValues(), request.scoringGuide(), request.scoringRubric(), request.unit(),
                request.tags(), request.enabled(), request.sortOrder(), true);
        return EvaluationMetricDefinitionDto.from(repository.save(item));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(requireEntity(id));
    }

    private EvaluationMetricDefinition requireEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "评估指标不存在"));
    }

    private void apply(EvaluationMetricDefinition item,
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
}
