package com.artfetch.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.artfetch.auth.service.AuditLogService;
import com.artfetch.auth.support.PermissionCodes;
import com.artfetch.dto.ArtworkDto;
import com.artfetch.dto.PageResult;
import com.artfetch.evaluation.service.ExpertEvaluationAccessService;
import com.artfetch.repository.ArtworkRepository;
import com.artfetch.repository.ArtworkSpec;
import com.artfetch.service.ExportService;
import com.artfetch.service.HdImageService;
import com.artfetch.service.OriginalImageService;
import com.artfetch.service.TransactionPriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/api/artworks")
@RequiredArgsConstructor
public class ArtworkController {

    private final ArtworkRepository artworkRepository;
    private final ExportService exportService;
    private final OriginalImageService originalImageService;
    private final HdImageService hdImageService;
    private final TransactionPriceService transactionPriceService;
    private final AuditLogService auditLogService;
    private final ExpertEvaluationAccessService expertEvaluationAccessService;

    @GetMapping
    @SaCheckPermission(PermissionCodes.ARTWORK_VIEW)
    public ResponseEntity<PageResult<ArtworkDto>> listArtworks(
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String artist,
            @RequestParam(required = false) String auctionDate,
            @RequestParam(required = false) String lotNumber,
            @RequestParam(required = false) String hdImageSyncStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var artworkPage = artworkRepository.findAll(
                ArtworkSpec.search(taskId, blankToNull(keyword), blankToNull(artist),
                        blankToNull(auctionDate), blankToNull(lotNumber), blankToNull(hdImageSyncStatus)),
                PageRequest.of(page, size)
        );
        return ResponseEntity.ok(PageResult.of(artworkPage, ArtworkDto::from));
    }

    @GetMapping("/{id}")
    @SaCheckPermission(PermissionCodes.ARTWORK_VIEW)
    public ResponseEntity<ArtworkDto> getArtwork(@PathVariable Long id) {
        return artworkRepository.findById(id)
                .map(artwork -> ResponseEntity.ok(ArtworkDto.from(artwork)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/original-image")
    @SaCheckPermission(PermissionCodes.ARTWORK_IMAGE_VIEW)
    public ResponseEntity<Resource> viewOriginalImage(@PathVariable Long id) {
        expertEvaluationAccessService.requireGeneralArtworkImageAccess(id);
        Resource resource = originalImageService.loadOriginalImage(id);
        String filename = originalImageService.originalFilename(id);
        MediaType mediaType = originalImageService.resolveMediaType(id);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" +
                        URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20"))
                .body(resource);
    }

    @GetMapping("/{id}/hd-image")
    @SaCheckPermission(PermissionCodes.ARTWORK_IMAGE_VIEW)
    public ResponseEntity<Resource> viewHdImage(@PathVariable Long id) {
        expertEvaluationAccessService.requireGeneralArtworkImageAccess(id);
        Resource resource = hdImageService.loadHdImage(id);
        String filename = hdImageService.hdFilename(id);
        MediaType mediaType = hdImageService.resolveMediaType(id);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" +
                        URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20"))
                .body(resource);
    }

    @PostMapping("/{id}/original-image/redownload")
    @SaCheckPermission(PermissionCodes.ARTWORK_IMAGE_REDOWNLOAD)
    public ResponseEntity<ArtworkDto> redownloadOriginalImage(@PathVariable Long id) {
        return ResponseEntity.ok(originalImageService.redownloadOriginalImage(id));
    }

    @PostMapping("/{id}/hd-image/redownload")
    @SaCheckPermission(PermissionCodes.ARTWORK_IMAGE_REDOWNLOAD)
    public ResponseEntity<ArtworkDto> redownloadHdImage(@PathVariable Long id) {
        return ResponseEntity.ok(hdImageService.redownloadHdImage(id));
    }

    @PostMapping("/{id}/transaction-price/supplement")
    @SaCheckPermission(PermissionCodes.ARTWORK_TRANSACTION_PRICE_SUPPLEMENT)
    public ResponseEntity<ArtworkDto> supplementTransactionPrice(@PathVariable Long id) {
        return ResponseEntity.ok(transactionPriceService.supplementSingleArtwork(id));
    }

    @GetMapping("/export")
    @SaCheckPermission(PermissionCodes.ARTWORK_EXPORT)
    public ResponseEntity<byte[]> exportArtworks(
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String artist,
            @RequestParam(required = false) String auctionDate,
            @RequestParam(required = false) String lotNumber,
            @RequestParam(required = false) String hdImageSyncStatus) throws IOException {

        byte[] data = exportService.exportToExcel(taskId, blankToNull(keyword), blankToNull(artist),
                blankToNull(auctionDate), blankToNull(lotNumber), blankToNull(hdImageSyncStatus));
        auditLogService.recordSuccess("artwork.export", "ARTWORK", null, "导出艺术品数据");

        String filename = "artworks_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .body(data);
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
