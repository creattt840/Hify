package com.hify.modules.mcp.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.mcp.infra.entity.McpToolPo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface McpToolMapper extends BaseMapper<McpToolPo> {

    /** 统计有多少 Agent 绑定了该 Server 的工具 */
    @Select("SELECT COUNT(DISTINCT at.agent_id) FROM t_agent_tool at " +
            "INNER JOIN t_mcp_tool mt ON at.tool_id = mt.id " +
            "WHERE mt.server_id = #{serverId} AND at.deleted = 0 AND mt.deleted = 0")
    int countAgentBindings(Long serverId);
}
