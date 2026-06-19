package com.artfetch.evaluation.support;

import java.util.List;

public final class CalligraphyTrainingTemplate {
    public static final String TEMPLATE_CODE = "calligraphy_training_annotation";
    public static final List<String> METRIC_CODES = List.of(
            "calligraphy_brush",
            "calligraphy_composition",
            "calligraphy_ink",
            "calligraphy_color",
            "calligraphy_technique"
    );

    private CalligraphyTrainingTemplate() {
    }
}
