package com.hify.modules.provider.api;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProviderDetailResponse extends ProviderResponse {

    private List<ModelConfigResponse> modelConfigs;
    private ProviderHealthResponse health;
}
