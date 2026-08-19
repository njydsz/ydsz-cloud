package com.njydsz.message.server.service.impl.batch;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.message.domain.dto.batch.BatchProgressVO;
import com.njydsz.message.domain.dto.batch.BatchSendRequestDTO;
import com.njydsz.message.domain.dto.batch.BatchSendResult;
import com.njydsz.message.domain.entity.batch.MsgBatch;
import com.njydsz.message.domain.event.BatchCompletedEvent;
import com.njydsz.message.infra.repository.MsgBatchRepository;
import com.njydsz.message.server.event.DomainEventPublisher;
import com.njydsz.message.server.service.SseEmitterService;
import com.njydsz.message.server.service.batch.BatchService;
import com.njydsz.message.server.service.core.MessageService;

/**
 * 消息批次服务实现。
 *
 * <p>异步批量发送流程：
 *
 * <ol>
 *   <li>{@link #submitBatch} 创建 PENDING 批次记录，返回 batchId
 *   <li>{@link #executeBatch} 异步处理：逐条调用 {@link MessageService#send}， 实时更新 success/failed/skipped 计数
 *   <li>处理完成后更新状态为 COMPLETED / FAILED
 * </ol>
 *
 * <p>支持 receiverList 模式（统一模板+接收人列表展开）和 requests 模式（每条独立请求）。 单批最大 10000 条，超出拒绝。异步处理通过 Spring
 * {@code @Async} 线程池执行。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchServiceImpl implements BatchService {

  /** 单批最大条数 */
  private static final int MAX_BATCH_SIZE = 10000;

  /** 批次记录 Repository */
  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  private final MsgBatchRepository msgBatchRepository;

  /** 消息发送服务（逐条发送） */
  private final MessageService messageService;

  /** P1-E2: SSE 发射器服务（批次进度推送） */
  private final SseEmitterService sseEmitterService;

  /** P2-A4: 领域事件发布器 */
  private final DomainEventPublisher domainEventPublisher;

  @Override
  public MsgBatch submitBatch(BatchSendRequestDTO dto) {
    if (dto == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("批量发送参数不能为空")
          .build();
    }
    // 构建请求列表
    List<MessageRequest> requests = buildRequests(dto);
    if (requests.isEmpty()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("接收人列表为空")
          .build();
    }
    if (requests.size() > MAX_BATCH_SIZE) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("单批最大 " + MAX_BATCH_SIZE + " 条，当前 " + requests.size() + " 条")
          .build();
    }
    // 创建批次记录
    String batchId =
        StringUtils.hasText(dto.getBatchId())
            ? dto.getBatchId()
            : String.valueOf(snowflakeIdGenerator.nextId());
    MsgBatch batch = new MsgBatch();
    batch.setBatchId(batchId);
    batch.setBatchName(dto.getBatchName());
    batch.setChannel(dto.getChannel());
    batch.setTemplateCode(dto.getTemplateCode());
    batch.setBizType(dto.getBizType());
    batch.setTotal(requests.size());
    batch.setSuccess(0);
    batch.setFailed(0);
    batch.setSkipped(0);
    batch.setStatus("PENDING");
    batch.setSenderId(dto.getSenderId());
    batch.setTenantId(TenantContextHolder.getTenantId());
    // P1-A3: 序列化请求列表存入 payload，支持后续断点续传
    batch.setPayload(YdszJson.toJson(requests));
    msgBatchRepository.insert(batch);
    log.info(
        "[Batch] 批次已创建: batchId={} total={} channel={}",
        batchId,
        requests.size(),
        dto.getChannel());

    // 异步执行
    boolean async = dto.getAsync() == null || dto.getAsync();
    if (async) {
      executeBatchAsync(batchId, requests);
    } else {
      executeBatchSync(batchId, requests);
    }
    return batch;
  }

  @Override
  public BatchProgressVO getProgress(String batchId) {
    if (!StringUtils.hasText(batchId)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("批次 ID 不能为空")
          .build();
    }
    MsgBatch batch =
        msgBatchRepository.selectOne(
            new LambdaQueryWrapper<MsgBatch>().eq(MsgBatch::getBatchId, batchId).last("LIMIT 1"));
    if (batch == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .message("批次不存在: " + batchId)
          .build();
    }
    BatchProgressVO vo = new BatchProgressVO();
    vo.setBatchId(batch.getBatchId());
    vo.setBatchName(batch.getBatchName());
    vo.setChannel(batch.getChannel());
    vo.setTemplateCode(batch.getTemplateCode());
    vo.setTotal(batch.getTotal() == null ? 0 : batch.getTotal());
    vo.setSuccess(batch.getSuccess() == null ? 0 : batch.getSuccess());
    vo.setFailed(batch.getFailed() == null ? 0 : batch.getFailed());
    vo.setSkipped(batch.getSkipped() == null ? 0 : batch.getSkipped());
    int processed = vo.getSuccess() + vo.getFailed() + vo.getSkipped();
    vo.setProcessed(processed);
    vo.setProgressPercent(
        vo.getTotal() > 0 ? Math.round(processed * 10000.0 / vo.getTotal()) / 100.0 : 0.0);
    vo.setStatus(batch.getStatus());
    vo.setErrorMessage(batch.getErrorMessage());
    vo.setStartedAt(batch.getStartedAt());
    vo.setCompletedAt(batch.getCompletedAt());
    vo.setCreatedAt(batch.getCreatedAt());
    return vo;
  }

  /**
   * 异步执行批次发送（在 {@code messageBatchExecutor} 线程池执行）。
   *
   * <p>由 {@link #submitBatch} 在 {@code async=true}（默认）时调用；内部委托 {@link #doExecuteBatch} 完成状态推进与发送。
   * 注意：本方法通过 Spring {@code @Async} 代理生效，<strong>务必经注入的 Bean
   * 调用</strong>，同类内直接调用（self-invocation）不会触发异步。
   *
   * @param batchId 批次 ID
   * @param requests 待发送消息请求列表（非空，已在 {@link #submitBatch} 校验上限）
   */
  @Async("messageBatchExecutor")
  public void executeBatchAsync(String batchId, List<MessageRequest> requests) {
    // 首次执行：batch 计数为 0，使用增量累加模式（效果等同全量覆盖）
    doExecuteBatch(batchId, requests, true);
  }

  /** 同步执行批次发送（async=false 时使用）。 */
  private void executeBatchSync(String batchId, List<MessageRequest> requests) {
    doExecuteBatch(batchId, requests, true);
  }

  /** P1-A3: 反序列化 payload 为请求列表，异常时返回空列表。 */
  private List<MessageRequest> parsePayload(String payload) {
    if (!StringUtils.hasText(payload)) {
      return new ArrayList<>();
    }
    try {
      List<MessageRequest> requests = YdszJson.parseArray(payload, MessageRequest.class);
      return requests != null ? requests : new ArrayList<>();
    } catch (Exception e) {
      log.warn("[Batch] payload 反序列化失败: {}", e.getMessage(), e);
      return new ArrayList<>();
    }
  }

  /**
   * P1-A3: 断点续传执行。
   *
   * <p>从 DB 读取批次 payload 恢复请求列表，仅处理尚未成功的部分（基于 success+failed+skipped 计数的偏移）； 若批次已完成或为终态则跳过。
   *
   * @param batchId 批次 ID
   */
  @Override
  public void executeBatch(String batchId) {
    MsgBatch batch =
        msgBatchRepository.selectOne(
            new LambdaQueryWrapper<MsgBatch>().eq(MsgBatch::getBatchId, batchId).last("LIMIT 1"));
    if (batch == null) {
      log.warn("[Batch] 批次不存在: {}", batchId);
      return;
    }
    List<MessageRequest> allRequests = parsePayload(batch.getPayload());
    if (allRequests.isEmpty()) {
      log.warn("[Batch] 批次 payload 为空，无法恢复: {}", batchId);
      return;
    }
    // 计算已处理偏移（断点续传起点）
    int processed =
        (batch.getSuccess() != null ? batch.getSuccess() : 0)
            + (batch.getFailed() != null ? batch.getFailed() : 0)
            + (batch.getSkipped() != null ? batch.getSkipped() : 0);
    if (processed >= allRequests.size()) {
      log.info(
          "[Batch] 批次已全部处理完成，跳过: batchId={} processed={} total={}",
          batchId,
          processed,
          allRequests.size());
      return;
    }
    List<MessageRequest> remaining = allRequests.subList(processed, allRequests.size());
    log.info(
        "[Batch] 断点续传: batchId={} processed={} remaining={}", batchId, processed, remaining.size());
    // 重置为 PROCESSING 并异步执行剩余请求
    batch.setStatus("PROCESSING");
    msgBatchRepository.updateById(batch);
    executeBatchAsync(batchId, remaining);
  }

  /**
   * 执行批次发送核心逻辑。
   *
   * <p>逐条串行发送，避免并行发送的线程切换开销和复杂错误处理。 P1-A3: 支持断点续传（incremental=true）时增量累加计数。
   *
   * @param batchId 批次 ID
   * @param requests 消息请求列表
   * @param incremental true 表示增量累加（断点续传），false 表示全量覆盖（首次执行）
   */
  private void doExecuteBatch(String batchId, List<MessageRequest> requests, boolean incremental) {
    MsgBatch batch =
        msgBatchRepository.selectOne(
            new LambdaQueryWrapper<MsgBatch>().eq(MsgBatch::getBatchId, batchId).last("LIMIT 1"));
    if (batch == null) {
      log.warn("[Batch] 批次不存在: {}", batchId);
      return;
    }
    batch.setStatus("PROCESSING");
    if (batch.getStartedAt() == null) {
      batch.setStartedAt(LocalDateTime.now());
    }
    msgBatchRepository.updateById(batch);

    // 逐条串行发送
    int success = 0;
    int failure = 0;
    int skipped = 0;
    for (MessageRequest request : requests) {
      try {
        MessageResult result = messageService.send(request);
        if (result != null && result.isSuccess()) {
          success++;
        } else {
          failure++;
        }
      } catch (Exception e) {
        failure++;
        log.warn("[Batch] 单条发送失败: msgId={} err={}", request.getMessageId(), e.getMessage());
      }
    }

    BatchSendResult batchResult = new BatchSendResult(batchId, requests.size(), success, failure, skipped);

    // P1-A3: 断点续传时增量累加计数，首次执行时直接覆盖
    if (incremental) {
      batch.setSuccess(
          (batch.getSuccess() != null ? batch.getSuccess() : 0) + batchResult.getSuccess());
      batch.setFailed(
          (batch.getFailed() != null ? batch.getFailed() : 0) + batchResult.getFailed());
      batch.setSkipped(
          (batch.getSkipped() != null ? batch.getSkipped() : 0) + batchResult.getSkipped());
    } else {
      batch.setSuccess(batchResult.getSuccess());
      batch.setFailed(batchResult.getFailed());
      batch.setSkipped(batchResult.getSkipped());
    }
    batch.setStatus("COMPLETED");
    batch.setCompletedAt(LocalDateTime.now());
    msgBatchRepository.updateById(batch);
    int totalProcessed =
        (batch.getSuccess() != null ? batch.getSuccess() : 0)
            + (batch.getFailed() != null ? batch.getFailed() : 0)
            + (batch.getSkipped() != null ? batch.getSkipped() : 0);
    log.info(
        "[Batch] 批次完成: batchId={} total={} success={} failed={} skipped={} mode={}",
        batchId,
        totalProcessed,
        batch.getSuccess(),
        batch.getFailed(),
        batch.getSkipped(),
        incremental ? "RESUME" : "FULL");

    // P1-E2: 向 SSE 订阅者广播完成事件
    sseEmitterService.broadcastComplete(batchId, buildProgressSnapshot(batch));

    // P2-A4: 发布批次完成领域事件
    domainEventPublisher.publish(
        new BatchCompletedEvent(
            batch.getTenantId(),
            batchId,
            totalProcessed,
            batch.getSuccess(),
            batch.getFailed(),
            batch.getSkipped(),
            incremental ? "RESUME" : "FULL"));
  }

  /**
   * P1-E2: 构建批次进度快照（用于 SSE 推送）。
   *
   * @param batch 批次实体
   * @return 进度快照
   */
  private Map<String, Object> buildProgressSnapshot(MsgBatch batch) {
    int success = batch.getSuccess() != null ? batch.getSuccess() : 0;
    int failed = batch.getFailed() != null ? batch.getFailed() : 0;
    int skipped = batch.getSkipped() != null ? batch.getSkipped() : 0;
    int total = batch.getTotal() != null ? batch.getTotal() : 0;
    int processed = success + failed + skipped;
    double progressPercent = total > 0 ? Math.round(processed * 10000.0 / total) / 100.0 : 0.0;
    return Map.of(
        "batchId", batch.getBatchId(),
        "status", batch.getStatus(),
        "total", total,
        "success", success,
        "failed", failed,
        "skipped", skipped,
        "processed", processed,
        "progressPercent", progressPercent);
  }

  /**
   * 从 DTO 构建消息请求列表。
   *
   * <p>优先使用直接传入的 requests 列表，否则使用 receiverList 模式（统一模板展开）。
   *
   * @param dto 批量发送请求
   * @return 消息请求列表
   */
  private List<MessageRequest> buildRequests(BatchSendRequestDTO dto) {
    List<MessageRequest> requests = new ArrayList<>();
    // 优先使用直接传入的 requests 列表
    if (!CollectionUtils.isEmpty(dto.getRequests())) {
      for (MessageRequest req : dto.getRequests()) {
        if (req != null) {
          if (!StringUtils.hasText(req.getMessageId())) {
            req.setMessageId(String.valueOf(snowflakeIdGenerator.nextId()));
          }
          requests.add(req);
        }
      }
      return requests;
    }
    // receiverList 模式（统一模板展开）
    if (!CollectionUtils.isEmpty(dto.getReceiverList())) {
      for (String receiver : dto.getReceiverList()) {
        if (!StringUtils.hasText(receiver)) {
          continue;
        }
        MessageRequest req = new MessageRequest();
        req.setChannel(dto.getChannel());
        req.setTemplateCode(dto.getTemplateCode());
        req.setReceiver(receiver.trim());
        req.setParams(dto.getParams());
        req.setBizType(dto.getBizType());
        req.setMessageId(String.valueOf(snowflakeIdGenerator.nextId()));
        requests.add(req);
      }
    }
    return requests;
  }
}
