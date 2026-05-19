package com.hify.modules.provider.infra.entity;

public enum ProviderType {

    OPENAI("openai"),
    OPENAI_COMPATIBLE("openai_compatible"),
    ANTHROPIC("anthropic"),
    GEMINI("gemini"),
    OLLAMA("ollama");

    private final String code;

    ProviderType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static ProviderType fromCode(String code) {
        for (ProviderType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown provider type: " + code);
    }
}
