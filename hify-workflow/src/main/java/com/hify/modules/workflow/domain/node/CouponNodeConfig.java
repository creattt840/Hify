package com.hify.modules.workflow.domain.node;

import com.fasterxml.jackson.annotation.JsonTypeName;

import java.math.BigDecimal;

/**
 * COUPON 节点配置 —— 发放优惠券。
 */
@JsonTypeName("COUPON")
public record CouponNodeConfig(
    String couponType,
    BigDecimal amount
) implements NodeConfig {
}
