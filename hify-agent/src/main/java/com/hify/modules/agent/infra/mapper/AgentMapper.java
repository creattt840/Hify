package com.hify.modules.agent.infra.mapper;

import com.hify.common.infra.BaseMapper;
import com.hify.modules.agent.infra.entity.AgentPo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentMapper extends BaseMapper<AgentPo> {
}
