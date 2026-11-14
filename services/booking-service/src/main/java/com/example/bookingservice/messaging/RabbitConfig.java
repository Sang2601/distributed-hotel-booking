package com.example.bookingservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chương 2, 4 – Kiến trúc hướng sự kiện qua RabbitMQ.
 *
 *  payment-service ──publish──> [hotel.events] ──payment.completed──> [booking.payment-completed] ──> booking-service
 *                                                                              │ (xử lý lỗi 3 lần)
 *                                                                              ▼
 *                                          [hotel.events.dlx] ──> [booking.payment-completed.dlq]
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "hotel.events";
    public static final String PAYMENT_COMPLETED_KEY = "payment.completed";
    public static final String PAYMENT_COMPLETED_QUEUE = "booking.payment-completed";

    public static final String DEAD_LETTER_EXCHANGE = "hotel.events.dlx";
    public static final String PAYMENT_COMPLETED_DLQ = "booking.payment-completed.dlq";

    @Bean
    public TopicExchange hotelEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue paymentCompletedQueue() {
        return QueueBuilder.durable(PAYMENT_COMPLETED_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", PAYMENT_COMPLETED_DLQ)
                .build();
    }

    @Bean
    public Queue paymentCompletedDeadLetterQueue() {
        return QueueBuilder.durable(PAYMENT_COMPLETED_DLQ).build();
    }

    @Bean
    public Binding paymentCompletedBinding() {
        return BindingBuilder.bind(paymentCompletedQueue())
                .to(hotelEventsExchange())
                .with(PAYMENT_COMPLETED_KEY);
    }

    @Bean
    public Binding paymentCompletedDeadLetterBinding() {
        return BindingBuilder.bind(paymentCompletedDeadLetterQueue())
                .to(deadLetterExchange())
                .with(PAYMENT_COMPLETED_DLQ);
    }

    /** Message dạng JSON, chuyển thẳng sang kiểu tham số của @RabbitListener. */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }
}
