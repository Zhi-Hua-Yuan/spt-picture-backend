package com.spt.sptpicturebackend.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqTopology {
    public static final String EVENTS_EXCHANGE = "picture.events";
    public static final String RETRY_EXCHANGE = "picture.retry";
    public static final String DLX_EXCHANGE = "picture.dlx";
    public static final String PROCESS_QUEUE = "picture.process.q";
    public static final String BATCH_QUEUE = "picture.batch-import.q";
    public static final String FILE_DELETE_QUEUE = "picture.file-delete.q";
    public static final String SEARCH_INDEX_QUEUE = "picture.search-index.q";
    public static final String INDEX_MAINTENANCE_QUEUE = "picture.index-rebuild.q";
    public static final String PROCESS_DLQ = "picture.process.dlq";
    public static final String BATCH_DLQ = "picture.batch-import.dlq";
    public static final String FILE_DELETE_DLQ = "picture.file-delete.dlq";
    public static final String SEARCH_INDEX_DLQ = "picture.search-index.dlq";
    public static final String INDEX_MAINTENANCE_DLQ = "picture.index-rebuild.dlq";
    public static final String PARKING_EXCHANGE = "picture.parking";
    public static final String PARKING_QUEUE = "picture.parking.q";

    public static final String PROCESS_KEY = "picture.process.requested";
    public static final String BATCH_KEY = "picture.batch.requested";
    public static final String BATCH_ITEM_KEY = "picture.batch.item.requested";
    public static final String DELETE_KEY = "picture.deleted";
    public static final String CHANGED_KEY = "picture.changed";
    public static final String INDEX_REBUILD_KEY = "picture.index.rebuild.requested";
    public static final String INDEX_RECONCILE_KEY = "picture.index.reconcile.requested";
    public static final String FILES_DELETE_KEY = "picture.files.delete.requested";
    public static final String PROCESS_RETRY_RETURN_KEY = "picture.process.retry";
    public static final String BATCH_RETRY_RETURN_KEY = "picture.batch.retry";
    public static final String FILE_DELETE_RETRY_RETURN_KEY = "picture.file-delete.retry";
    public static final String SEARCH_INDEX_RETRY_RETURN_KEY = "picture.search-index.retry";
    public static final String INDEX_MAINTENANCE_RETRY_RETURN_KEY = "picture.index-rebuild.retry";

    @Bean
    TopicExchange pictureEventsExchange() {
        return ExchangeBuilder.topicExchange(EVENTS_EXCHANGE).durable(true).build();
    }

    @Bean
    DirectExchange pictureRetryExchange() {
        return ExchangeBuilder.directExchange(RETRY_EXCHANGE).durable(true).build();
    }

    @Bean
    DirectExchange pictureDlxExchange() {
        return ExchangeBuilder.directExchange(DLX_EXCHANGE).durable(true).build();
    }

    @Bean
    DirectExchange pictureParkingExchange() {
        return ExchangeBuilder.directExchange(PARKING_EXCHANGE).durable(true).build();
    }

    @Bean
    Queue pictureParkingQueue() { return QueueBuilder.durable(PARKING_QUEUE).build(); }

    @Bean
    Binding pictureParkingBinding(DirectExchange pictureParkingExchange, Queue pictureParkingQueue) {
        return BindingBuilder.bind(pictureParkingQueue).to(pictureParkingExchange).with("picture.parking");
    }

    @Bean
    Queue pictureProcessQueue() {
        return QueueBuilder.durable(PROCESS_QUEUE).build();
    }

    @Bean
    Queue pictureBatchQueue() {
        return QueueBuilder.durable(BATCH_QUEUE).build();
    }

    @Bean
    Queue pictureFileDeleteQueue() {
        return QueueBuilder.durable(FILE_DELETE_QUEUE).build();
    }

    @Bean
    Queue pictureSearchIndexQueue() { return QueueBuilder.durable(SEARCH_INDEX_QUEUE).build(); }

    @Bean
    Queue pictureIndexMaintenanceQueue() { return QueueBuilder.durable(INDEX_MAINTENANCE_QUEUE).build(); }

    @Bean
    Queue pictureProcessDlq() { return QueueBuilder.durable(PROCESS_DLQ).build(); }
    @Bean
    Queue pictureBatchDlq() { return QueueBuilder.durable(BATCH_DLQ).build(); }
    @Bean
    Queue pictureFileDeleteDlq() { return QueueBuilder.durable(FILE_DELETE_DLQ).build(); }
    @Bean
    Queue pictureSearchIndexDlq() { return QueueBuilder.durable(SEARCH_INDEX_DLQ).build(); }
    @Bean
    Queue pictureIndexMaintenanceDlq() { return QueueBuilder.durable(INDEX_MAINTENANCE_DLQ).build(); }

    @Bean
    Binding pictureProcessDlqBinding(DirectExchange pictureDlxExchange, Queue pictureProcessDlq) {
        return BindingBuilder.bind(pictureProcessDlq).to(pictureDlxExchange).with(PictureEventType.PROCESS_REQUESTED);
    }
    @Bean
    Binding pictureBatchDlqBinding(DirectExchange pictureDlxExchange, Queue pictureBatchDlq) {
        return BindingBuilder.bind(pictureBatchDlq).to(pictureDlxExchange).with("BATCH_IMPORT_REQUESTED");
    }
    @Bean
    Binding pictureBatchItemDlqBinding(DirectExchange pictureDlxExchange, Queue pictureBatchDlq) {
        return BindingBuilder.bind(pictureBatchDlq).to(pictureDlxExchange).with("BATCH_IMPORT_ITEM_REQUESTED");
    }
    @Bean
    Binding pictureFileDeleteDlqBinding(DirectExchange pictureDlxExchange, Queue pictureFileDeleteDlq) {
        return BindingBuilder.bind(pictureFileDeleteDlq).to(pictureDlxExchange).with(PictureEventType.PICTURE_DELETED);
    }
    @Bean
    Binding pictureFilesDeleteDlqBinding(DirectExchange pictureDlxExchange, Queue pictureFileDeleteDlq) {
        return BindingBuilder.bind(pictureFileDeleteDlq).to(pictureDlxExchange).with(PictureEventType.FILES_DELETE_REQUESTED);
    }

    @Bean
    Binding pictureChangedDlqBinding(DirectExchange pictureDlxExchange, Queue pictureSearchIndexDlq) {
        return BindingBuilder.bind(pictureSearchIndexDlq).to(pictureDlxExchange).with(PictureEventType.PICTURE_CHANGED);
    }

    @Bean
    Binding pictureDeletedSearchDlqBinding(DirectExchange pictureDlxExchange, Queue pictureSearchIndexDlq) {
        return BindingBuilder.bind(pictureSearchIndexDlq).to(pictureDlxExchange).with(PictureEventType.PICTURE_DELETED + ".search");
    }

    @Bean
    Binding pictureIndexMaintenanceDlqBinding(DirectExchange pictureDlxExchange, Queue pictureIndexMaintenanceDlq) {
        return BindingBuilder.bind(pictureIndexMaintenanceDlq).to(pictureDlxExchange).with("INDEX_MAINTENANCE");
    }

    @Bean
    Binding pictureProcessBinding(TopicExchange pictureEventsExchange, Queue pictureProcessQueue) {
        return BindingBuilder.bind(pictureProcessQueue).to(pictureEventsExchange).with(PROCESS_KEY);
    }

    @Bean
    Binding pictureProcessRetryReturnBinding(TopicExchange pictureEventsExchange, Queue pictureProcessQueue) {
        return BindingBuilder.bind(pictureProcessQueue).to(pictureEventsExchange).with(PROCESS_RETRY_RETURN_KEY);
    }

    @Bean
    Binding pictureBatchBinding(TopicExchange pictureEventsExchange, Queue pictureBatchQueue) {
        return BindingBuilder.bind(pictureBatchQueue).to(pictureEventsExchange).with("picture.batch.#");
    }

    @Bean
    Binding pictureDeleteBinding(TopicExchange pictureEventsExchange, Queue pictureFileDeleteQueue) {
        return BindingBuilder.bind(pictureFileDeleteQueue).to(pictureEventsExchange).with(DELETE_KEY);
    }

    @Bean
    Binding pictureFilesDeleteBinding(TopicExchange pictureEventsExchange, Queue pictureFileDeleteQueue) {
        return BindingBuilder.bind(pictureFileDeleteQueue).to(pictureEventsExchange).with(FILES_DELETE_KEY);
    }

    @Bean
    Binding pictureChangedBinding(TopicExchange pictureEventsExchange, Queue pictureSearchIndexQueue) {
        return BindingBuilder.bind(pictureSearchIndexQueue).to(pictureEventsExchange).with(CHANGED_KEY);
    }

    @Bean
    Binding pictureDeletedSearchBinding(TopicExchange pictureEventsExchange, Queue pictureSearchIndexQueue) {
        return BindingBuilder.bind(pictureSearchIndexQueue).to(pictureEventsExchange).with(DELETE_KEY);
    }

    @Bean
    Binding indexRebuildBinding(TopicExchange pictureEventsExchange, Queue pictureIndexMaintenanceQueue) {
        return BindingBuilder.bind(pictureIndexMaintenanceQueue).to(pictureEventsExchange).with(INDEX_REBUILD_KEY);
    }

    @Bean
    Binding indexReconcileBinding(TopicExchange pictureEventsExchange, Queue pictureIndexMaintenanceQueue) {
        return BindingBuilder.bind(pictureIndexMaintenanceQueue).to(pictureEventsExchange).with(INDEX_RECONCILE_KEY);
    }

    @Bean
    Binding pictureFileDeleteRetryReturnBinding(TopicExchange pictureEventsExchange, Queue pictureFileDeleteQueue) {
        return BindingBuilder.bind(pictureFileDeleteQueue).to(pictureEventsExchange).with(FILE_DELETE_RETRY_RETURN_KEY);
    }

    @Bean
    Binding pictureSearchIndexRetryReturnBinding(TopicExchange pictureEventsExchange, Queue pictureSearchIndexQueue) {
        return BindingBuilder.bind(pictureSearchIndexQueue).to(pictureEventsExchange).with(SEARCH_INDEX_RETRY_RETURN_KEY);
    }

    @Bean
    Binding pictureIndexMaintenanceRetryReturnBinding(TopicExchange pictureEventsExchange, Queue pictureIndexMaintenanceQueue) {
        return BindingBuilder.bind(pictureIndexMaintenanceQueue).to(pictureEventsExchange).with(INDEX_MAINTENANCE_RETRY_RETURN_KEY);
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

    @Bean
    Queue pictureProcessRetry10s() { return retryQueue("picture.process.retry.10s", PROCESS_KEY, 10000); }
    @Bean
    Queue pictureProcessRetry60s() { return retryQueue("picture.process.retry.60s", PROCESS_KEY, 60000); }
    @Bean
    Queue pictureProcessRetry300s() { return retryQueue("picture.process.retry.300s", PROCESS_KEY, 300000); }
    @Bean
    Queue pictureBatchRetry10s() { return retryQueue("picture.batch.retry.10s", "picture.batch.retry", 10000); }
    @Bean
    Queue pictureBatchRetry60s() { return retryQueue("picture.batch.retry.60s", "picture.batch.retry", 60000); }
    @Bean
    Queue pictureBatchRetry300s() { return retryQueue("picture.batch.retry.300s", "picture.batch.retry", 300000); }
    @Bean
    Queue pictureFileDeleteRetry10s() { return retryQueue("picture.file-delete.retry.10s", "picture.file-delete.retry", 10000); }
    @Bean
    Queue pictureFileDeleteRetry60s() { return retryQueue("picture.file-delete.retry.60s", "picture.file-delete.retry", 60000); }
    @Bean
    Queue pictureFileDeleteRetry300s() { return retryQueue("picture.file-delete.retry.300s", "picture.file-delete.retry", 300000); }
    @Bean
    Queue pictureSearchIndexRetry10s() { return retryQueue("picture.search-index.retry.10s", "picture.search-index.retry", 10000); }
    @Bean
    Queue pictureSearchIndexRetry60s() { return retryQueue("picture.search-index.retry.60s", "picture.search-index.retry", 60000); }
    @Bean
    Queue pictureSearchIndexRetry300s() { return retryQueue("picture.search-index.retry.300s", "picture.search-index.retry", 300000); }
    @Bean
    Queue pictureIndexMaintenanceRetry10s() { return retryQueue("picture.index-rebuild.retry.10s", "picture.index-rebuild.retry", 10000); }
    @Bean
    Queue pictureIndexMaintenanceRetry60s() { return retryQueue("picture.index-rebuild.retry.60s", "picture.index-rebuild.retry", 60000); }
    @Bean
    Queue pictureIndexMaintenanceRetry300s() { return retryQueue("picture.index-rebuild.retry.300s", "picture.index-rebuild.retry", 300000); }

    private Queue retryQueue(String name, String returnKey, int ttl) {
        return QueueBuilder.durable(name)
                .ttl(ttl)
                .deadLetterExchange(EVENTS_EXCHANGE)
                .deadLetterRoutingKey(returnKey)
                .build();
    }

    @Bean
    Binding processRetry10Binding(DirectExchange pictureRetryExchange, Queue pictureProcessRetry10s) { return BindingBuilder.bind(pictureProcessRetry10s).to(pictureRetryExchange).with("picture.process.retry.1"); }
    @Bean
    Binding processRetry60Binding(DirectExchange pictureRetryExchange, Queue pictureProcessRetry60s) { return BindingBuilder.bind(pictureProcessRetry60s).to(pictureRetryExchange).with("picture.process.retry.2"); }
    @Bean
    Binding processRetry300Binding(DirectExchange pictureRetryExchange, Queue pictureProcessRetry300s) { return BindingBuilder.bind(pictureProcessRetry300s).to(pictureRetryExchange).with("picture.process.retry.3"); }
    @Bean
    Binding batchRetry10Binding(DirectExchange pictureRetryExchange, Queue pictureBatchRetry10s) { return BindingBuilder.bind(pictureBatchRetry10s).to(pictureRetryExchange).with("picture.batch.retry.1"); }
    @Bean
    Binding batchRetry60Binding(DirectExchange pictureRetryExchange, Queue pictureBatchRetry60s) { return BindingBuilder.bind(pictureBatchRetry60s).to(pictureRetryExchange).with("picture.batch.retry.2"); }
    @Bean
    Binding batchRetry300Binding(DirectExchange pictureRetryExchange, Queue pictureBatchRetry300s) { return BindingBuilder.bind(pictureBatchRetry300s).to(pictureRetryExchange).with("picture.batch.retry.3"); }
    @Bean
    Binding fileDeleteRetry10Binding(DirectExchange pictureRetryExchange, Queue pictureFileDeleteRetry10s) { return BindingBuilder.bind(pictureFileDeleteRetry10s).to(pictureRetryExchange).with("picture.file-delete.retry.1"); }
    @Bean
    Binding fileDeleteRetry60Binding(DirectExchange pictureRetryExchange, Queue pictureFileDeleteRetry60s) { return BindingBuilder.bind(pictureFileDeleteRetry60s).to(pictureRetryExchange).with("picture.file-delete.retry.2"); }
    @Bean
    Binding fileDeleteRetry300Binding(DirectExchange pictureRetryExchange, Queue pictureFileDeleteRetry300s) { return BindingBuilder.bind(pictureFileDeleteRetry300s).to(pictureRetryExchange).with("picture.file-delete.retry.3"); }
    @Bean
    Binding searchIndexRetry10Binding(DirectExchange pictureRetryExchange, Queue pictureSearchIndexRetry10s) { return BindingBuilder.bind(pictureSearchIndexRetry10s).to(pictureRetryExchange).with("picture.search-index.retry.1"); }
    @Bean
    Binding searchIndexRetry60Binding(DirectExchange pictureRetryExchange, Queue pictureSearchIndexRetry60s) { return BindingBuilder.bind(pictureSearchIndexRetry60s).to(pictureRetryExchange).with("picture.search-index.retry.2"); }
    @Bean
    Binding searchIndexRetry300Binding(DirectExchange pictureRetryExchange, Queue pictureSearchIndexRetry300s) { return BindingBuilder.bind(pictureSearchIndexRetry300s).to(pictureRetryExchange).with("picture.search-index.retry.3"); }
    @Bean
    Binding indexMaintenanceRetry10Binding(DirectExchange pictureRetryExchange, Queue pictureIndexMaintenanceRetry10s) { return BindingBuilder.bind(pictureIndexMaintenanceRetry10s).to(pictureRetryExchange).with("picture.index-rebuild.retry.1"); }
    @Bean
    Binding indexMaintenanceRetry60Binding(DirectExchange pictureRetryExchange, Queue pictureIndexMaintenanceRetry60s) { return BindingBuilder.bind(pictureIndexMaintenanceRetry60s).to(pictureRetryExchange).with("picture.index-rebuild.retry.2"); }
    @Bean
    Binding indexMaintenanceRetry300Binding(DirectExchange pictureRetryExchange, Queue pictureIndexMaintenanceRetry300s) { return BindingBuilder.bind(pictureIndexMaintenanceRetry300s).to(pictureRetryExchange).with("picture.index-rebuild.retry.3"); }
}
