package com.remisoft.message.infra.mapper.config;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.remisoft.message.domain.entity.config.MsgSubscription;

/**
 * 消息订阅关系 Mapper
 *
 * <p>对应数据表 <code>remi_msg_subscription</code>。
 * <p>订阅关系决定用户是否接收某类消息（OA 通知/系统公告/项目动态等），可由用户主动订阅或业务自动订阅。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_user_event — (用户+事件类型) 唯一索引</li>
 *   <li>idx_biz_type — 业务类型过滤索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see com.remisoft.message.domain.entity.config.MsgSubscription 订阅实体
 * @see com.remisoft.message.server.service.MsgSubscriptionService 订阅 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface MsgSubscriptionMapper extends BaseMapper<MsgSubscription> {
}
