package com.artfetch.evaluation.dto;

import jakarta.validation.Valid;

import java.util.List;

public record PreviewArtworksRequest(
        @Valid List<CriterionItemDto> criteria,
        Integer page,
        Integer size
) {
}
