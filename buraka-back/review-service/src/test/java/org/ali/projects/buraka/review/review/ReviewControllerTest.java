package org.ali.projects.buraka.review.review;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReviewController.class)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    @Test
    void createReturns201() throws Exception {
        Review review = new Review(7L, 10L, "Great", 5, LocalDateTime.now());
        when(reviewService.create(eq(7L), any(CreateReviewRequest.class))).thenReturn(review);

        mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":10,\"title\":\"Great\",\"rating\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Great"))
                .andExpect(jsonPath("$.userId").value(7));
    }

    @Test
    void createRejectsRatingAbove5() throws Exception {
        mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":10,\"title\":\"Bad\",\"rating\":6}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsMissingUserHeader() throws Exception {
        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":10,\"title\":\"Great\",\"rating\":5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forProductReturnsReviews() throws Exception {
        when(reviewService.forProduct(10L))
                .thenReturn(List.of(new Review(7L, 10L, "Great", 5, LocalDateTime.now())));

        mockMvc.perform(get("/api/reviews").param("productId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Great"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/reviews/1").header("X-User-Id", "7"))
                .andExpect(status().isNoContent());
    }
}
