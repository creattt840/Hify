package com.hify.modules.workflow.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.workflow.infra.entity.WorkflowNodeRunPo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowNodeRunMapper extends BaseMapper<WorkflowNodeRunPo> {
}
