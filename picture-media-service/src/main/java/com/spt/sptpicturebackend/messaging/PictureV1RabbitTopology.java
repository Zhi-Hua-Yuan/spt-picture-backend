package com.spt.sptpicturebackend.messaging;

import com.spt.sptpicturebackend.messaging.v1.PictureRoutingV1;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.core.DirectExchange;

@Configuration
public class PictureV1RabbitTopology {
    @Bean(name = "pictureV1EventsExchange")
    TopicExchange pictureV1EventsExchange() { return ExchangeBuilder.topicExchange(PictureRoutingV1.EVENTS_EXCHANGE).durable(true).build(); }

    @Bean(name = "pictureV1RetryExchange")
    DirectExchange pictureV1RetryExchange() { return ExchangeBuilder.directExchange(PictureRoutingV1.RETRY_EXCHANGE).durable(true).build(); }

    @Bean(name = "pictureV1DlxExchange")
    DirectExchange pictureV1DlxExchange() { return ExchangeBuilder.directExchange(PictureRoutingV1.DLX_EXCHANGE).durable(true).build(); }

    @Bean(name = "pictureV1ParkingExchange")
    DirectExchange pictureV1ParkingExchange() { return ExchangeBuilder.directExchange(PictureRoutingV1.PARKING_EXCHANGE).durable(true).build(); }

    @Bean(name = "pictureV1ParkingQueue")
    Queue pictureV1ParkingQueue() { return QueueBuilder.durable(PictureRoutingV1.PARKING_QUEUE).build(); }

    @Bean
    Binding pictureV1ParkingBinding(@Qualifier("pictureV1ParkingExchange") DirectExchange exchange,
                                    @Qualifier("pictureV1ParkingQueue") Queue queue) {
        return BindingBuilder.bind(queue).to(exchange).with("picture.parking.v1");
    }

    @Bean(name = "pictureV1ProcessDlq") Queue pictureV1ProcessDlq() { return QueueBuilder.durable(PictureRoutingV1.MEDIA_PROCESS_DLQ).build(); }
    @Bean(name = "pictureV1BatchDlq") Queue pictureV1BatchDlq() { return QueueBuilder.durable(PictureRoutingV1.MEDIA_BATCH_DLQ).build(); }
    @Bean(name = "pictureV1FileDeleteDlq") Queue pictureV1FileDeleteDlq() { return QueueBuilder.durable(PictureRoutingV1.MEDIA_FILE_DELETE_DLQ).build(); }
    @Bean Binding pictureV1ProcessDlqBinding(@Qualifier("pictureV1DlxExchange") DirectExchange exchange, @Qualifier("pictureV1ProcessDlq") Queue queue) { return BindingBuilder.bind(queue).to(exchange).with(PictureRoutingV1.MEDIA_PROCESS_DLQ); }
    @Bean Binding pictureV1BatchDlqBinding(@Qualifier("pictureV1DlxExchange") DirectExchange exchange, @Qualifier("pictureV1BatchDlq") Queue queue) { return BindingBuilder.bind(queue).to(exchange).with(PictureRoutingV1.MEDIA_BATCH_DLQ); }
    @Bean Binding pictureV1FileDeleteDlqBinding(@Qualifier("pictureV1DlxExchange") DirectExchange exchange, @Qualifier("pictureV1FileDeleteDlq") Queue queue) { return BindingBuilder.bind(queue).to(exchange).with(PictureRoutingV1.MEDIA_FILE_DELETE_DLQ); }

    @Bean(name = "pictureV1ProcessQueue")
    Queue pictureV1ProcessQueue() { return QueueBuilder.durable(PictureRoutingV1.MEDIA_PROCESS_QUEUE).build(); }
    @Bean(name = "pictureV1BatchQueue")
    Queue pictureV1BatchQueue() { return QueueBuilder.durable(PictureRoutingV1.MEDIA_BATCH_QUEUE).build(); }
    @Bean(name = "pictureV1FileDeleteQueue")
    Queue pictureV1FileDeleteQueue() { return QueueBuilder.durable(PictureRoutingV1.MEDIA_FILE_DELETE_QUEUE).build(); }

    @Bean Binding pictureV1ProcessBinding(@Qualifier("pictureV1EventsExchange") TopicExchange exchange,
                                          @Qualifier("pictureV1ProcessQueue") Queue queue) { return BindingBuilder.bind(queue).to(exchange).with(PictureRoutingV1.MEDIA_PROCESS_REQUESTED); }
    @Bean Binding pictureV1BatchBinding(@Qualifier("pictureV1EventsExchange") TopicExchange exchange,
                                        @Qualifier("pictureV1BatchQueue") Queue queue) { return BindingBuilder.bind(queue).to(exchange).with(PictureRoutingV1.MEDIA_BATCH_REQUESTED); }
    @Bean Binding pictureV1FileDeleteBinding(@Qualifier("pictureV1EventsExchange") TopicExchange exchange,
                                             @Qualifier("pictureV1FileDeleteQueue") Queue queue) { return BindingBuilder.bind(queue).to(exchange).with(PictureRoutingV1.MEDIA_FILE_DELETE_REQUESTED); }

    @Bean(name = "pictureV1ProcessRetry10Queue") Queue pictureV1ProcessRetry10Queue() { return retryQueue("picture.media.process.retry.v1.10s", PictureRoutingV1.MEDIA_PROCESS_REQUESTED, 10000); }
    @Bean(name = "pictureV1ProcessRetry60Queue") Queue pictureV1ProcessRetry60Queue() { return retryQueue("picture.media.process.retry.v1.60s", PictureRoutingV1.MEDIA_PROCESS_REQUESTED, 60000); }
    @Bean(name = "pictureV1ProcessRetry300Queue") Queue pictureV1ProcessRetry300Queue() { return retryQueue("picture.media.process.retry.v1.300s", PictureRoutingV1.MEDIA_PROCESS_REQUESTED, 300000); }
    @Bean(name = "pictureV1BatchRetry10Queue") Queue pictureV1BatchRetry10Queue() { return retryQueue("picture.media.batch.retry.v1.10s", PictureRoutingV1.MEDIA_BATCH_REQUESTED, 10000); }
    @Bean(name = "pictureV1BatchRetry60Queue") Queue pictureV1BatchRetry60Queue() { return retryQueue("picture.media.batch.retry.v1.60s", PictureRoutingV1.MEDIA_BATCH_REQUESTED, 60000); }
    @Bean(name = "pictureV1BatchRetry300Queue") Queue pictureV1BatchRetry300Queue() { return retryQueue("picture.media.batch.retry.v1.300s", PictureRoutingV1.MEDIA_BATCH_REQUESTED, 300000); }
    @Bean(name = "pictureV1DeleteRetry10Queue") Queue pictureV1DeleteRetry10Queue() { return retryQueue("picture.media.delete.retry.v1.10s", PictureRoutingV1.MEDIA_FILE_DELETE_REQUESTED, 10000); }
    @Bean(name = "pictureV1DeleteRetry60Queue") Queue pictureV1DeleteRetry60Queue() { return retryQueue("picture.media.delete.retry.v1.60s", PictureRoutingV1.MEDIA_FILE_DELETE_REQUESTED, 60000); }
    @Bean(name = "pictureV1DeleteRetry300Queue") Queue pictureV1DeleteRetry300Queue() { return retryQueue("picture.media.delete.retry.v1.300s", PictureRoutingV1.MEDIA_FILE_DELETE_REQUESTED, 300000); }

    @Bean Binding pictureV1ProcessRetry10(@Qualifier("pictureV1RetryExchange") DirectExchange exchange, @Qualifier("pictureV1ProcessRetry10Queue") Queue queue) { return retryBinding(exchange, queue, PictureRoutingV1.MEDIA_PROCESS_RETRY); }
    @Bean Binding pictureV1ProcessRetry60(@Qualifier("pictureV1RetryExchange") DirectExchange exchange, @Qualifier("pictureV1ProcessRetry60Queue") Queue queue) { return retryBinding(exchange, queue, PictureRoutingV1.MEDIA_PROCESS_RETRY + ".60"); }
    @Bean Binding pictureV1ProcessRetry300(@Qualifier("pictureV1RetryExchange") DirectExchange exchange, @Qualifier("pictureV1ProcessRetry300Queue") Queue queue) { return retryBinding(exchange, queue, PictureRoutingV1.MEDIA_PROCESS_RETRY + ".300"); }
    @Bean Binding pictureV1BatchRetry10(@Qualifier("pictureV1RetryExchange") DirectExchange exchange, @Qualifier("pictureV1BatchRetry10Queue") Queue queue) { return retryBinding(exchange, queue, PictureRoutingV1.MEDIA_BATCH_RETRY); }
    @Bean Binding pictureV1BatchRetry60(@Qualifier("pictureV1RetryExchange") DirectExchange exchange, @Qualifier("pictureV1BatchRetry60Queue") Queue queue) { return retryBinding(exchange, queue, PictureRoutingV1.MEDIA_BATCH_RETRY + ".60"); }
    @Bean Binding pictureV1BatchRetry300(@Qualifier("pictureV1RetryExchange") DirectExchange exchange, @Qualifier("pictureV1BatchRetry300Queue") Queue queue) { return retryBinding(exchange, queue, PictureRoutingV1.MEDIA_BATCH_RETRY + ".300"); }
    @Bean Binding pictureV1DeleteRetry10(@Qualifier("pictureV1RetryExchange") DirectExchange exchange, @Qualifier("pictureV1DeleteRetry10Queue") Queue queue) { return retryBinding(exchange, queue, PictureRoutingV1.MEDIA_FILE_DELETE_RETRY); }
    @Bean Binding pictureV1DeleteRetry60(@Qualifier("pictureV1RetryExchange") DirectExchange exchange, @Qualifier("pictureV1DeleteRetry60Queue") Queue queue) { return retryBinding(exchange, queue, PictureRoutingV1.MEDIA_FILE_DELETE_RETRY + ".60"); }
    @Bean Binding pictureV1DeleteRetry300(@Qualifier("pictureV1RetryExchange") DirectExchange exchange, @Qualifier("pictureV1DeleteRetry300Queue") Queue queue) { return retryBinding(exchange, queue, PictureRoutingV1.MEDIA_FILE_DELETE_RETRY + ".300"); }

    private Queue retryQueue(String queueName, String targetKey, int ttl) {
        return QueueBuilder.durable(queueName).ttl(ttl)
                .deadLetterExchange(PictureRoutingV1.EVENTS_EXCHANGE)
                .deadLetterRoutingKey(targetKey).build();
    }

    private Binding retryBinding(DirectExchange exchange, Queue queue, String retryKey) {
        return BindingBuilder.bind(queue).to(exchange).with(retryKey);
    }
}
