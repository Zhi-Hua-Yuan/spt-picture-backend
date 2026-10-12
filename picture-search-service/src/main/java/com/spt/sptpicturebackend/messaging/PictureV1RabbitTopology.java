package com.spt.sptpicturebackend.messaging;

import com.spt.sptpicturebackend.messaging.v1.PictureRoutingV1;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.amqp.core.DirectExchange;

/** Search-side declarations for the database-autonomy event routes. */
@Configuration
public class PictureV1RabbitTopology {
    @Bean(name = "pictureV1EventsExchange")
    TopicExchange pictureV1EventsExchange() {
        return ExchangeBuilder.topicExchange(PictureRoutingV1.EVENTS_EXCHANGE).durable(true).build();
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
    Binding pictureV1ParkingBinding(@Qualifier("pictureV1ParkingExchange") DirectExchange exchange,
                                    @Qualifier("pictureV1ParkingQueue") Queue queue) {
        return BindingBuilder.bind(queue).to(exchange).with("picture.parking.v1");
    }

    @Bean(name = "pictureV1SearchDlq") Queue pictureV1SearchDlq() { return QueueBuilder.durable(PictureRoutingV1.SEARCH_INDEX_DLQ).build(); }
    @Bean(name = "pictureV1MaintenanceDlq") Queue pictureV1MaintenanceDlq() { return QueueBuilder.durable(PictureRoutingV1.SEARCH_MAINTENANCE_DLQ).build(); }
    @Bean Binding pictureV1SearchDlqBinding(@Qualifier("pictureV1DlxExchange") DirectExchange exchange, @Qualifier("pictureV1SearchDlq") Queue queue) { return BindingBuilder.bind(queue).to(exchange).with(PictureRoutingV1.SEARCH_INDEX_DLQ); }
    @Bean Binding pictureV1MaintenanceDlqBinding(@Qualifier("pictureV1DlxExchange") DirectExchange exchange, @Qualifier("pictureV1MaintenanceDlq") Queue queue) { return BindingBuilder.bind(queue).to(exchange).with(PictureRoutingV1.SEARCH_MAINTENANCE_DLQ); }

    @Bean(name = "pictureV1SearchIndexQueue")
    Queue pictureV1SearchIndexQueue() { return QueueBuilder.durable(PictureRoutingV1.SEARCH_INDEX_QUEUE).build(); }

    @Bean(name = "pictureV1MaintenanceQueue")
    Queue pictureV1MaintenanceQueue() { return QueueBuilder.durable(PictureRoutingV1.SEARCH_MAINTENANCE_QUEUE).build(); }

    @Bean
    Binding pictureV1UpsertBinding(@Qualifier("pictureV1EventsExchange") TopicExchange pictureV1EventsExchange,
                                   @Qualifier("pictureV1SearchIndexQueue") Queue pictureV1SearchIndexQueue) {
        return BindingBuilder.bind(pictureV1SearchIndexQueue).to(pictureV1EventsExchange).with(PictureRoutingV1.SEARCH_INDEX_UPSERTED);
    }

    @Bean
    Binding pictureV1DeleteBinding(@Qualifier("pictureV1EventsExchange") TopicExchange pictureV1EventsExchange,
                                   @Qualifier("pictureV1SearchIndexQueue") Queue pictureV1SearchIndexQueue) {
        return BindingBuilder.bind(pictureV1SearchIndexQueue).to(pictureV1EventsExchange).with(PictureRoutingV1.SEARCH_INDEX_DELETED);
    }

    @Bean
    Binding pictureV1MaintenanceBinding(@Qualifier("pictureV1EventsExchange") TopicExchange pictureV1EventsExchange,
                                        @Qualifier("pictureV1MaintenanceQueue") Queue pictureV1MaintenanceQueue) {
        return BindingBuilder.bind(pictureV1MaintenanceQueue).to(pictureV1EventsExchange).with(PictureRoutingV1.SEARCH_MAINTENANCE_REQUESTED);
    }

    @Bean(name = "pictureV1SearchRetry10Queue") Queue pictureV1SearchRetry10Queue() { return retryQueue("picture.search.index.retry.v1.10s", PictureRoutingV1.SEARCH_INDEX_UPSERTED, 10000); }
    @Bean(name = "pictureV1SearchRetry60Queue") Queue pictureV1SearchRetry60Queue() { return retryQueue("picture.search.index.retry.v1.60s", PictureRoutingV1.SEARCH_INDEX_UPSERTED, 60000); }
    @Bean(name = "pictureV1SearchRetry300Queue") Queue pictureV1SearchRetry300Queue() { return retryQueue("picture.search.index.retry.v1.300s", PictureRoutingV1.SEARCH_INDEX_UPSERTED, 300000); }
    @Bean(name = "pictureV1MaintenanceRetry10Queue") Queue pictureV1MaintenanceRetry10Queue() { return retryQueue("picture.search.maintenance.retry.v1.10s", PictureRoutingV1.SEARCH_MAINTENANCE_REQUESTED, 10000); }
    @Bean(name = "pictureV1MaintenanceRetry60Queue") Queue pictureV1MaintenanceRetry60Queue() { return retryQueue("picture.search.maintenance.retry.v1.60s", PictureRoutingV1.SEARCH_MAINTENANCE_REQUESTED, 60000); }
    @Bean(name = "pictureV1MaintenanceRetry300Queue") Queue pictureV1MaintenanceRetry300Queue() { return retryQueue("picture.search.maintenance.retry.v1.300s", PictureRoutingV1.SEARCH_MAINTENANCE_REQUESTED, 300000); }

    @Bean Binding pictureV1SearchRetry10(@Qualifier("pictureV1RetryExchange") DirectExchange exchange, @Qualifier("pictureV1SearchRetry10Queue") Queue queue) { return retryBinding(exchange, queue, PictureRoutingV1.SEARCH_INDEX_RETRY); }
    @Bean Binding pictureV1SearchRetry60(@Qualifier("pictureV1RetryExchange") DirectExchange exchange, @Qualifier("pictureV1SearchRetry60Queue") Queue queue) { return retryBinding(exchange, queue, PictureRoutingV1.SEARCH_INDEX_RETRY + ".60"); }
    @Bean Binding pictureV1SearchRetry300(@Qualifier("pictureV1RetryExchange") DirectExchange exchange, @Qualifier("pictureV1SearchRetry300Queue") Queue queue) { return retryBinding(exchange, queue, PictureRoutingV1.SEARCH_INDEX_RETRY + ".300"); }
    @Bean Binding pictureV1MaintenanceRetry10(@Qualifier("pictureV1RetryExchange") DirectExchange exchange, @Qualifier("pictureV1MaintenanceRetry10Queue") Queue queue) { return retryBinding(exchange, queue, PictureRoutingV1.SEARCH_MAINTENANCE_RETRY); }
    @Bean Binding pictureV1MaintenanceRetry60(@Qualifier("pictureV1RetryExchange") DirectExchange exchange, @Qualifier("pictureV1MaintenanceRetry60Queue") Queue queue) { return retryBinding(exchange, queue, PictureRoutingV1.SEARCH_MAINTENANCE_RETRY + ".60"); }
    @Bean Binding pictureV1MaintenanceRetry300(@Qualifier("pictureV1RetryExchange") DirectExchange exchange, @Qualifier("pictureV1MaintenanceRetry300Queue") Queue queue) { return retryBinding(exchange, queue, PictureRoutingV1.SEARCH_MAINTENANCE_RETRY + ".300"); }

    private Queue retryQueue(String queueName, String targetKey, int ttl) {
        return QueueBuilder.durable(queueName).ttl(ttl)
                .deadLetterExchange(PictureRoutingV1.EVENTS_EXCHANGE)
                .deadLetterRoutingKey(targetKey).build();
    }

    private Binding retryBinding(DirectExchange exchange, Queue queue, String retryKey) {
        return BindingBuilder.bind(queue).to(exchange).with(retryKey);
    }
}
