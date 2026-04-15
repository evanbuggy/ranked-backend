package com.shared;
//Authored by Liam Kelly, 22346317
import com.startGgIntegration.systemEvents.EventImported;
import com.startGgIntegration.systemEvents.ImportFailure;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public EventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishEvent(Object event) {
        if (event instanceof EventImported) {
            rabbitTemplate.convertAndSend(
                RabbitMqConfiguration.EXCHANGE,
                RabbitMqConfiguration.EVENT_IMPORTED_KEY,
                event
            );
        } else if (event instanceof ImportFailure) {
            rabbitTemplate.convertAndSend(
                RabbitMqConfiguration.EXCHANGE,
                RabbitMqConfiguration.IMPORT_FAILURE_KEY,
                event
            );
        }
    }
}