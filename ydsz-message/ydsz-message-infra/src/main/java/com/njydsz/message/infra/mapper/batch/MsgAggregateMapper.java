package com.njydsz.message.infra.mapper.batch;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import com.njydsz.message.domain.entity.batch.MsgAggregate;

/**
 * 聚合批次 Mapper
 *
 * <p>对应数据表 <code>ydsz_msg_aggregate</code>。
 * <p>聚合批次是把同一业务事件的多条消息合并为 1 条聚合消息发送（避免对用户的骚扰），按业务键聚合。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_agg_key — 聚合键唯一索引</li>
 *   <li>idx_agg_at — 聚合时间排序索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.message.domain.entity.batch.MsgAggregate 聚合批次实体
 * @see com.njydsz.message.server.service.MsgAggregateService 聚合批次 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface MsgAggregateMapper extends BaseMapper<MsgAggregate> {
}
