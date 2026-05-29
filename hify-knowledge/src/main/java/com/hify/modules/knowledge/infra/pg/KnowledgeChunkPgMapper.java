package com.hify.modules.knowledge.infra.pg;

import org.apache.ibatis.annotations.Update;

/**
 * PostgreSQL pgvector 分块表 Mapper，由 PgVectorConfig 的 pgSqlSessionFactory 管理。
 * 不标注 @Mapper 避免被 HifyApplication 的 MySQL SqlSessionFactory 扫描。
 */
public interface KnowledgeChunkPgMapper {

    @Update("UPDATE t_knowledge_chunk SET deleted = 1 WHERE kb_id = #{kbId}")
    int deleteByKbId(Long kbId);

    @Update("UPDATE t_knowledge_chunk SET deleted = 1 WHERE document_id = #{documentId}")
    int deleteByDocumentId(Long documentId);
}
