package com.hify.modules.provider.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProviderHealthResponse {

    private Integer failCount;
    private Integer latencyMs;
    private LocalDateTime lastSuccessAt;
}
