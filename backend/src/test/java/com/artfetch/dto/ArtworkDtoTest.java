package com.artfetch.dto;

import com.artfetch.entity.Artwork;
import com.artfetch.entity.SearchTask;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArtworkDtoTest {

    @Test
    void derivesTransactionPriceStatusFromPriceWhenStoredStatusIsMissing() {
        Artwork artwork = artwork();
        artwork.setTransactionPrice("RMB 120,000");

        ArtworkDto dto = ArtworkDto.from(artwork);

        assertThat(dto.getTransactionPriceStatus()).isEqualTo("HAS_PRICE");
    }

    @Test
    void derivesTransactionPriceStatusFromLoginRequiredNoteWhenStoredStatusIsMissing() {
        Artwork artwork = artwork();
        artwork.setTransactionPriceNote("需要登录");

        ArtworkDto dto = ArtworkDto.from(artwork);

        assertThat(dto.getTransactionPriceStatus()).isEqualTo("LOGIN_REQUIRED");
    }

    @Test
    void derivesTransactionPriceStatusFromFailureNoteWhenStoredStatusIsMissing() {
        Artwork artwork = artwork();
        artwork.setTransactionPriceNote("缺少详情页地址");

        ArtworkDto dto = ArtworkDto.from(artwork);

        assertThat(dto.getTransactionPriceStatus()).isEqualTo("FAILED");
    }

    @Test
    void defaultsTransactionPriceStatusToMissingWhenStoredStatusAndSignalsAreMissing() {
        ArtworkDto dto = ArtworkDto.from(artwork());

        assertThat(dto.getTransactionPriceStatus()).isEqualTo("MISSING");
    }

    private Artwork artwork() {
        SearchTask task = new SearchTask();
        task.setId(10L);
        task.setName("test task");

        Artwork artwork = new Artwork();
        artwork.setId(1L);
        artwork.setTask(task);
        artwork.setExternalId("external-1");
        artwork.setTitle("test artwork");
        return artwork;
    }
}
