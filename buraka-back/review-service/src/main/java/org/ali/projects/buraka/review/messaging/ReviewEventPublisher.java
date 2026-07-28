package org.ali.projects.buraka.review.messaging;

import org.ali.projects.buraka.shared.messaging.ReviewEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class ReviewEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public ReviewEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(ReviewEvent event) {
        String routingKey = event.type() == ReviewEvent.Type.CREATED ? "review.created" : "review.deleted";
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, routingKey, event);
    }
}
