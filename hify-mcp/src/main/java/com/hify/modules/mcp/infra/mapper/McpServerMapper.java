package com.hify.modules.mcp.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.mcp.infra.entity.McpServerPo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface McpServerMapper extends BaseMapper<McpServerPo> {
}
