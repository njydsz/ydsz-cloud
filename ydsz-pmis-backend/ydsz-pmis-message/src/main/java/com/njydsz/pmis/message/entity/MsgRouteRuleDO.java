package com.njydsz.pmis.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 消息路由规则表: 按 biz_type/channel/条件表达式路由到目标通道,支持降级
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_msg_route_rule")
public class MsgRouteRuleDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 规则编码(租户内唯一) */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 业务类型 */
    private String bizType;

    /** 通道 */
    private String channel;

    /** 优先级(数值越小越优先) */
    private Integer priority;

    /** 路由条件(SpEL 表达式) */
    private String conditionExpr;

    /** 命中后目标通道 */
    private String targetChannel;

    /** 目标通道发送失败时降级通道 */
    private String fallbackChannel;

    /** P1-8: 多级降级链(逗号分隔通道列表,如 "SMS,EMAIL,INAPP"),按顺序逐个尝试,优先于 fallbackChannel */
    private String fallbackChain;

    /** 状态: ENABLED 启用 / DISABLED 禁用 */
    private String status;

    /** 描述说明 */
    private String description;

    /** 排序序号 */
    private Integer sortOrder;

    /** 租户 ID(单租户部署默认 1) */
    private String tenantId;
}
