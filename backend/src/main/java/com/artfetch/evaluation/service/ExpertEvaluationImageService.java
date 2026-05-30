package com.artfetch.evaluation.service;

import com.artfetch.auth.service.AuditLogService;
import com.artfetch.config.AppProperties;
import com.artfetch.entity.Artwork;
import com.artfetch.repository.ArtworkRepository;
import com.artfetch.service.ArtronRequestSupport;
import com.artfetch.service.HdImageService;
import com.artfetch.service.OriginalImageService;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExpertEvaluationImageService {

    private final ExpertEvaluationAccessService accessService;
    private final ArtworkRepository artworkRepository;
    private final OriginalImageService originalImageService;
    private final HdImageService hdImageService;
    private final ArtronRequestSupport artronRequestSupport;
    private final AppProperties appProperties;
    private final AuditLogService auditLogService;

    public ImagePayload loadPreview(Long evaluationId, Long artworkId) {
        accessService.requireExpertImageAccess(evaluationId, artworkId);
        Artwork artwork = requireArtwork(artworkId);
        if (artwork.getImageUrl() == null || artwork.getImageUrl().isBlank()) {
            throw new IllegalStateException("预览图尚未准备好");
        }
        try {
            var response = artronRequestSupport.configure(
                            Jsoup.connect(artwork.getImageUrl()),
                            artwork.getSourceUrl(),
                            appProperties.getImage().getDownloadTimeoutMs()
                    )
                    .ignoreContentType(true)
                    .execute();
            byte[] bytes = response.bodyAsBytes();
            if (bytes.length == 0) {
                throw new IllegalStateException("预览图响应为空");
            }
            return new ImagePayload(bytes, parseMediaType(response.contentType(), MediaType.IMAGE_JPEG));
        } catch (Exception e) {
            throw new IllegalStateException("预览图暂不可用，请稍后重试");
        }
    }

    public Resource loadOriginal(Long evaluationId, Long artworkId) {
        accessService.requireExpertImageAccess(evaluationId, artworkId);
        try {
            Resource resource = originalImageService.loadOriginalImage(artworkId);
            auditLogService.recordSuccess("evaluation-image.original.view", "ARTWORK", String.valueOf(artworkId),
                    "专家查看评估原图 evaluationId=" + evaluationId + ", artworkId=" + artworkId);
            return resource;
        } catch (Exception e) {
            throw new IllegalStateException("原图暂不可用，请稍后重试");
        }
    }

    public Resource loadHd(Long evaluationId, Long artworkId) {
        accessService.requireExpertImageAccess(evaluationId, artworkId);
        try {
            Resource resource = hdImageService.loadHdImage(artworkId);
            auditLogService.recordSuccess("evaluation-image.hd.view", "ARTWORK", String.valueOf(artworkId),
                    "专家查看评估高清大图 evaluationId=" + evaluationId + ", artworkId=" + artworkId);
            return resource;
        } catch (Exception e) {
            throw new IllegalStateException("高清大图暂不可用，请稍后重试");
        }
    }

    public MediaType originalMediaType(Long artworkId) {
        return originalImageService.resolveMediaType(artworkId);
    }

    public MediaType hdMediaType(Long artworkId) {
        return hdImageService.resolveMediaType(artworkId);
    }

    public String originalFilename(Long artworkId) {
        return originalImageService.originalFilename(artworkId);
    }

    public String hdFilename(Long artworkId) {
        return hdImageService.hdFilename(artworkId);
    }

    private Artwork requireArtwork(Long artworkId) {
        return artworkRepository.findById(artworkId)
                .orElseThrow(() -> new IllegalArgumentException("艺术品不存在"));
    }

    private MediaType parseMediaType(String contentType, MediaType fallback) {
        if (contentType == null || contentType.isBlank()) {
            return fallback;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception e) {
            return fallback;
        }
    }

    public record ImagePayload(byte[] bytes, MediaType mediaType) {
    }
}
