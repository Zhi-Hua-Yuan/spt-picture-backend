package com.spt.sptpicturebackend.messaging;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.spt.sptpicturebackend.messaging.v1.PictureRoutingV1;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;

/**
 * Core API owns the V1 event ingress exchange and the result-consumer topology.
 * Media and Search declare their own queue bindings and retry routes.
 */
@Configuration
public class RabbitMqTopology {
    @Bean
    TopicExchange pictureV1EventsExchange() {
        return ExchangeBuilder.topicExchange(PictureRoutingV1.EVENTS_EXCHANGE).durable(true).build();
    }

    @Bean(name = "pictureV1CoreMediaResultQueue")
    Queue pictureV1CoreMediaResultQueue() {
        return QueueBuilder.durable(PictureRoutingV1.CORE_MEDIA_RESULT_QUEUE).build();
    }

    @Bean
    Binding pictureV1CoreMediaResultBinding(
            @org.springframework.beans.factory.annotation.Qualifier("pictureV1EventsExchange") TopicExchange exchange,
            @org.springframework.beans.factory.annotation.Qualifier("pictureV1CoreMediaResultQueue") Queue queue) {
        return BindingBuilder.bind(queue).to(exchange).with(PictureRoutingV1.MEDIA_RESULT);
    }

    @Bean(name = "pictureV1RetryExchange")
    DirectExchange pictureV1RetryExchange() { return ExchangeBuilder.directExchange(PictureRoutingV1.RETRY_EXCHANGE).durable(true).build(); }

    @Bean(name = "pictureV1DlxExchange")
    DirectExchange pictureV1DlxExchange() { return ExchangeBuilder.directExchange(PictureRoutingV1.DLX_EXCHANGE).durable(true).build(); }

    @Bean(name = "pictureV1ParkingExchange")
    DirectExchange pictureV1ParkingExchange() { return ExchangeBuilder.directExchange(PictureRoutingV1.PARKING_EXCHANGE).durable(true).build(); }

    @Bean(name = "pictureV1ParkingQueue")
    Queue pictureV1ParkingQueue() { return QueueBuilder.durable(PictureRoutingV1.PARKING_QUEUE).build(); }

    @Bean
    Binding pictureV1ParkingBinding(
            @org.springframework.beans.factory.annotation.Qualifier("pictureV1ParkingExchange") DirectExchange exchange,
            @org.springframework.beans.factory.annotation.Qualifier("pictureV1ParkingQueue") Queue queue) {
        return BindingBuilder.bind(queue).to(exchange).with("picture.parking.v1");
    }

    @Bean(name = "pictureV1CoreResultDlq") Queue pictureV1CoreResultDlq() { return QueueBuilder.durable(PictureRoutingV1.CORE_MEDIA_RESULT_DLQ).build(); }
    @Bean Binding pictureV1CoreResultDlqBinding(@org.springframework.beans.factory.annotation.Qualifier("pictureV1DlxExchange") DirectExchange exchange, @org.springframework.beans.factory.annotation.Qualifier("pictureV1CoreResultDlq") Queue queue) { return BindingBuilder.bind(queue).to(exchange).with(PictureRoutingV1.CORE_MEDIA_RESULT_DLQ); }

    @Bean(name = "pictureV1CoreResultRetry10Queue") Queue pictureV1CoreResultRetry10Queue() { return retryQueue("picture.core.media-result.retry.v1.10s", PictureRoutingV1.MEDIA_RESULT, 10000); }
    @Bean(name = "pictureV1CoreResultRetry60Queue") Queue pictureV1CoreResultRetry60Queue() { return retryQueue("picture.core.media-result.retry.v1.60s", PictureRoutingV1.MEDIA_RESULT, 60000); }
    @Bean(name = "pictureV1CoreResultRetry300Queue") Queue pictureV1CoreResultRetry300Queue() { return retryQueue("picture.core.media-result.retry.v1.300s", PictureRoutingV1.MEDIA_RESULT, 300000); }

    @Bean Binding pictureV1CoreResultRetry10(@org.springframework.beans.factory.annotation.Qualifier("pictureV1RetryExchange") DirectExchange exchange, @org.springframework.beans.factory.annotation.Qualifier("pictureV1CoreResultRetry10Queue") Queue queue) { return retryBinding(exchange, queue, PictureRoutingV1.CORE_MEDIA_RESULT_RETRY); }
    @Bean Binding pictureV1CoreResultRetry60(@org.springframework.beans.factory.annotation.Qualifier("pictureV1RetryExchange") DirectExchange exchange, @org.springframework.beans.factory.annotation.Qualifier("pictureV1CoreResultRetry60Queue") Queue queue) { return retryBinding(exchange, queue, PictureRoutingV1.CORE_MEDIA_RESULT_RETRY + ".60"); }
    @Bean Binding pictureV1CoreResultRetry300(@org.springframework.beans.factory.annotation.Qualifier("pictureV1RetryExchange") DirectExchange exchange, @org.springframework.beans.factory.annotation.Qualifier("pictureV1CoreResultRetry300Queue") Queue queue) { return retryBinding(exchange, queue, PictureRoutingV1.CORE_MEDIA_RESULT_RETRY + ".300"); }

    private Queue retryQueue(String queueName, String targetKey, int ttl) {
        return QueueBuilder.durable(queueName).ttl(ttl)
                .deadLetterExchange(PictureRoutingV1.EVENTS_EXCHANGE)
                .deadLetterRoutingKey(targetKey).build();
    }

    private Binding retryBinding(DirectExchange exchange, Queue queue, String retryKey) {
        return BindingBuilder.bind(queue).to(exchange).with(retryKey);
    }

    @Bean
    RabbitTemplate rabbitTemplate(CachingConnectionFactory connectionFactory) {
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connectionFactory.setPublisherReturns(true);
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new SimpleMessageConverter());
        template.setMandatory(true);
        return template;
    }
}
