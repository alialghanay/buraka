package org.ali.projects.buraka.review.review;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;

    public ReviewService(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    public List<Review> forProduct(Long productId) {
        return reviewRepository.findByProductId(productId);
    }

    public Review create(long userId, CreateReviewRequest request) {
        Review review = new Review(userId, request.productId(), request.title(),
                request.rating(), LocalDateTime.now());
        return reviewRepository.save(review);
    }

    public void delete(long userId, long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "review not found"));
        if (review.getUserId() != userId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not your review");
        }
        reviewRepository.delete(review);
    }
}
