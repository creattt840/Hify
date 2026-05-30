package com.hify.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 模拟 MCP Server —— 实现 MCP Streamable HTTP 协议（JSON-RPC + SSE）。
 * <p>
 * MCP Streamable HTTP 协议：POST 发送 JSON-RPC 请求，GET 建立 SSE 流接收响应。
 * 由于 McpSyncClient 内部也通过 SSE 接收响应，GET 端点必须返回 SSE 流。
 */
@RestController
public class MockMcpController {

    private static final Logger log = LoggerFactory.getLogger(MockMcpController.class);

    private final ObjectMapper mapper = new ObjectMapper();

    /** 所有活跃的 SSE 连接，用于推送响应 */
    private static final CopyOnWriteArrayList<PrintWriter> sseClients = new CopyOnWriteArrayList<>();

    private static final Map<String, Map<String, String>> ORDERS = Map.of(
            "12345", Map.of("status", "运输中", "carrier", "顺丰快递", "tracking", "SF1234567890", "eta", "预计明天送达"),
            "67890", Map.of("status", "已签收", "carrier", "中通快递", "tracking", "ZT9876543210", "eta", "5月28日已签收"),
            "11111", Map.of("status", "待发货", "carrier", "", "tracking", "", "eta", "预计3天内发货")
    );

    private static final Map<String, Map<String, String>> REFUNDS = Map.of(
            "RF001", Map.of("orderId", "12345", "status", "审核中", "amount", "299.00", "reason", "不想要了", "eta", "预计1-3个工作日审核"),
            "RF002", Map.of("orderId", "67890", "status", "已退款", "amount", "158.50", "reason", "质量问题", "eta", "5月28日已原路退回"),
            "RF003", Map.of("orderId", "11111", "status", "待退货", "amount", "89.00", "reason", "尺码不合适", "eta", "请7天内寄回商品")
    );

    // ── 主入口 + 通配兜底 ──

    @RequestMapping({"/mock-mcp", "/mcp"})
    public ResponseEntity<?> handle(HttpServletRequest req, HttpServletResponse resp) {
        String method = req.getMethod();
        log.info("[MockMCP] {} {}", method, req.getRequestURI());

        if ("GET".equalsIgnoreCase(method)) {
            return handleSse(req, resp);
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            return ResponseEntity.ok("{}");
        }

        // POST 请求：JSON-RPC → 处理后将响应通过 SSE 推送给客户端
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = req.getReader()) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            JsonNode request = mapper.readTree(sb.toString());
            String rpcMethod = request.path("method").asText("");
            JsonNode id = request.get("id");

            log.info("[MockMCP] RPC method={}", rpcMethod);

            String response = handleJsonRpc(rpcMethod, id, request);
            if (response != null) {
                // 同时通过 HTTP body 和 SSE 返回（SDK 从前者读，SSE 用于服务器推送）
                broadcastSse("message", response);
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.ok("");
        } catch (Exception ex) {
            log.error("[MockMCP] Error", ex);
            String errJson = "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}";
            broadcastSse("message", errJson);
            return ResponseEntity.ok(errJson);
        }
    }

    // ── GET: SSE 流 ──

    /**
     * MCP Streamable HTTP: GET 建立 SSE 长连接，用于接收服务端推送的 JSON-RPC 响应。
     * McpSyncClient 通过 POST 发送请求，通过此 SSE 连接接收响应。
     * 我们在这个 mock 中直接返回一个初始事件，触发 sync client 完成初始化。
     */
    private ResponseEntity<?> handleSse(HttpServletRequest req, HttpServletResponse resp) {
        // 对于 McpSyncClient，SSE 连接在 start() 时建立，然后 send() 发送 POST。
        // SDK 的 start() 会等待 SSE 连接就绪（收到第一个事件后返回）。
        // 我们直接写入 SSE 格式的响应。
        resp.setContentType("text/event-stream");
        resp.setCharacterEncoding("UTF-8");
        try {
            PrintWriter w = resp.getWriter();
            // 发送 endpoint 事件（MCP Streamable HTTP 协议要求的初始事件）
            // 包含消息端点路径，告诉客户端后续 POST 的目标
            w.write("event: endpoint\ndata: /mock-mcp\n\n");
            w.flush();
            // 保持连接打开（模拟 SSE 长连接）
            sseClients.add(w);
        } catch (IOException e) {
            log.error("[MockMCP] SSE write error", e);
        }
        return null; // 返回 null 表示已手动处理响应
    }

    // ── JSON-RPC 方法分发 ──

    private String handleJsonRpc(String method, JsonNode id, JsonNode request) throws Exception {
        // --- notifications/initialized ---
        if ("notifications/initialized".equals(method)) {
            return null; // no response for notifications
        }

        // --- initialize ---
        if ("initialize".equals(method)) {
            return successJson(id, buildInitializeResult());
        }

        // --- tools/list ---
        if ("tools/list".equals(method)) {
            return successJson(id, buildToolsListResult());
        }

        // --- tools/call ---
        if ("tools/call".equals(method)) {
            JsonNode params = request.path("params");
            String toolName = params.path("name").asText("");
            JsonNode args = params.path("arguments");
            return successJson(id, buildToolCallResult(toolName, args));
        }

        // --- ping ---
        if ("ping".equals(method)) {
            return successJsonRaw(id, mapper.createObjectNode());
        }

        // unknown
        return errorJson(id, -32601, "Unknown method: " + method);
    }

    // ── JSON 构建 ──

    private ObjectNode buildInitializeResult() {
        ObjectNode r = mapper.createObjectNode();
        r.put("protocolVersion", "2025-03-26");
        r.putObject("capabilities").putObject("tools");
        ObjectNode info = r.putObject("serverInfo");
        info.put("name", "Mock退款服务");
        info.put("version", "1.0.0");
        return r;
    }

    private ObjectNode buildToolsListResult() {
        ObjectNode r = mapper.createObjectNode();
        ArrayNode tools = r.putArray("tools");

        ObjectNode t1 = tools.addObject();
        t1.put("name", "query_order");
        t1.put("description", "根据用户ID和订单号查询订单状态，返回订单当前状态、快递公司和预计送达时间");
        ObjectNode s1 = t1.putObject("inputSchema");
        s1.put("type", "object");
        ObjectNode p1 = s1.putObject("properties");
        p1.putObject("userId").put("type", "string").put("description", "用户ID");
        p1.putObject("orderId").put("type", "string").put("description", "订单号");
        ArrayNode req1 = s1.putArray("required");
        req1.add("orderId");

        ObjectNode t2 = tools.addObject();
        t2.put("name", "get_return_policy");
        t2.put("description", "查询退换货政策，返回退换货条件、流程和时限");
        t2.putObject("inputSchema").put("type", "object").putObject("properties");

        ObjectNode t3 = tools.addObject();
        t3.put("name", "query_refund_status");
        t3.put("description", "根据退款单号查询退款进度，返回审核状态、退款金额和预计到账时间");
        ObjectNode s3 = t3.putObject("inputSchema");
        s3.put("type", "object");
        ObjectNode p3 = s3.putObject("properties");
        p3.putObject("refundId").put("type", "string").put("description", "退款单号，如 RF001");
        s3.putArray("required").add("refundId");

        ObjectNode t4 = tools.addObject();
        t4.put("name", "apply_refund");
        t4.put("description", "为指定订单提交退款申请，返回退款单号和受理结果");
        ObjectNode s4 = t4.putObject("inputSchema");
        s4.put("type", "object");
        ObjectNode p4 = s4.putObject("properties");
        p4.putObject("orderId").put("type", "string").put("description", "订单号");
        p4.putObject("reason").put("type", "string").put("description", "退款原因");
        p4.putObject("amount").put("type", "string").put("description", "退款金额");
        s4.putArray("required").add("orderId").add("reason");

        return r;
    }

    private ObjectNode buildToolCallResult(String toolName, JsonNode args) {
        String text;
        if ("query_order".equals(toolName)) {
            String orderId = args.path("orderId").asText("");
            String userId = args.path("userId").asText("");
            Map<String, String> order = ORDERS.get(orderId);
            if (order != null) {
                text = String.format(
                        "订单 %s（用户 %s）：\n- 状态：%s\n- 快递：%s\n- 运单号：%s\n- 预计到达：%s",
                        orderId, userId,
                        order.get("status"), order.get("carrier"),
                        order.get("tracking"), order.get("eta"));
            } else {
                text = "未找到订单 " + orderId + "，请检查订单号是否正确。";
            }
        } else if ("get_return_policy".equals(toolName)) {
            text = "退换货政策：\n1. 7天无理由退货：商品完好，自签收日起7天内可申请\n"
                    + "2. 15天质量问题换货：商品存在质量问题，15天内可申请换货\n"
                    + "3. 退货运费：质量问题由商家承担，非质量问题由买家承担\n"
                    + "4. 退款时效：收到退货后3-5个工作日原路退回";
        } else if ("query_refund_status".equals(toolName)) {
            String refundId = args.path("refundId").asText("");
            Map<String, String> refund = REFUNDS.get(refundId);
            if (refund != null) {
                text = String.format(
                        "退款单 %s（订单 %s）：\n- 状态：%s\n- 金额：¥%s\n- 原因：%s\n- 预计：%s",
                        refundId, refund.get("orderId"),
                        refund.get("status"), refund.get("amount"),
                        refund.get("reason"), refund.get("eta"));
            } else {
                text = "未找到退款单 " + refundId + "，请检查退款单号是否正确。可用测试单号：RF001、RF002、RF003";
            }
        } else if ("apply_refund".equals(toolName)) {
            String orderId = args.path("orderId").asText("");
            String reason = args.path("reason").asText("未填写");
            String amount = args.path("amount").asText("");
            if (!ORDERS.containsKey(orderId)) {
                text = "订单 " + orderId + " 不存在，无法申请退款。测试订单号：12345、67890、11111";
            } else {
                String newRefundId = "RF" + String.format("%03d", 100 + orderId.hashCode() % 900);
                text = String.format(
                        "退款申请已受理：\n- 退款单号：%s\n- 订单号：%s\n- 退款原因：%s\n- 申请金额：%s\n- 状态：审核中\n- 预计1-3个工作日完成审核",
                        newRefundId, orderId, reason,
                        amount.isEmpty() ? "按实付金额" : "¥" + amount);
            }
        } else {
            return null; // will be handled as error
        }

        if (text == null) {
            return null;
        }

        log.info("[MockMCP] tool={}, result={}", toolName,
                text.substring(0, Math.min(60, text.length())));

        ObjectNode r = mapper.createObjectNode();
        ArrayNode content = r.putArray("content");
        ObjectNode tc = content.addObject();
        tc.put("type", "text");
        tc.put("text", text);
        return r;
    }

    // ── SSE 广播 ──

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

    // ── helpers ──

    private String successJson(JsonNode id, ObjectNode result) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("jsonrpc", "2.0");
        root.set("id", id);
        root.set("result", result);
        return mapper.writeValueAsString(root);
    }

    private String successJsonRaw(JsonNode id, ObjectNode result) throws Exception {
        return successJson(id, result);
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
