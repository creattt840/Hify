package com.hify.modules.mcp.api;

import lombok.Data;

@Data
public class ConnectionTestResult {

    private boolean success;
    private String message;
    private Integer toolCount;
}
