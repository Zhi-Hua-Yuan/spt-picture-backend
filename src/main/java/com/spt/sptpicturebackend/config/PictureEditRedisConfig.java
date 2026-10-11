package com.spt.sptpicturebackend.config;

import com.spt.sptpicturebackend.manager.websocket.PictureEditHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class PictureEditRedisConfig {
    @Bean
    RedisMessageListenerContainer pictureEditRedisListenerContainer(
            RedisConnectionFactory connectionFactory,
            PictureEditHandler handler,
            PictureWebSocketProperties properties) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(handler, new ChannelTopic(properties.getChannel()));
        return container;
    }
}
