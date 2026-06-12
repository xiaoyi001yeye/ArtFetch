package com.artfetch.service;

import com.artfetch.entity.Artwork;
import com.artfetch.repository.ArtworkRepository;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExportServiceTest {

    private final ArtworkRepository artworkRepository = mock(ArtworkRepository.class);
    private final ExportService exportService = new ExportService(artworkRepository);

    @Test
    void rejectsExportWithoutFilters() {
        assertThatThrownBy(() -> exportService.exportToExcel(null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请至少选择一个筛选条件后再导出");

        verifyNoInteractions(artworkRepository);
    }

    @Test
    void rejectsExportWithBlankTextFilters() {
        assertThatThrownBy(() -> exportService.exportToExcel(null, " ", "\t", "", " ", "\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请至少选择一个筛选条件后再导出");

        verifyNoInteractions(artworkRepository);
    }

    @Test
    void exportsArtworkDescription() throws Exception {
        Artwork artwork = new Artwork();
        artwork.setDescription("款识：山居图。");
        when(artworkRepository.findAll(any(Specification.class))).thenReturn(List.of(artwork));

        byte[] data = exportService.exportToExcel(1L, null, null, null, null, null);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(data))) {
            assertThat(workbook.getSheet("艺术品数据").getRow(0).getCell(7).getStringCellValue())
                    .isEqualTo("拍品描述");
            assertThat(workbook.getSheet("艺术品数据").getRow(1).getCell(7).getStringCellValue())
                    .isEqualTo("款识：山居图。");
        }
    }

    @Test
    void allowsExportFilteredOnlyByTransactionPriceStatus() throws Exception {
        Artwork artwork = new Artwork();
        artwork.setTitle("待补充成交价作品");
        artwork.setTransactionPriceStatus(Artwork.TransactionPriceStatus.MISSING);
        when(artworkRepository.findAll(any(Specification.class))).thenReturn(List.of(artwork));

        byte[] data = exportService.exportToExcel(null, null, null, null, null, null, "MISSING");

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(data))) {
            assertThat(workbook.getSheet("艺术品数据").getRow(1).getCell(1).getStringCellValue())
                    .isEqualTo("待补充成交价作品");
        }
    }
}
