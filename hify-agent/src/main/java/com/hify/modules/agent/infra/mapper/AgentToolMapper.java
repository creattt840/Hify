package com.hify.modules.agent.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.agent.infra.entity.AgentToolPo;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentToolMapper extends BaseMapper<AgentToolPo> {

    /** 物理删除已软删除的记录，防止 UNIQUE 索引冲突 */
    @Delete("DELETE FROM t_agent_tool WHERE agent_id = #{agentId} AND deleted = 1")
    int physicalDeleteSoftDeleted(Long agentId);
}
