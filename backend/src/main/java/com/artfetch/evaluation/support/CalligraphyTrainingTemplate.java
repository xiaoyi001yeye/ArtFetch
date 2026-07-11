package com.artfetch.evaluation.support;

import java.util.List;

public final class CalligraphyTrainingTemplate {
    public static final String TEMPLATE_CODE = "calligraphy_training_annotation";
    public static final String TEMPLATE_CODE_V2 = "calligraphy_training_annotation_v2";
    public static final List<String> METRIC_CODES = List.of(
            "calligraphy_brush",
            "calligraphy_composition",
            "calligraphy_ink",
            "calligraphy_color",
            "calligraphy_technique"
    );
    public static final List<MetricSpec> V2_METRICS = List.of(
            new MetricSpec("calligraphy_v2_craftsmanship", "craftsmanship", "画工", "技法成熟度、完成度、造型与线条控制", 1),
            new MetricSpec("calligraphy_v2_composition", "composition", "构图", "画面结构、空间关系和视觉平衡", 2),
            new MetricSpec("calligraphy_v2_ink_brushwork", "ink_brushwork", "笔墨", "笔触、墨色层次、线条韵味和水墨表现力", 3),
            new MetricSpec("calligraphy_v2_color", "color", "用色", "设色协调性、色彩层次、综合色感和表现力", 4),
            new MetricSpec("calligraphy_v2_subject", "subject", "题材", "题材辨识度、艺术表达适配度和市场关注度", 5),
            new MetricSpec("calligraphy_v2_size", "size", "尺寸", "尺幅规格对表现力、展示性、收藏和市场价值的影响", 6),
            new MetricSpec("calligraphy_v2_inscription", "inscription", "提拔", "题跋、款识、印章等辅助信息的完整性和价值支撑", 7),
            new MetricSpec("calligraphy_v2_provenance", "provenance", "来源著录", "来源、流传脉络、出版、展览、著录和可追溯性", 8),
            new MetricSpec("calligraphy_v2_rarity", "rarity", "稀缺性", "作者、时期、题材、风格、尺幅或流通状态的稀缺程度", 9),
            new MetricSpec("calligraphy_v2_condition", "condition", "品相", "保存状态、污损、折痕、缺损、修复痕迹和完整度", 10),
            new MetricSpec("calligraphy_v2_mounting", "mounting", "裱工", "装裱材料、工艺、平整度、保存保护效果和展示价值", 11)
    );

    private CalligraphyTrainingTemplate() {
    }

    public record MetricSpec(String code, String exportField, String name, String description, int sortOrder) {
    }
}
