package com.hify.modules.provider.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    private String model;

    private List<Message> messages;

    private Double temperature;

    private Integer maxTokens;

    /** LLM 工具定义列表（OpenAI function calling 格式） */
    private List<Tool> tools;

    private Map<String, Object> extraParams;

    // ── 消息 ──

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;

        /** role=tool 时的 tool_call_id，对应 assistant 消息中 tool_calls[].id */
        private String toolCallId;

        /** role=assistant 时的 tool_calls 列表 */
        private List<ToolCall> toolCalls;
    }

    // ── 工具定义 ──

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Tool {
        /** 固定 "function" */
        private String type;
        private Function function;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Function {
        private String name;
        private String description;
        /** JSON Schema 参数定义 */
        private Map<String, Object> parameters;
    }

    // ── 工具调用（LLM 返回） ──

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {
        private String id;
        /** 固定 "function" */
        private String type;
        private FunctionCall function;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionCall {
        private String name;
        /** JSON 字符串 */
        private String arguments;
    }
}
