package com.hify.modules.workflow.domain.node;

import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("START")
public record StartNodeConfig() implements NodeConfig {
}
