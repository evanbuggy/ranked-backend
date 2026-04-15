package com.shared;
//Authored by Liam Kelly, 22346317
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;

@Configuration
public class RabbitMqConfiguration {

    public static final String EXCHANGE = "myapp.exchange";
    public static final String EVENT_IMPORTED_QUEUE = "event.imported.queue";
    public static final String IMPORT_FAILURE_QUEUE = "import.failure.queue";
    public static final String EVENT_IMPORTED_KEY = "event.imported";
    public static final String IMPORT_FAILURE_KEY = "import.failure";

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue eventImportedQueue() {
        return new Queue(EVENT_IMPORTED_QUEUE);
    }

    @Bean
    public Queue importFailureQueue() {
        return new Queue(IMPORT_FAILURE_QUEUE);
    }

    @Bean
    public Binding eventImportedBinding() {
        return BindingBuilder
            .bind(eventImportedQueue())
            .to(exchange())
            .with(EVENT_IMPORTED_KEY);
    }

    @Bean
    public Binding importFailureBinding() {
        return BindingBuilder
            .bind(importFailureQueue())
            .to(exchange())
            .with(IMPORT_FAILURE_KEY);
    }
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}