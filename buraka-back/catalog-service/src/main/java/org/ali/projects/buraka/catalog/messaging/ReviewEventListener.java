package org.ali.projects.buraka.catalog.messaging;

import org.ali.projects.buraka.catalog.product.ProductService;
import org.ali.projects.buraka.shared.messaging.ReviewEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ReviewEventListener {

    private static final Logger log = LoggerFactory.getLogger(ReviewEventListener.class);

    private final ProductService productService;

    public ReviewEventListener(ProductService productService) {
        this.productService = productService;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onReviewEvent(ReviewEvent event) {
        switch (event.type()) {
            case CREATED -> productService.applyReviewCreated(event.productId(), event.rating());
            case DELETED -> productService.applyReviewDeleted(event.productId(), event.rating());
            default -> log.warn("Unknown review event type '{}', dropping message", event.type());
        }
    }
}
