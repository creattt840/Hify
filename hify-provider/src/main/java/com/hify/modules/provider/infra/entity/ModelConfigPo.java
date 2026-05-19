package com.hify.modules.provider.infra.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_model_config", autoResultMap = true)
public class ModelConfigPo extends BaseEntity {

    private Long providerId;

    private String name;

    private String modelId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraParams;
}
