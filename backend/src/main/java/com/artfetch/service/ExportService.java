package com.artfetch.service;

import com.artfetch.entity.Artwork;
import com.artfetch.repository.ArtworkRepository;
import com.artfetch.repository.ArtworkSpec;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExportService {

    private final ArtworkRepository artworkRepository;

    private static final String[] HEADERS = {
            "ID", "拍品名称", "编号", "艺术家", "材质", "形制", "尺寸", "估价",
            "拍卖公司", "拍卖会", "拍卖专场", "拍卖日期", "拍卖地点",
            "预展时间", "预展地点", "来源URL", "图片URL", "抓取时间"
    };

    public byte[] exportToExcel(Long taskId, String keyword, String artist,
                                String auctionDate, String lotNumber) throws IOException {
        List<Artwork> artworks = artworkRepository.findAll(
                ArtworkSpec.search(taskId, keyword, artist, auctionDate, lotNumber));

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("艺术品数据");

            // 标题行样式
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            // 数据行样式（偶数行）
            CellStyle evenRowStyle = workbook.createCellStyle();
            evenRowStyle.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
            evenRowStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // 写入表头
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // 写入数据
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            for (int i = 0; i < artworks.size(); i++) {
                Artwork art = artworks.get(i);
                Row row = sheet.createRow(i + 1);
                if (i % 2 == 1) {
                    for (int j = 0; j < HEADERS.length; j++) {
                        row.createCell(j).setCellStyle(evenRowStyle);
                    }
                }

                setCellValue(row, 0, String.valueOf(art.getId()));
                setCellValue(row, 1, art.getTitle());
                setCellValue(row, 2, art.getLotNumber());
                setCellValue(row, 3, art.getArtist());
                setCellValue(row, 4, art.getMedium());
                setCellValue(row, 5, art.getFormat());
                setCellValue(row, 6, art.getDimensions());
                setCellValue(row, 7, art.getValuation());
                setCellValue(row, 8, art.getAuctionHouse());
                setCellValue(row, 9, art.getAuctionName());
                setCellValue(row, 10, art.getAuctionSession());
                setCellValue(row, 11, art.getAuctionDate());
                setCellValue(row, 12, art.getAuctionLocation());
                setCellValue(row, 13, art.getPreviewTime());
                setCellValue(row, 14, art.getPreviewLocation());
                setCellValue(row, 15, art.getSourceUrl());
                setCellValue(row, 16, art.getImageUrl());
                setCellValue(row, 17, art.getCreatedAt() != null ? art.getCreatedAt().format(formatter) : "");
            }

            // 自动列宽
            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
                int currentWidth = sheet.getColumnWidth(i);
                sheet.setColumnWidth(i, Math.min(currentWidth + 512, 15000));
            }

            // 冻结首行
            sheet.createFreezePane(0, 1);

            // 自动筛选
            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, HEADERS.length - 1));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void setCellValue(Row row, int col, String value) {
        Cell cell = row.getCell(col);
        if (cell == null) cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
    }
}
