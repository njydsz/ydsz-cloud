package com.njydsz.message.infra.mapper.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.message.infra.entity.MsgTrace;

/**
 * 消息轨迹 Mapper
 *
 * <p>对应数据表 <code>ydsz_msg_trace</code>。
 *
 * <p>轨迹按时间线记录消息的关键事件（创建/调度/发送/送达/已读/点击/失败/重试），用于消息全链路追踪与排查。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>idx_msg_id — 消息维度查询索引（按时间排序）
 *   <li>idx_trace_at — 轨迹时间排序索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.message.domain.entity.config.MsgTrace 轨迹实体
 * @see com.njydsz.message.server.service.MsgTraceService 轨迹 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface MsgTraceMapper extends BaseMapper<MsgTrace> {}
