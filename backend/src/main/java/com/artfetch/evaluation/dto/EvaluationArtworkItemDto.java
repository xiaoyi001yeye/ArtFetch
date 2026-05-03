package com.artfetch.evaluation.dto;

import com.artfetch.dto.ArtworkDto;
import com.artfetch.evaluation.entity.EvaluationArtwork;

public record EvaluationArtworkItemDto(
        Long id,
        Long artworkId,
        String status,
        ArtworkDto artwork
) {
    public static EvaluationArtworkItemDto from(EvaluationArtwork item, ArtworkDto artwork) {
        return new EvaluationArtworkItemDto(item.getId(), item.getArtworkId(), item.getStatus(), artwork);
    }
}
