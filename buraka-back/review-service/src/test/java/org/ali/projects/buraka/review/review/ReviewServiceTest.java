package org.ali.projects.buraka.review.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.ali.projects.buraka.review.messaging.ReviewEventPublisher;
import org.ali.projects.buraka.shared.messaging.ReviewEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewEventPublisher eventPublisher;

    private ReviewService service;

    @BeforeEach
    void setUp() {
        service = new ReviewService(reviewRepository, eventPublisher);
    }

    @Test
    void createPersistsAndPublishesCreatedEvent() {
        CreateReviewRequest request = new CreateReviewRequest(10L, "Great", 5);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setReviewId(1L);
            return r;
        });

        Review saved = service.create(7L, request);

        assertThat(saved.getUserId()).isEqualTo(7L);
        verify(eventPublisher).publish(argThat(event ->
                event.type() == ReviewEvent.Type.CREATED
                        && event.reviewId() == 1L
                        && event.productId() == 10L
                        && event.rating() == 5));
    }

    @Test
    void deletePublishesDeletedEvent() {
        Review review = new Review(7L, 10L, "Great", 5, LocalDateTime.now());
        review.setReviewId(1L);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

        service.delete(7L, 1L);

        verify(reviewRepository).delete(review);
        verify(eventPublisher).publish(argThat(event ->
                event.type() == ReviewEvent.Type.DELETED
                        && event.reviewId() == 1L
                        && event.productId() == 10L
                        && event.rating() == 5));
    }

    @Test
    void deleteRejectsOtherUsersReviewWithoutPublishing() {
        Review review = new Review(7L, 10L, "Great", 5, LocalDateTime.now());
        review.setReviewId(1L);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.delete(8L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(reviewRepository, never()).delete(any(Review.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void deleteMissingReviewReturns404() {
        when(reviewRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(7L, 99L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verifyNoInteractions(eventPublisher);
    }
}
