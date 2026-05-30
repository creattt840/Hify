package com.hify.modules.workflow.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.workflow.infra.entity.WorkflowRunPo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowRunMapper extends BaseMapper<WorkflowRunPo> {
}
