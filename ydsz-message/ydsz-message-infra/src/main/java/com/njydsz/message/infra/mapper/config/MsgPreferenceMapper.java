package com.njydsz.message.infra.mapper.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.message.domain.entity.MsgPreference;

/**
 * 用户消息偏好 Mapper
 *
 * <p>对应数据表 <code>ydsz_msg_preference</code>。
 *
 * <p>用户偏好决定消息的渠道（站内/邮件/短信/IM）、时段（勿扰时段）、免打扰类型等，是消息中心的核心个性化数据。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_user_id — 用户唯一索引（一个用户一份偏好）
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.message.domain.entity.config.MsgPreference 偏好实体
 * @see com.njydsz.message.server.service.MsgPreferenceService 偏好 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface MsgPreferenceMapper extends BaseMapper<MsgPreference> {}
