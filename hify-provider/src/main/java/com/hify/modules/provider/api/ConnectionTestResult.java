package com.hify.modules.provider.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionTestResult {

    private boolean success;
    private long latencyMs;
    private int modelCount;
    private String errorMessage;

    public static ConnectionTestResult ok(long latencyMs, int modelCount) {
        return new ConnectionTestResult(true, latencyMs, modelCount, null);
    }

    public static ConnectionTestResult fail(String errorMessage) {
        return new ConnectionTestResult(false, 0, 0, errorMessage);
    }
}
