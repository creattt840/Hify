package com.hify.modules.knowledge.infra.mapper;

import com.hify.common.infra.BaseMapper;
import com.hify.modules.knowledge.infra.entity.KnowledgeBasePo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBasePo> {
}
