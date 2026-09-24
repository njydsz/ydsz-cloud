package com.njydsz.message.server.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.core.context.TenantContextHolder;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.message.domain.dto.BatchProgressDTO;
import com.njydsz.message.domain.dto.BatchSendRequestDTO;
import com.njydsz.message.domain.enums.MessageExceptionCode;
import com.njydsz.message.domain.event.BatchCompletedEvent;
import com.njydsz.message.domain.query.MsgBatchQuery;
import com.njydsz.message.domain.repository.MsgBatchRepository;
import com.njydsz.message.domain.vo.MsgBatchVO;
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
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchServiceImpl implements BatchService {

  /** 单批最大条数 */
  private static final int MAX_BATCH_SIZE = 10000;

  /** 默认批次大小（初始容量） */
  private static final int DEFAULT_BATCH_SIZE = 16;

  /** SSE 进度推送间隔（每处理 N 条触发一次） */
  private static final int SSE_FLUSH_INTERVAL = 10;

  /** SSE 进度推送时间间隔阈值（毫秒） */
  private static final long SSE_FLUSH_TIME_INTERVAL_MS = 2000L;

  /** 批次执行状态：处理中 */
  private static final String STATUS_PROCESSING = "PROCESSING";

  /** 批次执行状态：已完成 */
  private static final String STATUS_COMPLETED = "COMPLETED";

  /** 批次执行状态：部分失败 */
  private static final String STATUS_PARTIAL_FAILED = "PARTIAL_FAILED";

  /** 批次执行状态：全部失败 */
  private static final String STATUS_FAILED = "FAILED";

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
  public MsgBatchVO submitBatch(BatchSendRequestDTO dto) {
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
    MsgBatchVO batch = new MsgBatchVO();
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
    batch.setPriority(dto.getPriority());
    batch.setTenantId(TenantContextHolder.getTenantId());
    // P1-A3: 序列化请求列表存入 payload，支持后续断点续传
    batch.setPayload(YdszJson.toJson(requests));
    msgBatchRepository.save(batch);
    log.info(
        "[Batch] 批次已创建: batchId={} total={} channel={}",
        batchId,
        requests.size(),
        dto.getChannel());

    // 异步执行
    boolean async = dto.getIsAsync() == null || dto.getIsAsync();
    if (async) {
      executeBatchAsync(batchId, requests);
    } else {
      executeBatchSync(batchId, requests);
    }
    return batch;
  }

  @Override
  public void executeBatch(String batchId) {
    MsgBatchQuery query = new MsgBatchQuery();
    query.setBatchId(batchId);
    MsgBatchVO batch = msgBatchRepository.findOne(query).orElse(null);
    if (batch == null) {
      log.warn("[Batch] executeBatch 批次不存在: batchId={}", batchId);
      return;
    }
    List<MessageRequest> requests = parsePayload(batch.getPayload());
    if (requests.isEmpty()) {
      log.warn("[Batch] executeBatch payload 为空: batchId={}", batchId);
      updateBatchStatus(batchId, "FAILED", "payload 为空");
      return;
    }
    doExecuteBatch(batchId, requests, true);
  }

  @Override
  public BatchProgressDTO getProgress(String batchId) {
    if (!StringUtils.hasText(batchId)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("批次 ID 不能为空")
          .build();
    }
    MsgBatchQuery query = new MsgBatchQuery();
    query.setBatchId(batchId);
    MsgBatchVO batch = msgBatchRepository.findOne(query).orElse(null);
    if (batch == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .message("批次不存在: " + batchId)
          .build();
    }
    BatchProgressDTO vo = new BatchProgressDTO();
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

  /**
   * 同步执行批次发送（async=false 时使用）。
   *
   * @param batchId 批次 ID
   * @param requests 待发送消息请求列表
   */
  private void executeBatchSync(String batchId, List<MessageRequest> requests) {
    doExecuteBatch(batchId, requests, true);
  }

  /**
   * P1-A3: 反序列化 payload 为请求列表，异常时返回空列表。
   *
   * @param payload 批次 payload JSON（序列化的请求列表）
   * @return 反序列化后的请求列表，解析失败返回空列表
   */
  // YDIZ-WARN-001 允许保留：泛型擦除，List.class→List<MessageRequest> 编译期无法验证
  @SuppressWarnings("unchecked")
  private List<MessageRequest> parsePayload(String payload) {
    if (!StringUtils.hasText(payload)) {
      return new ArrayList<>(0);
    }
    try {
      List<MessageRequest> requests =
          (List<MessageRequest>) YdszJson.fromJson(payload, List.class, MessageRequest.class);
      return requests != null ? requests : new ArrayList<>(0);
    } catch (Exception e) {
      log.warn("[Batch] payload 解析失败: {}", e.getMessage(), e);
      return new ArrayList<>(0);
    }
  }

  /**
   * 构建请求列表。
   *
   * <p>优先使用显式传入的 requests 列表；否则使用 receiverList + 统一模板参数展开。
   *
   * @param dto 批量发送请求
   * @return 消息请求列表
   */
  private List<MessageRequest> buildRequests(BatchSendRequestDTO dto) {
    if (dto == null) {
      return new ArrayList<>(0);
    }
    // 优先使用显式请求列表
    if (dto.getRequests() != null && !dto.getRequests().isEmpty()) {
      return dto.getRequests();
    }
    // receiverList 模式：用统一模板+参数展开
    if (dto.getReceiverList() == null || dto.getReceiverList().isEmpty()) {
      return new ArrayList<>(0);
    }
    List<MessageRequest> requests = new ArrayList<>(dto.getReceiverList().size());
    for (String receiver : dto.getReceiverList()) {
      MessageRequest req = new MessageRequest();
      req.setMessageId(String.valueOf(snowflakeIdGenerator.nextId()));
      req.setChannel(dto.getChannel());
      req.setReceiver(receiver);
      req.setTemplateCode(dto.getTemplateCode());
      req.setParams(dto.getParams());
      req.setBizType(dto.getBizType());
      requests.add(req);
    }
    return requests;
  }

  /**
   * 逐条执行批次发送。
   *
   * <p>遍历请求列表逐条调用 {@link MessageService#send}，根据返回结果累加 success/failed/skipped 计数。
   * 每 {@link #SSE_FLUSH_INTERVAL} 条或超过 {@link #SSE_FLUSH_TIME_INTERVAL_MS} 毫秒，触发一次 SSE
   * 进度推送并持久化计数。全部完成后发布 {@link BatchCompletedEvent}。
   *
   * <p><b>乐观锁保护：</b>通过 {@link MsgBatchVO#getRevision()} 与 MyBatis-Plus {@code @Version}
   * 自动检测并发覆盖。若持久化时发现 revision 已被修改，记 WARN 并重试一次加载。
   *
   * @param batchId 批次 ID
   * @param requests 待发送消息请求列表（非空）
   * @param incremental 是否增量累加（首次执行=true，会从当前 DB 计数开始累加）
   */
  private void doExecuteBatch(String batchId, List<MessageRequest> requests, boolean incremental) {
    log.info("[Batch] doExecuteBatch 开始: batchId={}, requests={}, incremental={}", batchId, requests.size(), incremental);

    // 1. 加载批次，初始化计数器
    MsgBatchVO batch = loadBatch(batchId);
    if (batch == null) {
      log.error("[Batch] 批次不存在，无法执行: batchId={}", batchId);
      return;
    }

    int total = requests.size();
    int initSuccess = incremental && batch.getSuccess() != null ? batch.getSuccess() : 0;
    int initFailed = incremental && batch.getFailed() != null ? batch.getFailed() : 0;
    int initSkipped = incremental && batch.getSkipped() != null ? batch.getSkipped() : 0;

    batch.setTotal(total);
    batch.setSuccess(initSuccess);
    batch.setFailed(initFailed);
    batch.setSkipped(initSkipped);
    batch.setStatus(STATUS_PROCESSING);
    batch.setStartedAt(LocalDateTime.now());
    msgBatchRepository.save(batch);

    int success = initSuccess;
    int failed = initFailed;
    int skipped = initSkipped;
    long lastFlushAt = System.currentTimeMillis();

    // 2. 逐条发送
    for (int i = 0; i < requests.size(); i++) {
      MessageRequest request = requests.get(i);
      try {
        MessageResult result = messageService.send(request);
        if (result != null && result.isSuccess()) {
          success++;
        } else {
          log.warn("[Batch] 单条发送失败: batchId={}, index={}, error={}",
              batchId, i, result != null ? result.getUserMessage() : "null result");
          failed++;
        }
      } catch (SysException e) {
        // 业务异常：记失败，保留堆栈供排查
        log.warn("[Batch] 单条业务异常: batchId={}, index={}, code={}, msg={}",
            batchId, i, e.getCode(), e.getMessage());
        failed++;
      } catch (Exception e) {
        // 系统异常：记失败，继续执行下一条
        log.error("[Batch] 单条系统异常: batchId={}, index={}", batchId, i, e);
        failed++;
      }

      batch.setSuccess(success);
      batch.setFailed(failed);
      batch.setSkipped(skipped);

      // 3. 按数量或时间阈值触发中间推送/持久化
      long now = System.currentTimeMillis();
      boolean hitCountThreshold = (i + 1) % SSE_FLUSH_INTERVAL == 0;
      boolean hitTimeThreshold = (now - lastFlushAt) >= SSE_FLUSH_TIME_INTERVAL_MS;
      if (hitCountThreshold || hitTimeThreshold || i == requests.size() - 1) {
        flushProgress(batchId, batch, total, success, failed, skipped);
        pushSseProgress(batchId, STATUS_PROCESSING, total, success, failed, skipped);
        lastFlushAt = now;
      }
    }

    // 4. 确定最终状态并持久化
    String finalStatus;
    if (failed == 0) {
      finalStatus = STATUS_COMPLETED;
    } else if (success > 0) {
      finalStatus = STATUS_PARTIAL_FAILED;
    } else {
      finalStatus = STATUS_FAILED;
    }

    batch.setStatus(finalStatus);
    batch.setSuccess(success);
    batch.setFailed(failed);
    batch.setSkipped(skipped);
    batch.setCompletedAt(LocalDateTime.now());
    msgBatchRepository.save(batch);

    // 5. 最终 SSE 推送
    pushSseProgress(batchId, finalStatus, total, success, failed, skipped);

    // 6. 发布领域事件
    String mode = incremental ? "RESUME" : "FULL";
    domainEventPublisher.publish(
        new BatchCompletedEvent(
            TenantContextHolder.getTenantId(),
            batchId, total, success, failed, skipped, mode));

    log.info("[Batch] doExecuteBatch 完成: batchId={}, status={}, success={}, failed={}, skipped={}",
        batchId, finalStatus, success, failed, skipped);
  }

  /**
   * 加载批次（内联 findOne + null 检查）。
   *
   * @param batchId 批次 ID
   * @return 批次 VO，不存在返回 null
   */
  private MsgBatchVO loadBatch(String batchId) {
    MsgBatchQuery query = new MsgBatchQuery();
    query.setBatchId(batchId);
    return msgBatchRepository.findOne(query).orElse(null);
  }

  /**
   * 将批次进度持久化到 DB（利用乐观锁保护并发写入）。
   *
   * <p>当 MyBatis-Plus 返回 0 行更新时（说明 revision 冲突），重新加载最新值后变更重试一次。
   *
   * @param batchId 批次 ID
   * @param batch 当前批次对象（含新计数）
   * @param total 总数
   * @param success 成功数
   * @param failed 失败数
   * @param skipped 跳过数
   */
  private void flushProgress(
      String batchId, MsgBatchVO batch, int total, int success, int failed, int skipped) {
    try {
      boolean ok = msgBatchRepository.save(batch);
      if (!ok) {
        // 乐观锁冲突：加载最新值后合并再写
        log.warn("[Batch] 进度持久化冲突，重试加载: batchId={}", batchId);
        MsgBatchVO fresh = loadBatch(batchId);
        if (fresh != null) {
          fresh.setSuccess(success);
          fresh.setFailed(failed);
          fresh.setSkipped(skipped);
          fresh.setStatus(STATUS_PROCESSING);
          msgBatchRepository.save(fresh);
        }
      }
    } catch (Exception e) {
      // 持久化异常不影响发送主流程，仅记日志
      log.error("[Batch] 进度持久化异常: batchId={}, err={}", batchId, e.getMessage());
    }
  }

  /**
   * 推送批次进度 SSE 事件。
   *
   * <p>构建包含总数/成功/失败/跳过/百分比的进度数据，广播给所有订阅者。
   *
   * @param batchId 批次 ID
   * @param status 状态标识
   * @param total 总数
   * @param success 成功数
   * @param failed 失败数
   * @param skipped 跳过数
   */
  private void pushSseProgress(
      String batchId, String status, int total, int success, int failed, int skipped) {
    try {
      int processed = success + failed + skipped;
      double percent = total > 0 ? Math.round(processed * 10000.0 / total) / 100.0 : 0.0;
      Map<String, Object> data = new HashMap<>(8);
      data.put("batchId", batchId);
      data.put("status", status);
      data.put("total", total);
      data.put("success", success);
      data.put("failed", failed);
      data.put("skipped", skipped);
      data.put("processed", processed);
      data.put("progressPercent", percent);
      data.put("timestamp", System.currentTimeMillis());
      sseEmitterService.broadcastProgress(batchId, data);
    } catch (Exception e) {
      // SSE 推送异常不影响主流程
      log.warn("[Batch] SSE 推送异常: batchId={}, err={}", batchId, e.getMessage());
    }
  }

  private void updateBatchStatus(String batchId, String status, String errorMessage) {
    MsgBatchQuery query = new MsgBatchQuery();
    query.setBatchId(batchId);
    MsgBatchVO batch = msgBatchRepository.findOne(query).orElse(null);
    if (batch == null) {
      return;
    }
    batch.setStatus(status);
    batch.setErrorMessage(errorMessage);
    if (STATUS_COMPLETED.equals(status) || STATUS_FAILED.equals(status)
        || STATUS_PARTIAL_FAILED.equals(status)) {
      batch.setCompletedAt(LocalDateTime.now());
    }
    msgBatchRepository.save(batch);
  }
}

