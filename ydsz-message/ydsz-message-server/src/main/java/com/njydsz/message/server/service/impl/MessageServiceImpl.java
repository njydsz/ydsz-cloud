package com.njydsz.message.server.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.core.constant.SystemConstants;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.publish.DomainEventPublisher;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.queue.constant.YdszMessageTopics;
import com.njydsz.common.safe.sensitive.SensitiveUtil;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.common.thread.util.ExecutorUtils;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.common.util.id.TracerUtils;
import com.njydsz.message.domain.constant.MessageConstants;
import com.njydsz.message.domain.dto.BatchSendRequestDTO;
import com.njydsz.message.domain.dto.BatchSendResult;
import com.njydsz.message.domain.dto.MessageLogQueryDTO;
import com.njydsz.message.domain.dto.MessageSendDTO;
import com.njydsz.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.message.domain.enums.receipt.RecallStatusEnum;
import com.njydsz.message.domain.event.OutboxEvent;
import com.njydsz.message.domain.repository.MsgLogRepository;
import com.njydsz.message.domain.repository.OutboxEventRepository;
import com.njydsz.message.domain.vo.MsgBatchVO;
import com.njydsz.message.domain.vo.MsgLogVO;
import com.njydsz.message.server.channel.ChannelRouter;
import com.njydsz.message.server.config.MessageProperties;
import com.njydsz.message.server.metric.MessageMetrics;
import com.njydsz.message.server.producer.MessageQueueOperations;
import com.njydsz.message.server.service.batch.BatchService;
import com.njydsz.message.server.service.chain.PipelineTemplate;
import com.njydsz.message.server.service.chain.SendContext;
import com.njydsz.message.server.service.chain.SendPipelineFacade;
import com.njydsz.message.server.service.core.DeliveryTimeOptimizer;
import com.njydsz.message.server.service.core.MessageQueryService;
import com.njydsz.message.server.service.core.MessageRenderService;
import com.njydsz.message.server.service.core.MessageRenderService.RenderedContent;
import com.njydsz.message.server.service.core.MessageSendService;
import com.njydsz.message.server.service.core.MessageSendTxService;
import com.njydsz.message.server.service.core.MessageService;
import com.njydsz.message.server.service.core.MessageTraceService;

/**
 * 消息服务实现（核心）。
 *
 * <p>统一的消息发送入口：根据 {@code ydsz_message_service} 配置选择渠道策略 → 渲染模板 → 解析变量 →
 *
 * <p>发送前幂等校验 → 调用渠道 Sender → 异步落库 → 触发回执。
 *
 * <p>支持单发、批量、聚合、定时、灰度、A/B 等多种发送模式。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {
  /** 提前发送窗口（分钟） */
  private static final int SEND_AHEAD_MINUTES = 5;

  /** 异步等待超时（秒） */
  private static final int ASYNC_TIMEOUT_SECONDS = 30;


  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  /** 通道路由器（负责通道选择与消息分发） */
  private final ChannelRouter channelRouter;

  /** 消息日志 Repository（落库 / 查询） */
  private final MsgLogRepository msgLogRepository;

  /** 消息模块配置属性 */
  private final MessageProperties messageProperties;

  /** 消息指标采集（Prometheus） */
  private final MessageMetrics messageMetrics;

  /** 消息全链路追踪服务 */
  private final MessageTraceService messageTraceService;

  /** 智能推送时间优化器（用户活跃度画像） */
  private final DeliveryTimeOptimizer deliveryTimeOptimizer;

  /** P2-3: 消息队列操作（可选,未配置 MQ 时为 null） */
  private final ObjectProvider<MessageQueueOperations> mqProducerProvider;

  /** 批次服务（批量异步发送） */
  private final BatchService batchService;

  /** P1-1: 发送 / 查询子服务（从本类拆分，降低 God Class 复杂度） */
  private final MessageSendService messageSendService;

  private final MessageQueryService messageQueryService;

  /** P0-5: 聚合路径独立 Service（事务安全） */
  private final AggregatePersistenceService aggregatePersistenceService;

  private final ObjectProvider<DomainEventPublisher> eventPublisherProvider;

  /** P0-A1: Outbox 事件仓储（异步消息投递） */
  private final OutboxEventRepository outboxEventRepository;

  /** P1-A3: 消息内容渲染服务（从本类拆分，降低 God Class 复杂度） */
  private final MessageRenderService messageRenderService;

  /** P2-1: 管线模板门面（自动选择模板 + 按需组合 Handler） */
  private final SendPipelineFacade sendPipelineFacade;

  /** P2-A6: 消息发送事务包装（解决同类 self-invocation 事务不生效问题） */
  private final MessageSendTxService messageSendTxService;

  /** P2-C5: 级联消息发送线程池（固定大小，避免级联消息耗尽主线程池） */
  // CHECKSTYLE.OFF: RegexpSinglelineJava - 级联消息专用池，线程数固定为4，避免耗尽主线程池
  private final Executor cascadeExecutor = ExecutorUtils.newFixedThreadPool(4, "message-cascade");
  // CHECKSTYLE.ON: RegexpSinglelineJava

  @Override
  public MessageResult send(MessageRequest request) {
    return sendInternal(request, 0);
  }

  /**
   * P2-6: 内部发送方法,携带级联深度。
   * 
   * <p>顶层消息 depth=0,级联子消息 depth 递增,超过 {@link MessageConstants#MAX_CASCADE_DEPTH} 跳过。 级联触发时机：父消息
   * {@code doDispatch} 成功后,遍历 {@link MessageRequest#getCascadeTo()}, 为每个子消息设置 {@code parentMsgId =
   * 父 msgId} 后递归调用本方法。 单条级联消息失败不影响其他级联消息(try-catch 吞异常记 WARN)。
   * 
   * <p>P1-3: 拆分为 preprocess → renderContent → persistAndDispatch 三个阶段, 聚合路径(insert + appendOrStart
   * + updateById)用 {@code @Transactional} 保证原子性。
   * 
   * <p>P1-A4: 支持异步发送模式。当 {@code ydsz.message.defaultAsync=true} 且请求未显式要求同步时, 落库 PENDING + 写入
   * OutboxEvent 后立即返回, 由 OutboxEventScheduler 异步投递 MQ。
   * 
   *
   * @param request 参数说明
   * @param depth 参数说明
   * @return 返回值说明
   */
  private MessageResult sendInternal(MessageRequest request, int depth) {
    if (request == null) {
      return MessageResult.fail(null, "消息请求为空");
    }
    // P2-6: 级联深度保护(防御性,正常路径下 triggerCascade 已提前拦截)
    if (depth > MessageConstants.MAX_CASCADE_DEPTH) {
      log.warn(
          "[Message] 级联深度超限,拒绝发送: depth={} max={} receiver={}",
          depth,
          MessageConstants.MAX_CASCADE_DEPTH,
          SensitiveUtil.scanAndMask(request.getReceiver()));
      return MessageResult.fail(request.getChannel(), "级联深度超限");
    }

    // ① 预处理管线：P2-1 根据场景自动选择模板（模板发送/简单直发/批量/回调）
    SendContext ctx = new SendContext();
    PipelineTemplate template = sendPipelineFacade.resolveTemplate(request);
    sendPipelineFacade.execute(request, ctx, template);
    if (ctx.getErrorResult() != null) {
      return ctx.getErrorResult();
    }

    // ② 渲染内容: 模板加载 → 变量填充 → 渲染 → 敏感词 → 富媒体（P1-A3: 委托 MessageRenderService）
    RenderedContent rendered = messageRenderService.renderContent(request, ctx);

    // P1-7: 消息内容大小限制
    int maxLen = messageProperties.getMaxContentLength();
    if (maxLen > 0 && rendered.content() != null && rendered.content().length() > maxLen) {
      messageMetrics.recordSend(ctx.getChannel(), "CONTENT_TOO_LARGE", 0);
      log.warn(
          "[Message] 内容超长拒绝发送: length={} max={} channel={}",
          rendered.content().length(),
          maxLen,
          ctx.getChannel());
      return MessageResult.fail(
          ctx.getChannel(), "消息内容超过最大长度限制: " + rendered.content().length() + " > " + maxLen);
    }

    // ③ 构造落库对象
    MsgLogVO logDO = buildLogDO(request, ctx, rendered);

    // ④ 定时/聚合早期 return 路径
    MessageResult earlyResult = handleEarlyReturns(request, ctx, logDO, rendered);
    if (earlyResult != null) {
      return earlyResult;
    }

    // P1-A4: 异步发送模式 —— 落库 PENDING + 写入 OutboxEvent 后立即返回，由 OutboxEventScheduler 异步投递 MQ
    if (messageProperties.isDefaultAsync()) {
      return dispatchAsync(logDO, ctx);
    }

    // ⑤ 常规落库 PENDING（同步模式）— P2-A6: 通过事务包装确保 OutboxDomainEventPublisher 感知事务上下文
    messageSendTxService.insertLogAndOutbox(logDO, null);

    // ⑥ 通道分发 + 级联
    MessageResult result =
        messageSendService.dispatch(logDO, ctx.getMatchedRule(), ctx.getReceiver());
    if (result != null && result.isSuccess()) {
      triggerCascade(request, logDO, depth);
    }
    return result;
  }

  /**
   * P1-A4: 异步分发 —— 落库 PENDING + 写入 OutboxEvent，由 OutboxEventScheduler 异步投递 MQ。
   *
   * <p>对标阿里消息中心发送入口 100% 异步化：API 仅落库 PENDING + 返回 msgId，实际发送由 Worker 池消费。
   *
   * @param logDO 消息日志实体（已构造，未落库）
   * @param ctx 管线上下文
   * @return 发送结果（含 msgId 供追踪）
   */
  private MessageResult dispatchAsync(MsgLogVO logDO, SendContext ctx) {
    // P2-A6: 构造 OutboxEvent, 与 msgLog 落库在同一事务中(原子性保证)
    OutboxEvent outboxEvent =
        new OutboxEvent(
            "Message",
            logDO.getMsgId(),
            "MessageAsyncDispatch",
            YdszJson.toJson(buildMessageRequestFromLog(logDO, ctx)),
            TenantContextHolder.getTenantId());
    // P2-A6: 落库 PENDING + 写 Outbox 在同一事务中(OutboxDomainEventPublisher 因此感知事务上下文)
    messageSendTxService.insertLogAndOutbox(logDO, outboxEvent);
    log.info(
        "[Message] 异步模式: 消息已写入 Outbox: msgId={} outboxId={} channel={}",
        logDO.getMsgId(),
        outboxEvent.getId(),
        ctx.getChannel());
    return MessageResult.ok(ctx.getChannel(), logDO.getMsgId());
  }

  /**
   * P1-A4: 从 MsgLogVO 和 SendContext 重建 MessageRequest（用于 Outbox 序列化）。
   *
   * @param logDO 消息日志实体
   * @param ctx 管线上下文
   * @return MessageRequest
   */
  private MessageRequest buildMessageRequestFromLog(MsgLogVO logDO, SendContext ctx) {
    MessageRequest request = new MessageRequest();
    request.setChannel(ctx.getChannel());
    request.setReceiver(logDO.getReceiver());
    request.setContent(logDO.getContent());
    request.setBizType(logDO.getBizType());
    request.setBizId(logDO.getBizId());
    request.setTemplateCode(logDO.getTemplateCode());
    request.setMessageId(logDO.getMsgId());
    request.setPriority(logDO.getPriority() != null ? logDO.getPriority() : null);
    return request;
  }

  /**
   * P1-3: 构造落库 MsgLogVO。
   *
   * @param request 参数说明
   * @param ctx 参数说明
   * @param rendered 参数说明
   * @return 返回值说明
   */
  private MsgLogVO buildLogDO(MessageRequest request, SendContext ctx, RenderedContent rendered) {
    MsgLogVO logDO = new MsgLogVO();
    logDO.setChannel(ctx.getChannel());
    logDO.setBizType(ctx.getBizType());
    logDO.setBizId(request.getBizId());
    logDO.setReceiver(ctx.getReceiver());
    logDO.setTemplateCode(ctx.getTemplateCode());
    logDO.setTemplateParams(YdszJson.toJson(request.getParams()));
    logDO.setContent(rendered.content());
    logDO.setStatus(MessageStatusEnum.PENDING.name());
    logDO.setPriority(resolvePriority(request));
    logDO.setSenderId(SystemConstants.SYSTEM_USER_ID);
    logDO.setCanary(ctx.getCanaryFlag());
    logDO.setCanaryKey(ctx.getCanaryKeyForLog());
    logDO.setRecallStatus(RecallStatusEnum.NONE.name());
    logDO.setReceiptStatus("NONE");
    logDO.setRetryCount(0);
    logDO.setTraceId(TracerUtils.getOrCreateTraceId());
    logDO.setMsgId(
        StringUtils.hasText(request.getMessageId())
            ? request.getMessageId()
            : String.valueOf(snowflakeIdGenerator.nextId()));
    logDO.setDedupKey(ctx.getDedupKey());
    logDO.setParentMsgId(request.getParentMsgId());
    logDO.setScheduledAt(request.getScheduledAt());
    if (ctx.getMatchedRule() != null) {
      logDO.setRouteRuleId(ctx.getMatchedRule().getId());
    }
    logDO.setTenantId(TenantContextHolder.getTenantId());
    return logDO;
  }

  /**
   * P1-3: 处理定时消息/智能定时/聚合的早期 return 路径。
   *
   * <p>聚合路径(insert + appendOrStart + updateById)未加 @Transactional, 因 Spring 同类 self-invocation
   * 事务不生效。appendOrStart 失败时 insert 的 PENDING 记录由恢复扫描器兜底,不致数据不一致。
   *
   * @param request 消息请求
   * @param ctx 预处理上下文
   * @param logDO 待落库对象(方法内会修改 status/scheduledAt)
   * @param rendered 渲染结果(含 templateMissing 标志)
   * @return 非 null 表示已处理(调用方直接返回),null 表示继续走常规分发
   */
  private MessageResult handleEarlyReturns(
      MessageRequest request, SendContext ctx, MsgLogVO logDO, RenderedContent rendered) {
    // 模板缺失: renderContent 标记 templateMissing=true 时直接返回失败
    if (rendered.templateMissing()) {
      return MessageResult.fail(ctx.getChannel(), "模板不存在: " + ctx.getTemplateCode());
    }
    // ⑧-2 P0-3: 定时消息 —— scheduledAt 非空且在未来时,落库 SCHEDULED 不立即发送
    if (request.getScheduledAt() != null && request.getScheduledAt().isAfter(LocalDateTime.now())) {
      logDO.setStatus(MessageStatusEnum.SCHEDULED.name());
      msgLogRepository.save(logDO);
      log.info(
          "[Message] 定时消息已入库: msgId={} scheduledAt={} channel={}",
          logDO.getMsgId(),
          logDO.getScheduledAt(),
          ctx.getChannel());
      return MessageResult.ok(ctx.getChannel(), logDO.getMsgId());
    }

    // P1-1: 智能推送时间优化
    if (request.getScheduledAt() == null
        && StringUtils.hasText(ctx.getReceiver())
        && !"URGENT".equals(resolvePriority(request))) {
      try {
        LocalDateTime optimalTime =
            deliveryTimeOptimizer.getOptimalDeliveryTime(ctx.getReceiver(), ctx.getChannel());
        if (optimalTime != null && optimalTime.isAfter(LocalDateTime.now().plusMinutes(SEND_AHEAD_MINUTES))) {
          request.setScheduledAt(optimalTime);
          logDO.setScheduledAt(optimalTime);
          logDO.setStatus(MessageStatusEnum.SCHEDULED.name());
          msgLogRepository.save(logDO);
          messageTraceService.recordTrace(
              logDO.getMsgId(),
              "SCHEDULED",
              "SUCCESS",
              ctx.getChannel(),
              "智能定时: optimalAt=" + optimalTime);
          log.info(
              "[Message] 智能定时推送: msgId={} receiver={} optimalAt={}",
              logDO.getMsgId(),
              SensitiveUtil.scanAndMask(ctx.getReceiver()),
              optimalTime);
          return MessageResult.ok(ctx.getChannel(), logDO.getMsgId());
        }
      } catch (Exception e) {
        log.debug(
            "[Message] 智能推送时间优化失败,降级立即发送: receiver={} err={}",
            SensitiveUtil.scanAndMask(ctx.getReceiver()),
            e.getMessage());
      }
    }

    // P0-5: 聚合判断 —— 委托 AggregatePersistenceService 执行事务安全的原子操作
    if (ctx.getPreference() != null
        && Integer.valueOf(1).equals(ctx.getPreference().getDigestEnabled())
        && StringUtils.hasText(ctx.getBizType())
        && StringUtils.hasText(ctx.getReceiver())) {
      aggregatePersistenceService.persistAggregated(
          logDO, ctx.getBizType(), ctx.getReceiver(), ctx.getChannel(), logDO.getTenantId());
      return MessageResult.ok(ctx.getChannel(), logDO.getMsgId());
    }
    return null;
  }

  /**
   * P2-6: 触发级联发送。
   *
   * <p>遍历 {@code request.getCascadeTo()},为每个子消息设置 {@code parentMsgId = 父 msgId}, 递归调用 {@link
   * #sendInternal}。单条级联失败不影响其他级联(try-catch 吞异常记 WARN)。 深度超限时整体跳过并记 WARN。
   *
   * @param request 父消息请求(含 cascadeTo 列表)
   * @param parentLog 父消息落库记录(提供 msgId 作为子消息的 parentMsgId)
   * @param depth 父消息的级联深度
   */
  private void triggerCascade(MessageRequest request, MsgLogVO parentLog, int depth) {
    List<MessageRequest> cascadeTo = request.getCascadeTo();
    if (cascadeTo == null || cascadeTo.isEmpty()) {
      return;
    }
    if (depth + 1 > MessageConstants.MAX_CASCADE_DEPTH) {
      log.warn(
          "[Message] 级联深度超限,跳过全部级联: parentMsgId={} depth={} max={}",
          parentLog.getMsgId(),
          depth,
          MessageConstants.MAX_CASCADE_DEPTH);
      return;
    }
    // P2-C5: 使用 CompletableFuture 并行发送级联消息
    List<CompletableFuture<Boolean>> futures = cascadeTo.stream()
        .filter(child -> child != null)
        .map(child -> CompletableFuture.supplyAsync(() -> {
          try {
            child.setParentMsgId(parentLog.getMsgId());
            sendInternal(child, depth + 1);
            return true;
          } catch (Exception e) {
            log.warn("[Message] 级联消息发送失败,不影响其他级联: parentMsgId={} childMsgId={} err={}",
                parentLog.getMsgId(),
                child.getMessageId(),
                e.getMessage());
            return false;
          }
        }, cascadeExecutor))
        .toList();

    // 等待所有级联消息发送完成（带超时）
    try {
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
          .get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      log.warn("[Message] 级联消息发送超时,部分消息可能未完成: parentMsgId={}", parentLog.getMsgId());
    } catch (Exception e) {
      log.error("[Message] 级联消息发送异常: parentMsgId={}", parentLog.getMsgId(), e);
    }
  }

  @Override
  public MessageResult sendDirect(MessageSendDTO dto) {
    if (dto == null) {
      return MessageResult.fail(null, "发送参数为空");
    }
    MessageRequest request = new MessageRequest();
    request.setChannel(dto.getChannel());
    request.setTemplateCode(dto.getTemplateCode());
    request.setReceiver(dto.getReceiver());
    request.setParams(dto.getParams());
    request.setContent(dto.getContent());
    request.setSubject(dto.getSubject());
    request.setBizType(dto.getBizType());
    request.setBizId(dto.getBizId());
    request.setMessageId(dto.getMessageId());
    return send(request);
  }

  @Override
  public BatchSendResult batchSend(List<MessageRequest> requests, String batchId) {
    if (requests == null || requests.isEmpty() || !StringUtils.hasText(batchId)) {
      return new BatchSendResult(batchId, 0, 0, 0, 0);
    }
    // 限制单批最大 100 条,防止阻塞过久
    int limit = Math.min(requests.size(), MessageConstants.BATCH_SEND_MAX_SIZE);
    List<MessageRequest> batch = requests.subList(0, limit);
    // 统一设置 bizId = batchId 便于进度查询
    for (MessageRequest req : batch) {
      if (req != null) {
        req.setBizId(batchId);
      }
    }
    // 使用 BatchService.submitBatch 异步批量发送
    BatchSendRequestDTO dto = new BatchSendRequestDTO();
    dto.setBatchId(batchId);
    dto.setRequests(batch);
    dto.setAsync(true);
    MsgBatchVO msgBatch = batchService.submitBatch(dto);
    // 异步模式下返回初始进度（实际处理在后台线程池执行）
    BatchSendResult result = new BatchSendResult(batchId, msgBatch.getTotal(), 0, 0, 0);
    log.info(
        "[Message] 批量发送已提交: batchId={} total={} status={}",
        batchId,
        msgBatch.getTotal(),
        msgBatch.getStatus());
    return result;
  }

  @Override
  public PageResponse<List<MsgLogVO>> pageLog(MessageLogQueryDTO query) {
    return messageQueryService.pageLog(query);
  }

  @Override
  public MessageResult cancelScheduledMessage(String msgId) {
    if (!StringUtils.hasText(msgId)) {
      return MessageResult.fail(null, "消息 ID 不能为空");
    }
    MessageLogQueryDTO query = new MessageLogQueryDTO();
    query.setMsgId(msgId);
    query.setPageNum(1);
    query.setPageSize(1);
    MsgLogVO logVO = msgLogRepository.findOne(query).orElse(null);
    if (logVO == null) {
      return MessageResult.fail(null, "消息不存在: " + msgId);
    }
    if (!MessageStatusEnum.SCHEDULED.name().equals(logVO.getStatus())) {
      return MessageResult.fail(logVO.getChannel(), "仅允许取消定时消息（当前状态: " + logVO.getStatus() + "）");
    }
    logVO.setStatus(MessageStatusEnum.SKIPPED.name());
    logVO.setErrorMessage("USER_CANCELLED");
    msgLogRepository.update(logVO);
    log.info("[Message] 定时消息已取消: msgId={} channel={}", msgId, logVO.getChannel());
    return MessageResult.ok(logVO.getChannel(), msgId);
  }

  private String resolvePriority() {
    try {
      String p = messageProperties.getDefaultPriority();
      return StringUtils.hasText(p) ? p : "NORMAL";
    } catch (Exception e) {
      return "NORMAL";
    }
  }

  /**
   * P0-3: 解析发送优先级,优先使用请求中的 priority,回退全局配置。
   *
   * @param request 参数说明
   * @return 返回值说明
   */
  private String resolvePriority(MessageRequest request) {
    if (request != null && StringUtils.hasText(request.getPriority())) {
      return request.getPriority().trim().toUpperCase();
    }
    return resolvePriority();
  }

  private String buildRateLimitKey(String channel, String bizType) {
    return (channel == null ? "unknown" : channel) + ":" + (bizType == null ? "default" : bizType);
  }

  /**
   * private String buildDedupKey(MessageRequest request) { if
   * (StringUtils.hasText(request.getMessageId())) { return request.getMessageId(); } if
   * (StringUtils.hasText(request.getBizType()) && StringUtils.hasText(request.getBizId()) &&
   * StringUtils.hasText(request.getTemplateCode()) && StringUtils.hasText(request.getReceiver())) {
   * return request.getBizType() + ":" + request.getBizId() + ":" + request.getTemplateCode() + ":"
   * + request.getReceiver(); } return null; }
   *
   * <p>/** P2-3: 事务消息发送。
   *
   * <p>通过 RocketMQ 半消息机制,确保通知请求仅在本地事务校验（通道/模板有效性）通过后才投递。 半消息发送后由 {@link
   * com.njydsz.message.server.producer.MessageTransactionListener} 执行校验,COMMIT 后消费端异步调用 {@link
   * #send} 完成实际发送。
   *
   * <p>降级策略：未配置 RocketMQ 时直接走同步 {@link #send}。
   *
   * @param request 消息发送请求
   * @return 发送结果
   */
  @Override
  public MessageResult sendTransactionally(MessageRequest request) {
    if (request == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("消息请求不能为空")
          .build();
    }
    MessageQueueOperations mqProducer = mqProducerProvider.getIfAvailable();
    if (mqProducer == null) {
      log.warn("[Message] MQ 未配置,事务消息降级为同步发送: channel={}", request.getChannel());
      return send(request);
    }
    try {
      String msgId = mqProducer.sendTransactionMessage(request);
      log.info(
          "[Message] 事务消息半消息已提交: messageId={} msgId={} channel={}",
          request.getMessageId(),
          msgId,
          request.getChannel());
      return MessageResult.ok(request.getChannel(), msgId);
    } catch (Exception e) {
      log.error(
          "[Message] 事务消息发送失败,降级同步发送: channel={} err={}", request.getChannel(), e.getMessage());
      return send(request);
    }
  }

  /**
   * P0-A1: 异步发送消息（先落库 PENDING → 写入 Outbox → 异步投递 MQ）。
   *
   * <p>可靠性保证流程（事务一致性修复）：
   *
   * <ol>
   *   <li>幂等校验：同 messageId 的 PENDING/SENDING/SUCCESS 记录已存在时直接返回
   *   <li>生成 messageId（雪花 ID）
   *   <li>落库 PENDING 记录 + 写入 OutboxEvent（同事务，DB 是 Source of Truth）
   *   <li>OutboxEventScheduler 异步扫描 Outbox 表并投递到 MQ
   *   <li>MQ 投递失败 → Outbox 扫描器重试，不降级为同步发送（避免重复落库）
   * </ol>
   *
   * <p><b>事务一致性保证：</b>MQ 投递失败时不会降级调用 {@link #send}，避免产生重复 PENDING 记录。 PENDING 记录由
   * OutboxEventScheduler 扫描补偿，保证最终一致性。
   *
   * @param request 消息发送请求
   * @return 发送结果
   */
  @Override
  public MessageResult sendAsync(MessageRequest request) {
    if (request == null) {
      return MessageResult.fail(null, "消息请求为空");
    }
    // 确保有 messageId
    if (!StringUtils.hasText(request.getMessageId())) {
      request.setMessageId(String.valueOf(snowflakeIdGenerator.nextId()));
    }
    // P0-A1: 幂等校验 —— 同 messageId 的 PENDING/SENDING/SUCCESS 记录已存在时直接返回，避免重复落库
    MessageLogQueryDTO idempotentQuery = new MessageLogQueryDTO();
    idempotentQuery.setMsgId(request.getMessageId());
    idempotentQuery.setPageNum(1);
    idempotentQuery.setPageSize(10);
    List<MsgLogVO> existingLogs = msgLogRepository.findList(idempotentQuery);
    MsgLogVO existingLog = existingLogs.stream()
        .filter(vo -> {
          String s = vo.getStatus();
          return MessageStatusEnum.PENDING.name().equals(s)
              || MessageStatusEnum.SENDING.name().equals(s)
              || MessageStatusEnum.SUCCESS.name().equals(s);
        })
        .findFirst()
        .orElse(null);
    if (existingLog != null) {
      log.info(
          "[Message] 异步消息幂等命中,跳过重复落库: msgId={} status={}",
          request.getMessageId(),
          existingLog.getStatus());
      return MessageResult.ok(existingLog.getChannel(), existingLog.getMsgId());
    }
    // ① 先落库 PENDING（DB 是 Source of Truth）
    MsgLogVO logDO = new MsgLogVO();
    logDO.setMsgId(request.getMessageId());
    logDO.setChannel(request.getChannel());
    logDO.setBizType(request.getBizType());
    logDO.setBizId(request.getBizId());
    logDO.setReceiver(request.getReceiver());
    logDO.setTemplateCode(request.getTemplateCode());
    logDO.setContent(request.getContent());
    logDO.setStatus(MessageStatusEnum.PENDING.name());
    logDO.setPriority(resolvePriority(request));
    logDO.setRetryCount(0);
    logDO.setReceiptStatus("NONE");
    logDO.setRecallStatus(RecallStatusEnum.NONE.name());
    logDO.setTraceId(TracerUtils.getOrCreateTraceId());
    logDO.setSenderId(SystemConstants.SYSTEM_USER_ID);
    logDO.setTenantId(TenantContextHolder.getTenantId());
    logDO.setTopic(YdszMessageTopics.TOPIC_MESSAGE);
    try {
      msgLogRepository.save(logDO);
      log.info(
          "[Message] 异步消息已落库 PENDING: msgId={} channel={}", logDO.getMsgId(), request.getChannel());
    } catch (Exception e) {
      log.error("[Message] 异步消息落库失败: msgId={} err={}", request.getMessageId(), e.getMessage(), e);
      return MessageResult.fail(request.getChannel(), "消息落库失败: " + e.getMessage());
    }
    // ② 写入 Outbox 表（与业务同事务语义，由 OutboxEventScheduler 异步投递 MQ）
    try {
      OutboxEvent outboxEvent =
          new OutboxEvent(
              "Message",
              logDO.getMsgId(),
              "MessageAsyncDispatch",
              YdszJson.toJson(request),
              TenantContextHolder.getTenantId());
      outboxEventRepository.save(outboxEvent);
      log.info(
          "[Message] 异步消息已写入 Outbox: msgId={} outboxId={}",
          request.getMessageId(),
          outboxEvent.getId());
    } catch (Exception e) {
      // Outbox 落库失败不阻塞主流程，PENDING 记录由恢复扫描器补偿
      log.error(
          "[Message] Outbox 落库失败,由恢复扫描器补偿: msgId={} err={}",
          request.getMessageId(),
          e.getMessage());
    }
    return MessageResult.ok(request.getChannel(), request.getMessageId());
  }

  /**
   * 发布领域事件到 Outbox（DomainEventPublisher 不可用时静默跳过）。
   *
   * @param aggregateType 参数说明
   * @param aggregateId 参数说明
   * @param eventType 参数说明
   * @param payload 参数说明
   */
  private void publishEvent(
      String aggregateType, String aggregateId, String eventType, String payload) {
    DomainEventPublisher publisher = eventPublisherProvider.getIfAvailable();
    if (publisher != null) {
      publisher.publish(
          DomainEvent.builder()
              .aggregateType(aggregateType)
              .aggregateId(aggregateId)
              .eventType(eventType)
              .metadata("payload", payload)
              .build());
    }
  }
}
