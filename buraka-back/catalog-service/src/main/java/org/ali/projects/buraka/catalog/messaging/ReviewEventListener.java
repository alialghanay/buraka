package org.ali.projects.buraka.catalog.messaging;

import org.ali.projects.buraka.catalog.product.ProductService;
import org.ali.projects.buraka.shared.messaging.ReviewEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ReviewEventListener {

    private final ProductService productService;

    public ReviewEventListener(ProductService productService) {
        this.productService = productService;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onReviewEvent(ReviewEvent event) {
        if (event.type() == ReviewEvent.Type.CREATED) {
            productService.applyReviewCreated(event.productId(), event.rating());
        } else {
            productService.applyReviewDeleted(event.productId(), event.rating());
        }
    }
}
