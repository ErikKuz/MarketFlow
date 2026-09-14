package com.example.marketflow.config;

import java.util.List;
import java.util.Map;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.example.marketflow.messaging.RabbitMqNames;

@Configuration
@EnableRabbit
@EnableScheduling
public class RabbitMqConfig {

    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public Declarables marketFlowRabbitDeclarables() {
        TopicExchange events = new TopicExchange(RabbitMqNames.EVENTS_EXCHANGE, true, false);
        TopicExchange deadLetters = new TopicExchange(RabbitMqNames.DEAD_LETTER_EXCHANGE, true, false);

        Queue history = queueWithDeadLetters(RabbitMqNames.EVENT_HISTORY_QUEUE);
        Queue buyers = queueWithDeadLetters(RabbitMqNames.BUYER_NOTIFICATIONS_QUEUE);
        Queue sellers = queueWithDeadLetters(RabbitMqNames.SELLER_NOTIFICATIONS_QUEUE);
        Queue deadLetterQueue = QueueBuilder.durable(RabbitMqNames.DEAD_LETTER_QUEUE).build();

        List<Declarable> declarables = new java.util.ArrayList<>(List.of(
                events, deadLetters, history, buyers, sellers, deadLetterQueue,
                binding(history, events, "order.#"),
                binding(history, events, "seller-order.#"),
                binding(history, events, "money.#"),
                binding(buyers, events, RabbitMqNames.ORDER_CREATED),
                binding(buyers, events, RabbitMqNames.ORDER_PAID),
                binding(buyers, events, RabbitMqNames.SELLER_SENT_PRODUCT),
                binding(buyers, events, RabbitMqNames.ORDER_COMPLETED),
                binding(buyers, events, RabbitMqNames.ORDER_CANCELLED),
                binding(buyers, events, RabbitMqNames.ORDER_REFUNDED),
                binding(sellers, events, RabbitMqNames.ORDER_PAID),
                binding(sellers, events, RabbitMqNames.USER_RECEIVED_PRODUCT),
                binding(sellers, events, "money.seller.#"),
                binding(sellers, events, RabbitMqNames.ORDER_COMPLETED),
                binding(sellers, events, RabbitMqNames.ORDER_CANCELLED),
                binding(sellers, events, RabbitMqNames.ORDER_REFUNDED),
                binding(deadLetterQueue, deadLetters, "#")
        ));
        return new Declarables(declarables);
    }

    private Queue queueWithDeadLetters(String name) {
        return QueueBuilder.durable(name)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", RabbitMqNames.DEAD_LETTER_EXCHANGE,
                        "x-dead-letter-routing-key", RabbitMqNames.DEAD_LETTER_ROUTING_KEY
                ))
                .build();
    }

    private Binding binding(Queue queue, TopicExchange exchange, String routingKey) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }
}
