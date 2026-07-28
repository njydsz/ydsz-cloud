package com.njydsz.message.infra.mapper.config;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.message.domain.entity.config.MsgFeedback;

/**
 * 消息用户反馈 Mapper
 *
 * <p>对应数据表 <code>ydsz_msg_feedback</code>。
 * <p>用户反馈用于消息质量优化（取消订阅、调整推送频率、识别骚扰内容），同时作为渠道质量评分输入。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>idx_user_id — 用户维度查询索引</li>
 *   <li>idx_msg_id — 消息维度查询索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.message.domain.entity.config.MsgFeedback 反馈实体
 * @see com.njydsz.message.server.service.MsgFeedbackService 反馈 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface MsgFeedbackMapper extends BaseMapper<MsgFeedback> {
}
