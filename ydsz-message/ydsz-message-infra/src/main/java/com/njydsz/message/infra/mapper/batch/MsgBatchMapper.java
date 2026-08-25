package com.njydsz.message.infra.mapper.batch;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.message.infra.entity.MsgBatch;

/**
 * 消息批次 Mapper
 *
 * <p>对应数据表 <code>ydsz_msg_batch</code>。
 *
 * <p>批次是消息发送的最小调度单位，1 个批次可能包含若干通知（{@code ydsz_msg_notification}），由调度器统一推送。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_batch_no — 批次号唯一索引
 *   <li>idx_status — 批次状态过滤索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.domain.entity.batch.MsgBatch 批次实体
 * @see com.njydsz.message.server.service.MsgBatchService 批次 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface MsgBatchMapper extends BaseMapper<MsgBatch> {}
