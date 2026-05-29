package com.hify.modules.knowledge.api;

import lombok.Data;

@Data
public class ChunkDTO {

    private int chunkIndex;
    private String content;
    private int tokenCount;
    private float[] embedding;
}
