package com.artfetch.evaluation.config;

import com.artfetch.evaluation.entity.EvaluationMetricDefinition;
import com.artfetch.evaluation.entity.EvaluationMetricTemplate;
import com.artfetch.evaluation.entity.EvaluationMetricTemplateItem;
import com.artfetch.evaluation.repository.EvaluationMetricDefinitionRepository;
import com.artfetch.evaluation.repository.EvaluationMetricTemplateItemRepository;
import com.artfetch.evaluation.repository.EvaluationMetricTemplateRepository;
import com.artfetch.evaluation.support.CalligraphyTrainingTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class EvaluationTrainingDataInitializer implements ApplicationRunner {

    private final EvaluationMetricDefinitionRepository definitionRepository;
    private final EvaluationMetricTemplateRepository templateRepository;
    private final EvaluationMetricTemplateItemRepository itemRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<EvaluationMetricDefinition> definitions = List.of(
                upsertMetric("calligraphy_brush", "calligraphy_brush", "笔触/笔墨", "笔触、笔墨表现和线条质量", 1),
                upsertMetric("calligraphy_composition", "calligraphy_composition", "构图", "画面结构、空间关系和视觉平衡", 2),
                upsertMetric("calligraphy_ink", "calligraphy_ink", "墨量/墨色", "墨色层次、浓淡控制和水墨表现", 3),
                upsertMetric("calligraphy_color", "calligraphy_color", "用色", "色彩使用、协调性和表现力", 4),
                upsertMetric("calligraphy_technique", "calligraphy_technique", "技法/画工", "技法成熟度、完成度和画工表现", 5)
        );

        upsertTemplate(
                CalligraphyTrainingTemplate.TEMPLATE_CODE,
                "书画模型训练标注模板",
                "系统内置模板，用于生成 DINOv2 书画特征训练 annotations.json。",
                definitions
        );

        List<EvaluationMetricDefinition> v2Definitions = CalligraphyTrainingTemplate.V2_METRICS.stream()
                .map(spec -> upsertMetric(spec.code(), spec.exportField(), spec.name(), spec.description(), spec.sortOrder()))
                .toList();
        upsertTemplate(
                CalligraphyTrainingTemplate.TEMPLATE_CODE_V2,
                "书画模型训练标注模板V2",
                "系统内置 V2 模板，用于生成 11 维书画专家标注训练数据。",
                v2Definitions
        );
    }

    private void upsertTemplate(String code, String name, String description, List<EvaluationMetricDefinition> definitions) {
        EvaluationMetricTemplate template = templateRepository.findByCode(code)
                .orElseGet(EvaluationMetricTemplate::new);
        template.setCode(code);
        template.setName(name);
        template.setDescription(description);
        template.setEnabled(true);
        template.setBuiltIn(true);
        EvaluationMetricTemplate saved = templateRepository.save(template);

        itemRepository.deleteByTemplateId(saved.getId());
        for (EvaluationMetricDefinition definition : definitions) {
            EvaluationMetricTemplateItem item = new EvaluationMetricTemplateItem();
            item.setTemplateId(saved.getId());
            item.setMetricDefinitionId(definition.getId());
            item.setMetricDefinitionVersion(definition.getVersion());
            item.setCodeSnapshot(definition.getCode());
            item.setExportFieldSnapshot(definition.getExportField());
            item.setNameSnapshot(definition.getName());
            item.setDescriptionSnapshot(definition.getDescription());
            item.setCategorySnapshot(definition.getCategory());
            item.setScoreType(definition.getScoreType());
            item.setMinScore(definition.getMinScore());
            item.setMaxScore(definition.getMaxScore());
            item.setScoreStep(definition.getScoreStep());
            item.setWeight(definition.getDefaultWeight());
            item.setRequired(definition.isRequired());
            item.setInputComponent(definition.getInputComponent());
            item.setScoringGuide(definition.getScoringGuide());
            item.setScoringRubric(definition.getScoringRubric());
            item.setSortOrder(definition.getSortOrder());
            itemRepository.save(item);
        }
    }

    private EvaluationMetricDefinition upsertMetric(String code, String exportField, String name, String description, int sortOrder) {
        EvaluationMetricDefinition item = definitionRepository.findByCode(code).orElseGet(EvaluationMetricDefinition::new);
        item.setCode(code);
        item.setExportField(exportField);
        item.setName(name);
        item.setDescription(description);
        item.setCategory("书画模型训练");
        item.setApplicableArtworkTypes("书画");
        item.setScoreType("numeric");
        item.setMinScore(0.0);
        item.setMaxScore(10.0);
        item.setScoreStep(0.1);
        item.setDefaultWeight(1.0);
        item.setRequired(true);
        item.setInputComponent("input-number");
        item.setScoringGuide("请按 0-10 分打分，可保留 1 位小数。");
        item.setUnit("分");
        item.setTags("书画,DINOv2,训练标注");
        item.setEnabled(true);
        item.setBuiltIn(true);
        item.setSortOrder(sortOrder);
        item.setCreatedBy("system");
        return definitionRepository.save(item);
    }
}
