package com.artfetch.evaluation.service;

import com.artfetch.dto.PageResult;
import com.artfetch.evaluation.dto.*;
import com.artfetch.evaluation.entity.EvaluationMetricTemplate;
import com.artfetch.evaluation.entity.EvaluationMetricTemplateItem;
import com.artfetch.evaluation.repository.EvaluationMetricDefinitionRepository;
import com.artfetch.evaluation.repository.EvaluationMetricTemplateItemRepository;
import com.artfetch.evaluation.repository.EvaluationMetricTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EvaluationMetricTemplateService {

    private final EvaluationMetricTemplateRepository repository;
    private final EvaluationMetricTemplateItemRepository itemRepository;
    private final EvaluationMetricDefinitionRepository definitionRepository;

    @Transactional(readOnly = true)
    public PageResult<EvaluationMetricTemplateDto> list(int page, int size) {
        return PageResult.of(repository.findAllByOrderByUpdatedAtDesc(PageRequest.of(page, size)),
                template -> toDto(template, itemRepository.findByTemplateIdOrderBySortOrderAscIdAsc(template.getId())));
    }

    @Transactional(readOnly = true)
    public EvaluationMetricTemplateDto get(Long id) {
        EvaluationMetricTemplate template = requireEntity(id);
        return toDto(template, itemRepository.findByTemplateIdOrderBySortOrderAscIdAsc(id));
    }

    @Transactional
    public EvaluationMetricTemplateDto create(CreateEvaluationMetricTemplateRequest request) {
        EvaluationMetricTemplate template = new EvaluationMetricTemplate();
        template.setName(request.name().trim());
        template.setDescription(blankToNull(request.description()));
        template.setEnabled(request.enabled() == null || request.enabled());
        EvaluationMetricTemplate saved = repository.save(template);
        replaceItems(saved.getId(), request.items());
        return get(saved.getId());
    }

    @Transactional
    public EvaluationMetricTemplateDto update(Long id, UpdateEvaluationMetricTemplateRequest request) {
        EvaluationMetricTemplate template = requireEntity(id);
        if (template.isBuiltIn()) {
            throw new IllegalStateException("系统内置评估指标模板不能编辑");
        }
        template.setName(request.name().trim());
        template.setDescription(blankToNull(request.description()));
        template.setEnabled(request.enabled() == null || request.enabled());
        repository.save(template);
        replaceItems(id, request.items());
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        EvaluationMetricTemplate template = requireEntity(id);
        if (template.isBuiltIn()) {
            throw new IllegalStateException("系统内置评估指标模板不能删除");
        }
        itemRepository.deleteByTemplateId(id);
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<MetricConfigDto> listItems(Long id) {
        requireEntity(id);
        return itemRepository.findByTemplateIdOrderBySortOrderAscIdAsc(id).stream()
                .map(MetricConfigDto::fromTemplateItem)
                .toList();
    }

    private EvaluationMetricTemplate requireEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "评估指标模板不存在"));
    }

    private EvaluationMetricTemplateDto toDto(EvaluationMetricTemplate template, List<EvaluationMetricTemplateItem> items) {
        return EvaluationMetricTemplateDto.from(template, items.stream().map(MetricConfigDto::fromTemplateItem).toList());
    }

    private void replaceItems(Long templateId, List<MetricConfigRequest> items) {
        itemRepository.deleteByTemplateId(templateId);
        if (items == null || items.isEmpty()) {
            return;
        }
        List<EvaluationMetricTemplateItem> entities = new ArrayList<>();
        for (MetricConfigRequest item : items) {
            EvaluationMetricTemplateItem entity = new EvaluationMetricTemplateItem();
            entity.setTemplateId(templateId);
            entity.setMetricDefinitionId(item.sourceMetricDefinitionId());
            entity.setMetricDefinitionVersion(item.sourceVersion());
            entity.setCodeSnapshot(item.code().trim());
            entity.setExportFieldSnapshot(blankToNull(item.exportField()));
            entity.setNameSnapshot(item.name().trim());
            entity.setDescriptionSnapshot(blankToNull(item.description()));
            entity.setCategorySnapshot(blankToNull(item.category()));
            entity.setScoreType(blankToNull(item.scoreType()));
            entity.setMinScore(item.minScore());
            entity.setMaxScore(item.maxScore());
            entity.setScoreStep(item.scoreStep());
            entity.setWeight(item.weight());
            entity.setRequired(Boolean.TRUE.equals(item.required()));
            entity.setInputComponent(blankToNull(item.inputComponent()));
            entity.setOptionValues(blankToNull(item.optionValues()));
            entity.setScoringGuide(blankToNull(item.scoringGuide()));
            entity.setScoringRubric(blankToNull(item.scoringRubric()));
            entity.setSortOrder(item.sortOrder() == null ? 0 : item.sortOrder());
            if (entity.getMetricDefinitionId() != null) {
                definitionRepository.findById(entity.getMetricDefinitionId())
                        .ifPresent(definition -> {
                            entity.setMetricDefinitionVersion(definition.getVersion());
                            entity.setExportFieldSnapshot(definition.getExportField());
                        });
            }
            if (entity.getExportFieldSnapshot() == null) {
                throw new IllegalArgumentException("模板指标缺少导出字段: " + entity.getNameSnapshot());
            }
            entities.add(entity);
        }
        itemRepository.saveAll(entities);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
