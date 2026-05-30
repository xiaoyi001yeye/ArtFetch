package com.artfetch.evaluation.service;

import com.artfetch.dto.PageResult;
import com.artfetch.evaluation.dto.ExpertAssignedProjectListItemDto;
import com.artfetch.evaluation.entity.EvaluationProject;
import com.artfetch.evaluation.entity.EvaluationProjectExpert;
import com.artfetch.evaluation.entity.EvaluationProjectStatus;
import com.artfetch.evaluation.entity.ExpertReview;
import com.artfetch.evaluation.entity.ExpertReviewStatus;
import com.artfetch.evaluation.repository.EvaluationArtworkRepository;
import com.artfetch.evaluation.repository.EvaluationProjectExpertRepository;
import com.artfetch.evaluation.repository.EvaluationProjectMetricRepository;
import com.artfetch.evaluation.repository.EvaluationProjectRepository;
import com.artfetch.evaluation.repository.ExpertReviewRepository;
import com.artfetch.repository.ArtworkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpertEvaluationServiceTest {

    @Mock
    private ExpertEvaluationAccessService accessService;
    @Mock
    private EvaluationProjectRepository projectRepository;
    @Mock
    private EvaluationProjectExpertRepository projectExpertRepository;
    @Mock
    private EvaluationArtworkRepository evaluationArtworkRepository;
    @Mock
    private ExpertReviewRepository reviewRepository;
    @Mock
    private EvaluationProjectMetricRepository metricRepository;
    @Mock
    private ArtworkRepository artworkRepository;
    @Mock
    private ExpertReviewService expertReviewService;

    private ExpertEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new ExpertEvaluationService(
                accessService,
                projectRepository,
                projectExpertRepository,
                evaluationArtworkRepository,
                reviewRepository,
                metricRepository,
                artworkRepository,
                expertReviewService
        );
    }

    @Test
    void listsCurrentExpertProgressAndPrioritizesRejectedArtwork() {
        LocalDateTime now = LocalDateTime.now();
        EvaluationProject project = new EvaluationProject();
        project.setId(10L);
        project.setName("春拍评估");
        project.setStatus(EvaluationProjectStatus.IN_PROGRESS);
        project.setUpdatedAt(now);
        EvaluationProjectExpert assignment = new EvaluationProjectExpert();
        assignment.setEvaluationId(10L);
        assignment.setExpertId(7L);
        assignment.setUpdatedAt(now);

        when(accessService.currentUserId()).thenReturn(7L);
        when(projectExpertRepository.findByExpertIdOrderByAssignedAtDesc(7L)).thenReturn(List.of(assignment));
        when(projectRepository.findAllById(List.of(10L))).thenReturn(List.of(project));
        when(reviewRepository.findByEvaluationIdAndExpertIdOrderByArtworkIdAsc(10L, 7L)).thenReturn(List.of(
                review(101L, ExpertReviewStatus.SUBMITTED, now),
                review(102L, ExpertReviewStatus.DRAFT, now),
                review(103L, ExpertReviewStatus.REVIEW_REJECTED, now)
        ));

        PageResult<ExpertAssignedProjectListItemDto> result = service.listProjects("pending", 0, 20);
        ExpertAssignedProjectListItemDto item = result.getItems().get(0);

        assertEquals(1, result.getTotal());
        assertEquals(3, item.totalCount());
        assertEquals(1, item.submittedCount());
        assertEquals(2, item.pendingCount());
        assertEquals(1, item.rejectedCount());
        assertEquals(103L, item.nextArtworkId());
    }

    private ExpertReview review(Long artworkId, ExpertReviewStatus status, LocalDateTime updatedAt) {
        ExpertReview review = new ExpertReview();
        review.setArtworkId(artworkId);
        review.setStatus(status);
        review.setUpdatedAt(updatedAt);
        return review;
    }
}
