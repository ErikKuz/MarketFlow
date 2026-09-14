package com.example.marketflow.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import com.example.marketflow.config.RabbitMqConfig;

@Testcontainers(disabledWithoutDocker = true)
class RabbitMqIntegrationTest {
    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:4-management-alpine")
    );

    private CachingConnectionFactory connectionFactory;
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void declareTopology() {
        connectionFactory = new CachingConnectionFactory(RABBITMQ.getHost(), RABBITMQ.getAmqpPort());
        connectionFactory.setUsername(RABBITMQ.getAdminUsername());
        connectionFactory.setPassword(RABBITMQ.getAdminPassword());

        RabbitMqConfig configuration = new RabbitMqConfig();
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        var declarables = configuration.marketFlowRabbitDeclarables();
        declarables.getDeclarablesByType(Exchange.class).forEach(admin::declareExchange);
        declarables.getDeclarablesByType(Queue.class).forEach(admin::declareQueue);
        declarables.getDeclarablesByType(Binding.class).forEach(admin::declareBinding);

        rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(configuration.rabbitMessageConverter());
    }

    @AfterEach
    void closeConnection() {
        connectionFactory.destroy();
    }

    @Test
    void oneOrderEventReachesThreeIndependentQueuesAndDlxWorks() {
        MarketFlowEvent event = MarketFlowEvent.create(
                MarketFlowEventType.ORDER_PAID,
                42L, null, 7L, null, null,
                "PROCESSING", "PAID", Instant.now()
        );

        rabbitTemplate.convertAndSend(RabbitMqNames.EVENTS_EXCHANGE, RabbitMqNames.ORDER_PAID, event);

        assertReceived(event, RabbitMqNames.EVENT_HISTORY_QUEUE);
        assertReceived(event, RabbitMqNames.BUYER_NOTIFICATIONS_QUEUE);
        assertReceived(event, RabbitMqNames.SELLER_NOTIFICATIONS_QUEUE);

        rabbitTemplate.convertAndSend(
                RabbitMqNames.DEAD_LETTER_EXCHANGE,
                RabbitMqNames.DEAD_LETTER_ROUTING_KEY,
                event
        );
        assertReceived(event, RabbitMqNames.DEAD_LETTER_QUEUE);
    }

    private void assertReceived(MarketFlowEvent expected, String queue) {
        Object received = rabbitTemplate.receiveAndConvert(queue, 5_000);
        MarketFlowEvent event = assertInstanceOf(MarketFlowEvent.class, received);
        assertEquals(expected.eventId(), event.eventId());
        assertEquals(expected.eventType(), event.eventType());
    }
}
