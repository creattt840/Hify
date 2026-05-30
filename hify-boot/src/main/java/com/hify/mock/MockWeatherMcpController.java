package com.hify.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 模拟天气 MCP Server —— 实现 MCP Streamable HTTP 协议。
 * 端点：{@code http://127.0.0.1:8082/mcp}（8082 端口由 {@link com.hify.mock.WeatherMcpPortFilter} 转发）
 */
@RestController
public class MockWeatherMcpController {

    private static final Logger log = LoggerFactory.getLogger(MockWeatherMcpController.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private static final CopyOnWriteArrayList<PrintWriter> sseClients = new CopyOnWriteArrayList<>();

    /** 城市 → 当前天气 */
    private static final Map<String, Map<String, String>> WEATHER = Map.of(
            "北京", Map.of("temp", "18", "condition", "晴", "humidity", "35", "wind", "北风 3级", "aqi", "良"),
            "上海", Map.of("temp", "22", "condition", "多云", "humidity", "68", "wind", "东南风 2级", "aqi", "优"),
            "广州", Map.of("temp", "28", "condition", "阵雨", "humidity", "82", "wind", "南风 2级", "aqi", "良"),
            "深圳", Map.of("temp", "27", "condition", "阴", "humidity", "75", "wind", "东南风 3级", "aqi", "优"),
            "杭州", Map.of("temp", "20", "condition", "小雨", "humidity", "88", "wind", "东北风 2级", "aqi", "良"),
            "成都", Map.of("temp", "16", "condition", "雾", "humidity", "90", "wind", "微风", "aqi", "轻度污染"),
            "武汉", Map.of("temp", "21", "condition", "晴", "humidity", "55", "wind", "西北风 2级", "aqi", "优"),
            "西安", Map.of("temp", "15", "condition", "晴", "humidity", "40", "wind", "西风 3级", "aqi", "良")
    );

    /** 城市 → 未来三天预报 */
    private static final Map<String, List<Map<String, String>>> FORECAST = Map.of(
            "北京", List.of(
                    Map.of("date", "今天", "high", "20", "low", "10", "condition", "晴"),
                    Map.of("date", "明天", "high", "22", "low", "12", "condition", "多云"),
                    Map.of("date", "后天", "high", "19", "low", "11", "condition", "阴")
            ),
            "上海", List.of(
                    Map.of("date", "今天", "high", "24", "low", "18", "condition", "多云"),
                    Map.of("date", "明天", "high", "26", "low", "19", "condition", "晴"),
                    Map.of("date", "后天", "high", "25", "low", "20", "condition", "阵雨")
            ),
            "广州", List.of(
                    Map.of("date", "今天", "high", "30", "low", "24", "condition", "阵雨"),
                    Map.of("date", "明天", "high", "31", "low", "25", "condition", "雷阵雨"),
                    Map.of("date", "后天", "high", "29", "low", "24", "condition", "多云")
            )
    );

    @RequestMapping({"/mock-weather-internal"})
    public ResponseEntity<?> handle(HttpServletRequest req, HttpServletResponse resp) {
        String method = req.getMethod();
        log.info("[MockWeatherMCP] {} {}", method, req.getRequestURI());

        if ("GET".equalsIgnoreCase(method)) {
            return handleSse(resp);
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            return ResponseEntity.ok("{}");
        }

        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = req.getReader()) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            JsonNode request = mapper.readTree(sb.toString());
            String rpcMethod = request.path("method").asText("");
            JsonNode id = request.get("id");

            log.info("[MockWeatherMCP] RPC method={}", rpcMethod);

            String response = handleJsonRpc(rpcMethod, id, request);
            if (response != null) {
                broadcastSse("message", response);
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.ok("");
        } catch (Exception ex) {
            log.error("[MockWeatherMCP] Error", ex);
            String errJson = "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}";
            broadcastSse("message", errJson);
            return ResponseEntity.ok(errJson);
        }
    }

    private ResponseEntity<?> handleSse(HttpServletResponse resp) {
        resp.setContentType("text/event-stream");
        resp.setCharacterEncoding("UTF-8");
        try {
            PrintWriter w = resp.getWriter();
            w.write("event: endpoint\ndata: /mcp\n\n");
            w.flush();
            sseClients.add(w);
        } catch (IOException e) {
            log.error("[MockWeatherMCP] SSE write error", e);
        }
        return null;
    }

    private String handleJsonRpc(String method, JsonNode id, JsonNode request) throws Exception {
        if ("notifications/initialized".equals(method)) {
            return null;
        }
        if ("initialize".equals(method)) {
            return successJson(id, buildInitializeResult());
        }
        if ("tools/list".equals(method)) {
            return successJson(id, buildToolsListResult());
        }
        if ("tools/call".equals(method)) {
            JsonNode params = request.path("params");
            String toolName = params.path("name").asText("");
            JsonNode args = params.path("arguments");
            return successJson(id, buildToolCallResult(toolName, args));
        }
        if ("ping".equals(method)) {
            return successJson(id, mapper.createObjectNode());
        }
        return errorJson(id, -32601, "Unknown method: " + method);
    }

    private ObjectNode buildInitializeResult() {
        ObjectNode r = mapper.createObjectNode();
        r.put("protocolVersion", "2025-03-26");
        r.putObject("capabilities").putObject("tools");
        ObjectNode info = r.putObject("serverInfo");
        info.put("name", "Mock天气服务");
        info.put("version", "1.0.0");
        return r;
    }

    private ObjectNode buildToolsListResult() {
        ObjectNode r = mapper.createObjectNode();
        ArrayNode tools = r.putArray("tools");

        ObjectNode t1 = tools.addObject();
        t1.put("name", "get_weather");
        t1.put("description", "查询指定城市当前天气，返回温度、天气状况、湿度、风力和空气质量");
        ObjectNode s1 = t1.putObject("inputSchema");
        s1.put("type", "object");
        ObjectNode p1 = s1.putObject("properties");
        p1.putObject("city").put("type", "string").put("description", "城市名称，如：北京、上海、广州");
        s1.putArray("required").add("city");

        ObjectNode t2 = tools.addObject();
        t2.put("name", "get_weather_forecast");
        t2.put("description", "查询指定城市未来三天天气预报，返回每日最高/最低温度和天气状况");
        ObjectNode s2 = t2.putObject("inputSchema");
        s2.put("type", "object");
        ObjectNode p2 = s2.putObject("properties");
        p2.putObject("city").put("type", "string").put("description", "城市名称，如：北京、上海、广州");
        s2.putArray("required").add("city");

        return r;
    }

    private ObjectNode buildToolCallResult(String toolName, JsonNode args) {
        String text = switch (toolName) {
            case "get_weather" -> handleGetWeather(args);
            case "get_weather_forecast" -> handleGetForecast(args);
            default -> null;
        };
        if (text == null) {
            return null;
        }

        log.info("[MockWeatherMCP] tool={}, result={}", toolName,
                text.substring(0, Math.min(60, text.length())));

        ObjectNode r = mapper.createObjectNode();
        ArrayNode content = r.putArray("content");
        ObjectNode tc = content.addObject();
        tc.put("type", "text");
        tc.put("text", text);
        return r;
    }

    private String handleGetWeather(JsonNode args) {
        String city = normalizeCity(args.path("city").asText(""));
        if (city.isEmpty()) {
            return "请提供城市名称，例如：北京、上海、广州";
        }
        Map<String, String> w = WEATHER.get(city);
        if (w == null) {
            return "暂不支持城市「" + city + "」的天气查询。支持的城市："
                    + String.join("、", WEATHER.keySet());
        }
        return String.format(
                "%s 当前天气：\n- 温度：%s°C\n- 天气：%s\n- 湿度：%s%%\n- 风力：%s\n- 空气质量：%s",
                city, w.get("temp"), w.get("condition"),
                w.get("humidity"), w.get("wind"), w.get("aqi"));
    }

    private String handleGetForecast(JsonNode args) {
        String city = normalizeCity(args.path("city").asText(""));
        if (city.isEmpty()) {
            return "请提供城市名称，例如：北京、上海、广州";
        }
        List<Map<String, String>> days = FORECAST.get(city);
        if (days == null) {
            return "暂不支持城市「" + city + "」的天气预报。支持的城市："
                    + String.join("、", FORECAST.keySet());
        }
        StringBuilder sb = new StringBuilder(city).append(" 未来三天预报：\n");
        for (Map<String, String> day : days) {
            sb.append(String.format("- %s：%s，%s~%s°C\n",
                    day.get("date"), day.get("condition"),
                    day.get("low"), day.get("high")));
        }
        return sb.toString().stripTrailing();
    }

    /** 去掉「市」后缀，统一匹配 */
    private String normalizeCity(String city) {
        if (city == null || city.isBlank()) {
            return "";
        }
        city = city.trim();
        if (city.endsWith("市")) {
            return city.substring(0, city.length() - 1);
        }
        return city;
    }

    private void broadcastSse(String event, String data) {
        for (PrintWriter w : sseClients) {
            try {
                w.write("event: " + event + "\ndata: " + data + "\n\n");
                w.flush();
            } catch (Exception e) {
                sseClients.remove(w);
            }
        }
    }

    private String successJson(JsonNode id, ObjectNode result) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("jsonrpc", "2.0");
        root.set("id", id);
        root.set("result", result);
        return mapper.writeValueAsString(root);
    }

    private String errorJson(JsonNode id, int code, String message) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("jsonrpc", "2.0");
        root.set("id", id);
        ObjectNode e = root.putObject("error");
        e.put("code", code);
        e.put("message", message);
        return mapper.writeValueAsString(root);
    }
}
