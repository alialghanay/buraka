package org.ali.projects.buraka.review.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReviewRequest(
        @NotNull Long productId,
        @NotBlank @Size(max = 200) String title,
        @Min(1) @Max(5) int rating) {
}
