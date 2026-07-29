package org.ali.projects.buraka.review.review;

import java.time.LocalDateTime;
import java.util.List;

import org.ali.projects.buraka.review.messaging.ReviewEventPublisher;
import org.ali.projects.buraka.shared.messaging.ReviewEvent;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewEventPublisher eventPublisher;

    public ReviewService(ReviewRepository reviewRepository, ReviewEventPublisher eventPublisher) {
        this.reviewRepository = reviewRepository;
        this.eventPublisher = eventPublisher;
    }

    public List<Review> forProduct(Long productId) {
        return reviewRepository.findByProductId(productId);
    }

    public Review create(long userId, CreateReviewRequest request) {
        if (reviewRepository.findByUserIdAndProductId(userId, request.productId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "already reviewed this product");
        }
        Review review = new Review(userId, request.productId(), request.title(),
                request.rating(), LocalDateTime.now());
        Review saved = reviewRepository.save(review);
        eventPublisher.publish(new ReviewEvent(saved.getReviewId(), saved.getProductId(),
                saved.getRating(), ReviewEvent.Type.CREATED));
        return saved;
    }

    public void delete(long userId, long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "review not found"));
        if (review.getUserId() != userId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not your review");
        }
        reviewRepository.delete(review);
        eventPublisher.publish(new ReviewEvent(review.getReviewId(), review.getProductId(),
                review.getRating(), ReviewEvent.Type.DELETED));
    }
}
