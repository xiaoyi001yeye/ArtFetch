package com.artfetch.evaluation.dto;

import java.util.List;

public record UpdateDatasetArtworkSelectionRequest(
        List<Long> artworkIds,
        boolean selected
) {
}
