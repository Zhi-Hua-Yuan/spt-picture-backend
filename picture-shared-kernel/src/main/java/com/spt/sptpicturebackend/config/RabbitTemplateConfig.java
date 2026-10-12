package com.spt.sptpicturebackend.config;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Common publisher settings used by the three applications. */
@Configuration
public class RabbitTemplateConfig {
    @Bean
    @ConditionalOnMissingBean(RabbitTemplate.class)
    RabbitTemplate sharedRabbitTemplate(CachingConnectionFactory connectionFactory) {
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connectionFactory.setPublisherReturns(true);
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        MessageConverter converter = new SimpleMessageConverter();
        template.setMessageConverter(converter);
        template.setMandatory(true);
        return template;
    }
}
