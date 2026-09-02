package com.njydsz.message.infra.mapper.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.message.infra.entity.MsgRouteRule;

/**
 * 消息路由规则 Mapper
 *
 * <p>对应数据表 <code>ydsz_msg_route_rule</code>。
 *
 * <p>路由规则按 (业务类型, 优先级, 渠道) 决定消息的发送渠道、模板、降级策略、限流配置。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_biz_channel — (业务类型+渠道) 唯一索引
 *   <li>idx_priority — 优先级排序索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.message.domain.entity.config.MsgRouteRule 路由规则实体
 * @see com.njydsz.message.server.service.MsgRouteRuleService 路由规则 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface MsgRouteRuleMapper extends BaseMapper<MsgRouteRule> {}
