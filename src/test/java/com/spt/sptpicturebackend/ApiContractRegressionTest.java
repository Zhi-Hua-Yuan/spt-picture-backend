package com.spt.sptpicturebackend;

import com.spt.sptpicturebackend.config.WebSocketConfig;
import com.spt.sptpicturebackend.controller.PictureController;
import com.spt.sptpicturebackend.manager.websocket.PictureEditHandler;
import com.spt.sptpicturebackend.manager.websocket.WsHandshakeInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiContractRegressionTest {

    @Test
    void keepsPictureRestSurfaceStable() {
        assertThat(PictureController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/picture");

        Set<String> paths = Arrays.stream(PictureController.class.getDeclaredMethods())
                .flatMap(method -> {
                    PostMapping post = method.getAnnotation(PostMapping.class);
                    GetMapping get = method.getAnnotation(GetMapping.class);
                    if (post != null) return Arrays.stream(post.value());
                    if (get != null) return Arrays.stream(get.value());
                    return java.util.stream.Stream.empty();
                })
                .collect(Collectors.toSet());

        assertThat(paths).contains(
                "/upload", "/upload/url", "/delete", "/edit", "/list/page/vo",
                "/search/page", "/search/suggestions", "/search/facets",
                "/batch/tasks", "/tasks/{taskId}", "/tasks/{taskId}/retry");
    }

    @Test
    void keepsPictureEditWebSocketEndpointStable() {
        PictureEditHandler handler = Mockito.mock(PictureEditHandler.class);
        WsHandshakeInterceptor interceptor = Mockito.mock(WsHandshakeInterceptor.class);
        WebSocketHandlerRegistry registry = Mockito.mock(WebSocketHandlerRegistry.class);
        WebSocketHandlerRegistration registration = Mockito.mock(WebSocketHandlerRegistration.class);
        when(registry.addHandler(handler, "/ws/picture/edit")).thenReturn(registration);
        when(registration.addInterceptors(any())).thenReturn(registration);
        when(registration.setAllowedOrigins("*")).thenReturn(registration);

        WebSocketConfig config = new WebSocketConfig();
        ReflectionTestUtils.setField(config, "pictureEditHandler", handler);
        ReflectionTestUtils.setField(config, "wsHandshakeInterceptor", interceptor);
        config.registerWebSocketHandlers(registry);

        verify(registry).addHandler(handler, "/ws/picture/edit");
        verify(registration).addInterceptors(interceptor);
        verify(registration).setAllowedOrigins("*");
    }
}
