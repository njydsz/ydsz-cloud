package com.njydsz.message.server.service.batch;

import com.njydsz.message.domain.dto.BatchProgressDTO;
import com.njydsz.message.domain.dto.BatchSendRequestDTO;
import com.njydsz.message.domain.vo.MsgBatchVO;

/**
 * 消息批次 Service
 *
 * <p>管理异步批量发送的批次生命周期：批次创建、进度跟踪、后台异步执行。 适用于"一次业务操作要发送数百/数千条消息"的场景(如全员公告),通过异步化 避免同步阻塞,前端可轮询 {@link
 * #getProgress} 获取处理进度。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>创建批次</b>：{@link #submitBatch} — 立即返回批次 ID,后台异步处理
 *   <li><b>进度查询</b>：{@link #getProgress} — 返回 total/success/failed/pending 计数
 *   <li><b>异步执行</b>：{@link #executeBatch} — 由后台调度器/线程池调用
 * </ul>
 *
 * <p><b>批次状态：</b>{@code PENDING → PROCESSING → COMPLETED / FAILED}。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.domain.vo.MsgBatchVO 批次VO
 * @see AggregateService 聚合批次服务(按 group+receiver 聚合多条消息为单条摘要)
 */
public interface BatchService {

  /**
   * 创建批次并异步发送（异步模式立即返回，后台处理）。
   *
   * @param dto 批量发送请求
   * @return 批次实体（含 batchId 与初始状态）
   */
  MsgBatchVO submitBatch(BatchSendRequestDTO dto);

  /**
   * 查询批次进度。
   *
   * @param batchId 批次 ID
   * @return 进度 VO
   */
  BatchProgressDTO getProgress(String batchId);

  /**
   * 异步执行批次发送（后台线程调用）。
   *
   * @param batchId 批次 ID
   */
  void executeBatch(String batchId);
}
