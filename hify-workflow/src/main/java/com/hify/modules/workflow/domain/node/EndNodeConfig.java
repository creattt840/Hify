package com.hify.modules.workflow.domain.node;

import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("END")
public record EndNodeConfig() implements NodeConfig {
}
