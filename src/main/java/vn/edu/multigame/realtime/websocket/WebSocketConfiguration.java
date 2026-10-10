package vn.edu.multigame.realtime.websocket;

import org.springframework.context.annotation.*;
import org.springframework.web.socket.config.annotation.*;
import vn.edu.multigame.common.config.WebAccessProperties;

@Configuration
@Profile("mysql")
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {
    private final AuthenticatedWebSocketHandler handler;
    private final AuthenticatedHandshakeInterceptor authentication;
    private final WebAccessProperties origins;
    public WebSocketConfiguration(AuthenticatedWebSocketHandler handler,
            AuthenticatedHandshakeInterceptor authentication, WebAccessProperties origins) {
        this.handler = handler; this.authentication = authentication; this.origins = origins;
    }
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws").addInterceptors(authentication)
                .setAllowedOrigins(origins.allowedOrigins().toArray(String[]::new));
    }
}
