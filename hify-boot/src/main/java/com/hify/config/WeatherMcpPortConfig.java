package com.hify.config;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 为内置 Mock 天气 MCP 服务额外暴露 8082 端口。
 * MCP Java SDK 固定连接 {@code /mcp}，同端口无法区分多个 Mock 服务。
 */
@Configuration
public class WeatherMcpPortConfig {

    public static final int WEATHER_MCP_PORT = 8082;

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> weatherMcpConnector() {
        return factory -> factory.addAdditionalTomcatConnectors(createConnector(WEATHER_MCP_PORT));
    }

    private Connector createConnector(int port) {
        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setPort(port);
        return connector;
    }
}
