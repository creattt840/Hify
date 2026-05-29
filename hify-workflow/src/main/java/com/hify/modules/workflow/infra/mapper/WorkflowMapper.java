package com.hify.modules.workflow.infra.mapper;

import com.hify.common.infra.BaseMapper;
import com.hify.modules.workflow.infra.entity.WorkflowPo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowMapper extends BaseMapper<WorkflowPo> {
}
