package com.njydsz.message.domain.dto.config;

import com.njydsz.common.safe.annotation.Xss;

import com.njydsz.common.domain.query.PageQuery;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 退订记录分页查询 DTO（P1-5）。
 *
 * <p>用于管理后台分页查看已退订用户列表，支持按用户 / 主题 / 通道过滤，
 * 仅返回 {@code status=UNSUBSCRIBED} 的记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UnsubscribeQueryDTO extends PageQuery {

    /** 用户 ID（精确匹配） */
    @Xss
    private String userId;

    /** 主题编码（精确匹配） */
    @Xss
    private String topicCode;

    /** 通道（精确匹配） */
    @Xss
    private String channel;

    /** 租户 ID（精确匹配） */
    @Xss
    private String tenantId;
}
