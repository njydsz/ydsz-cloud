package com.njydsz.message.domain.entity.config;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 消息路由规则表: 按 biz_type/channel/条件表达式路由到目标通道,支持降级
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_msg_route_rule")
public class MsgRouteRule extends MpBaseEntity<String> {

    @Serial
    private static final long serialVersionUID = 1L;

}
