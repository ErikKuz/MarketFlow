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
//Он автоматически создаёт и связывает exchange с q ,routing 
    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public Declarables marketFlowRabbitDeclarables() {
        TopicExchange events = new TopicExchange(RabbitMqNames.BUSINESS_EVENTS_EXCHANGE, true, false);
        TopicExchange commands = new TopicExchange(RabbitMqNames.MONEY_COMMANDS_EXCHANGE, true, false);
        //rror message
        TopicExchange deadLetters = new TopicExchange(RabbitMqNames.DEAD_LETTER_EXCHANGE, true, false);

        Queue history = queueWithDeadLetters(RabbitMqNames.ORDER_EVENT_HISTORY_QUEUE);
        Queue buyers = queueWithDeadLetters(RabbitMqNames.BUYER_NOTIFICATION_QUEUE);
        Queue sellers = queueWithDeadLetters(RabbitMqNames.SELLER_NOTIFICATION_QUEUE);
        Queue withdrawals = queueWithDeadLetters(RabbitMqNames.SELLER_WITHDRAWAL_QUEUE);
        Queue settlements = queueWithDeadLetters(RabbitMqNames.SELLER_FUNDS_RELEASE_QUEUE);
        Queue deadLetterQueue = QueueBuilder.durable(RabbitMqNames.DEAD_LETTER_QUEUE).build();

        List<Declarable> declarables = new java.util.ArrayList<>(List.of(
                events, commands, deadLetters, history, buyers, sellers,
                withdrawals, settlements, deadLetterQueue,
                binding(history, events, "order.#"),
                binding(history, events, "seller-order.#"),
                binding(history, events, "money.#"),
                binding(buyers, events, RabbitMqNames.ORDER_CREATED_EVENT),
                binding(buyers, events, RabbitMqNames.ORDER_PAID_EVENT),
                binding(buyers, events, RabbitMqNames.SELLER_SENT_PRODUCT_EVENT),
                binding(buyers, events, RabbitMqNames.ORDER_COMPLETED_EVENT),
                binding(buyers, events, RabbitMqNames.ORDER_CANCELLED_EVENT),
                binding(buyers, events, RabbitMqNames.ORDER_REFUNDED_EVENT),
                binding(sellers, events, RabbitMqNames.ORDER_PAID_EVENT),
                binding(sellers, events, RabbitMqNames.BUYER_RECEIVED_PRODUCT_EVENT),
                binding(sellers, events, "money.seller.#"),
                binding(sellers, events, RabbitMqNames.ORDER_COMPLETED_EVENT),
                binding(sellers, events, RabbitMqNames.ORDER_CANCELLED_EVENT),
                binding(sellers, events, RabbitMqNames.ORDER_REFUNDED_EVENT),
                binding(withdrawals, commands, RabbitMqNames.REQUEST_SELLER_WITHDRAWAL_COMMAND),
                binding(settlements, commands, RabbitMqNames.RELEASE_SELLER_FUNDS_COMMAND),
                binding(deadLetterQueue, deadLetters, "#")
        ));
        return new Declarables(declarables);
    }

    private Queue queueWithDeadLetters(String name) {//Если не удалось обработать сообщение то оно идет в dead letter que
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
