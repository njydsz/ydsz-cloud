package com.njydsz.message.server.service.batch;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.message.domain.entity.batch.MsgAggregate;

/**
 * 消息聚合批次 Service
 *
 * <p>按"聚合组 + 接收人"维度将短时间内的多条消息聚合为单条摘要发送,避免用户被同一主题的 消息轰炸。例如：1 分钟内 10 条"工单创建"通知,合并为"您有 10
 * 个新工单,点击查看"一条摘要。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>追加消息</b>：{@link #appendOrStart} — 消息到达时追加到当前聚合批次;批次不存在则新建
 *   <li><b>刷新</b>：{@link #flushDue} / {@link #flushByGroup} — 定时任务或主动调用触发,生成摘要并发送
 *   <li><b>分页</b>：{@link #page} — 管理后台查询
 * </ul>
 *
 * <p><b>刷新策略：</b>由 {@code ydsz.message.aggregate.flush-interval-seconds} 配置(默认 60s), 定时任务每分钟扫描到期批次。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.domain.entity.batch.MsgAggregate 聚合批次实体
 * @see BatchService 普通批次服务(无聚合)
 */
public interface AggregateService {

  /**
   * 追加消息到聚合批次,不存在则新建批次
   *
   * @param group 聚合组
   * @param receiver 接收人
   * @param channel 通道
   * @param tenantId 租户 ID
   * @return 聚合批次实体
   */
  MsgAggregate appendOrStart(String group, String receiver, String channel, String tenantId);

  /**
   * 刷新到期的聚合批次(发送摘要)
   *
   * @return 已发送批次数
   */
  int flushDue();

  /**
   * 按聚合组 + 接收人刷新批次
   *
   * @param group 聚合组
   * @param receiver 接收人
   * @return 已发送批次数
   */
  int flushByGroup(String group, String receiver);

  /**
   * 分页查询聚合批次
   *
   * @param query 分页参数
   * @return 分页结果
   */
  Page<MsgAggregate> page(PageQuery query);
}
