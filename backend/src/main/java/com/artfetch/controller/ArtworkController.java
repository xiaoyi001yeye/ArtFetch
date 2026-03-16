package com.artfetch.controller;

import com.artfetch.dto.ArtworkDto;
import com.artfetch.dto.PageResult;
import com.artfetch.repository.ArtworkRepository;
import com.artfetch.repository.ArtworkSpec;
import com.artfetch.service.ExportService;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/api/artworks")
@RequiredArgsConstructor
public class ArtworkController {

    private final ArtworkRepository artworkRepository;
    private final ExportService exportService;

    @GetMapping
    public ResponseEntity<PageResult<ArtworkDto>> listArtworks(
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String artist,
            @RequestParam(required = false) String year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var artworkPage = artworkRepository.findAll(
                ArtworkSpec.search(taskId, blankToNull(keyword), blankToNull(artist), blankToNull(year)),
                PageRequest.of(page, size)
        );
        return ResponseEntity.ok(PageResult.of(artworkPage, ArtworkDto::from));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ArtworkDto> getArtwork(@PathVariable Long id) {
        return artworkRepository.findById(id)
                .map(artwork -> ResponseEntity.ok(ArtworkDto.from(artwork)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportArtworks(
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String artist,
            @RequestParam(required = false) String year) throws IOException {

        byte[] data = exportService.exportToExcel(taskId, blankToNull(keyword), blankToNull(artist), blankToNull(year));

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
}
