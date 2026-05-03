package com.artfetch.evaluation.dto;

public record CriterionItemDto(
        String fieldName,
        String fieldLabel,
        String operator,
        String value,
        String valueTo,
        String valueType
) {
}
