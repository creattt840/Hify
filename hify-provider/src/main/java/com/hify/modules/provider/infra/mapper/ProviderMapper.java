package com.hify.modules.provider.infra.mapper;

import com.hify.common.infra.BaseMapper;
import com.hify.modules.provider.infra.entity.ProviderPo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProviderMapper extends BaseMapper<ProviderPo> {
}
