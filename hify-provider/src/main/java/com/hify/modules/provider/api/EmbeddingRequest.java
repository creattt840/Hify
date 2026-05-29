package com.hify.modules.provider.api;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class EmbeddingRequest {

    private String model;

    private List<String> input;
}
