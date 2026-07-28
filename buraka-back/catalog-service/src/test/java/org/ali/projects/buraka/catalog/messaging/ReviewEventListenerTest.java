package org.ali.projects.buraka.catalog.messaging;

import static org.mockito.Mockito.verify;

import org.ali.projects.buraka.catalog.product.ProductService;
import org.ali.projects.buraka.shared.messaging.ReviewEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewEventListenerTest {

    @Mock
    private ProductService productService;

    @InjectMocks
    private ReviewEventListener listener;

    @Test
    void createdEventAppliesRatingIncrement() {
        listener.onReviewEvent(new ReviewEvent(1L, 10L, 5, ReviewEvent.Type.CREATED));

        verify(productService).applyReviewCreated(10L, 5);
    }

    @Test
    void deletedEventAppliesRatingDecrement() {
        listener.onReviewEvent(new ReviewEvent(1L, 10L, 5, ReviewEvent.Type.DELETED));

        verify(productService).applyReviewDeleted(10L, 5);
    }
}
