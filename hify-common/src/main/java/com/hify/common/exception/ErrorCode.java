package com.hify.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    // 通用 1000-1999
    SUCCESS(200, "success"),
    INTERNAL_ERROR(1000, "服务器内部错误"),
    PARAM_ERROR(1001, "参数校验失败"),
    NOT_FOUND(1002, "资源不存在"),
    METHOD_NOT_ALLOWED(1003, "不支持的请求方法"),

    // Provider 2000-2999
    PROVIDER_NOT_FOUND(2000, "模型提供商不存在"),
    PROVIDER_CONNECTION_FAILED(2001, "模型提供商连接失败"),
    PROVIDER_RATE_LIMITED(2002, "模型提供商限流"),
    PROVIDER_AUTH_FAILED(2003, "模型提供商认证失败"),
    PROVIDER_TIMEOUT(2004, "模型提供商响应超时"),
    INVALID_PROVIDER_TYPE(2005, "不支持的提供商类型"),

    // Agent 3000-3999
    AGENT_NOT_FOUND(3000, "Agent 不存在"),

    // Chat 4000-4999
    CONVERSATION_NOT_FOUND(4000, "对话不存在"),
    MESSAGE_SEND_FAILED(4001, "消息发送失败"),

    // MCP 5000-5999
    MCP_TOOL_NOT_FOUND(5000, "MCP 工具不存在"),
    MCP_TOOL_CALL_FAILED(5001, "MCP 工具调用失败"),

    // Workflow 6000-6999
    WORKFLOW_NOT_FOUND(6000, "工作流不存在"),
    WORKFLOW_EXECUTION_FAILED(6001, "工作流执行失败"),

    // Knowledge 7000-7999
    KNOWLEDGE_NOT_FOUND(7000, "知识库不存在"),
    KNOWLEDGE_UPLOAD_FAILED(7001, "知识库文档上传失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
