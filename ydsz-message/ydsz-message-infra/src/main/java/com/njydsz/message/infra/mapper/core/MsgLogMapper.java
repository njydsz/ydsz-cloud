package com.njydsz.message.infra.mapper.core;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import com.njydsz.message.domain.entity.core.MsgLog;

/**
 * 消息发送日志 Mapper
 *
 * <p>对应数据表 <code>ydsz_msg_log</code>。
 * <p>每条消息的发送记录（消息 ID、接收人、渠道、模板、状态、回执状态、重试次数、错误信息），是消息中心的核心事实表。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_msg_id — 消息 ID 唯一索引（雪花算法字符串）</li>
 *   <li>idx_user_status — 用户+状态过滤索引（待办列表）</li>
 *   <li>idx_send_at — 发送时间排序索引（按时间范围查询）</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.message.domain.entity.core.MsgLog 消息日志实体
 * @see com.njydsz.message.server.service.MsgLogService 消息日志 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface MsgLogMapper extends BaseMapper<MsgLog> {
}
