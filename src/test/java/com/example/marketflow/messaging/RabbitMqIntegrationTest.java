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
import com.example.marketflow.messaging.command.MarketFlowCommand;

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

        rabbitTemplate.convertAndSend(RabbitMqNames.BUSINESS_EVENTS_EXCHANGE, RabbitMqNames.ORDER_PAID_EVENT, event);

        assertReceived(event, RabbitMqNames.ORDER_EVENT_HISTORY_QUEUE);
        assertReceived(event, RabbitMqNames.BUYER_NOTIFICATION_QUEUE);
        assertReceived(event, RabbitMqNames.SELLER_NOTIFICATION_QUEUE);

        rabbitTemplate.convertAndSend(
                RabbitMqNames.DEAD_LETTER_EXCHANGE,
                RabbitMqNames.DEAD_LETTER_ROUTING_KEY,
                event
        );
        assertReceived(event, RabbitMqNames.DEAD_LETTER_QUEUE);
    }

    @Test
    void withdrawalCommandReachesOnlyWithdrawalQueue() {
        MarketFlowCommand command = MarketFlowCommand.withdrawal(
                81L, 7L, 15L, new java.math.BigDecimal("60.00"), Instant.now()
        );

        rabbitTemplate.convertAndSend(
                RabbitMqNames.MONEY_COMMANDS_EXCHANGE,
                RabbitMqNames.REQUEST_SELLER_WITHDRAWAL_COMMAND,
                command
        );

        Object received = rabbitTemplate.receiveAndConvert(RabbitMqNames.SELLER_WITHDRAWAL_QUEUE, 5_000);
        MarketFlowCommand delivered = assertInstanceOf(MarketFlowCommand.class, received);
        assertEquals(command.commandId(), delivered.commandId());
        assertEquals(command.commandType(), delivered.commandType());
        assertEquals(null, rabbitTemplate.receiveAndConvert(RabbitMqNames.SELLER_FUNDS_RELEASE_QUEUE, 200));
    }

    private void assertReceived(MarketFlowEvent expected, String queue) {
        Object received = rabbitTemplate.receiveAndConvert(queue, 5_000);
        MarketFlowEvent event = assertInstanceOf(MarketFlowEvent.class, received);
        assertEquals(expected.eventId(), event.eventId());
        assertEquals(expected.eventType(), event.eventType());
    }
}
