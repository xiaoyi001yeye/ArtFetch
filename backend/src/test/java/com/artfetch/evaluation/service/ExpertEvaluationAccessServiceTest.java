package com.artfetch.evaluation.service;

import com.artfetch.auth.service.CurrentUserService;
import com.artfetch.auth.service.DataScopeService;
import com.artfetch.evaluation.entity.EvaluationArtwork;
import com.artfetch.evaluation.entity.EvaluationProject;
import com.artfetch.evaluation.entity.EvaluationProjectExpert;
import com.artfetch.evaluation.entity.EvaluationProjectStatus;
import com.artfetch.evaluation.entity.ExpertReview;
import com.artfetch.evaluation.repository.EvaluationArtworkRepository;
import com.artfetch.evaluation.repository.EvaluationProjectExpertRepository;
import com.artfetch.evaluation.repository.EvaluationProjectRepository;
import com.artfetch.evaluation.repository.ExpertReviewRepository;
import com.artfetch.repository.ArtworkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpertEvaluationAccessServiceTest {

    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private EvaluationProjectRepository projectRepository;
    @Mock
    private EvaluationProjectExpertRepository projectExpertRepository;
    @Mock
    private EvaluationArtworkRepository evaluationArtworkRepository;
    @Mock
    private ExpertReviewRepository expertReviewRepository;
    @Mock
    private ArtworkRepository artworkRepository;

    private ExpertEvaluationAccessService service;

    @BeforeEach
    void setUp() {
        service = new ExpertEvaluationAccessService(
                currentUserService,
                dataScopeService,
                projectRepository,
                projectExpertRepository,
                evaluationArtworkRepository,
                expertReviewRepository,
                artworkRepository
        );
        when(currentUserService.currentUserId()).thenReturn(7L);
    }

    @Test
    void allowsAssignedExpertToAccessOwnProjectArtwork() {
        EvaluationProject project = publishedProject();
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(projectExpertRepository.findByEvaluationIdAndExpertId(10L, 7L))
                .thenReturn(Optional.of(new EvaluationProjectExpert()));
        when(artworkRepository.existsById(20L)).thenReturn(true);
        when(evaluationArtworkRepository.findByEvaluationIdAndArtworkId(10L, 20L))
                .thenReturn(Optional.of(new EvaluationArtwork()));
        when(expertReviewRepository.findByEvaluationIdAndArtworkIdAndExpertId(10L, 20L, 7L))
                .thenReturn(Optional.of(new ExpertReview()));

        service.requireExpertArtworkAccess(10L, 20L);
    }

    @Test
    void rejectsGenericImageAccessForUnassignedArtwork() {
        when(artworkRepository.existsById(20L)).thenReturn(true);
        when(expertReviewRepository.findByArtworkIdAndExpertId(20L, 7L)).thenReturn(List.of());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.requireGeneralArtworkImageAccess(20L));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    private EvaluationProject publishedProject() {
        EvaluationProject project = new EvaluationProject();
        project.setId(10L);
        project.setStatus(EvaluationProjectStatus.PUBLISHED);
        return project;
    }
}
