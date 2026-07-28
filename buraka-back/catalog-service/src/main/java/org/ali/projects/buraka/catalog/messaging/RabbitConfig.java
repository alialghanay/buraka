package org.ali.projects.buraka.catalog.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "buraka.events";
    public static final String QUEUE = "catalog.review-events";

    @Bean
    public TopicExchange burakaEvents() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue reviewEventsQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    public Binding reviewEventsBinding(Queue reviewEventsQueue, TopicExchange burakaEvents) {
        return BindingBuilder.bind(reviewEventsQueue).to(burakaEvents).with("review.*");
    }

    @Bean
    public JacksonJsonMessageConverter messageConverter() {
        return new JacksonJsonMessageConverter("org.ali.projects.buraka.shared.messaging");
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
