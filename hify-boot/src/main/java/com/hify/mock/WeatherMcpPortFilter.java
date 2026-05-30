package com.hify.mock;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static com.hify.config.WeatherMcpPortConfig.WEATHER_MCP_PORT;

/**
 * 8082 端口的 {@code /mcp} 请求转发到天气 Mock 控制器。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WeatherMcpPortFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        if (http.getLocalPort() == WEATHER_MCP_PORT) {
            http.getRequestDispatcher("/mock-weather-internal").forward(request, response);
            return;
        }
        chain.doFilter(request, response);
    }
}
