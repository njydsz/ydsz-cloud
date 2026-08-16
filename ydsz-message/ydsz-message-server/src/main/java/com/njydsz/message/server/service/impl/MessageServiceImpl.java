package com.njydsz.message.server.service.impl.core;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.core.constant.SystemConstants;
import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.publish.DomainEventPublisher;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.safe.sensitive.SensitiveUtil;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.common.util.id.TracerUtils;
import com.njydsz.message.domain.constant.MessageConstants;
import com.njydsz.message.domain.dto.batch.BatchSendResult;
import com.njydsz.message.domain.dto.core.MessageLogQueryDTO;
import com.njydsz.message.domain.dto.core.MessageSendDTO;
import com.njydsz.message.domain.dto.core.RichMediaContent;
import com.njydsz.message.domain.entity.config.MsgTrace;
import com.njydsz.message.domain.entity.core.MsgLog;
import com.njydsz.message.domain.entity.template.MsgTemplate;
import com.njydsz.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.message.domain.enums.receipt.RecallStatusEnum;
import com.njydsz.message.infra.mapper.core.MsgLogMapper;
import com.njydsz.message.server.channel.ChannelRouter;
import com.njydsz.message.server.config.MessageProperties;
import com.njydsz.message.server.config.RetryStrategyResolver;
import com.njydsz.message.server.filter.SensitiveWordFilter;
import com.njydsz.message.server.metric.MessageMetrics;
import com.njydsz.message.server.producer.MessageQueueOperations;
import com.njydsz.message.server.service.batch.AggregateService;
import com.njydsz.message.server.service.canary.CanaryService;
import com.njydsz.message.server.service.chain.SendContext;
import com.njydsz.message.server.service.chain.SendPipeline;
import com.njydsz.message.server.service.config.PreferenceService;
import com.njydsz.message.server.service.config.RouteRuleService;
import com.njydsz.message.server.service.config.SubscriptionService;
import com.njydsz.message.server.service.config.UserChannelBindingService;
import com.njydsz.message.server.service.config.VariableSourceResolver;
import com.njydsz.message.server.service.core.DedupService;
import com.njydsz.message.server.service.core.DeliveryTimeOptimizer;
import com.njydsz.message.server.service.core.MessageQueryService;
import com.njydsz.message.server.service.core.MessageSendService;
import com.njydsz.message.server.service.core.MessageService;
import com.njydsz.message.server.service.core.MessageTraceService;
import com.njydsz.message.server.service.impl.AggregatePersistenceService;
import com.njydsz.message.server.service.impl.ParallelBatchSender;
import com.njydsz.message.server.service.template.TemplateService;
import com.njydsz.message.server.template.RichMediaRenderer;
import com.njydsz.message.server.template.TemplateEngine;
import com.njydsz.message.server.template.TemplateVariableValidator;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

  /** 通道路由器（负责通道选择与消息分发） */
  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  private final ChannelRouter channelRouter;

  /** 模板引擎（变量占位符渲染） */
  private final TemplateEngine templateEngine;

  /** 模板管理服务（加载/校验模板） */
  private final TemplateService templateService;

  /** 消息日志 Mapper（落库 / 查询） */
  private final MsgLogMapper msgLogMapper;

  /** 路由规则服务（通道动态路由） */
  private final RouteRuleService routeRuleService;

  /** 限流服务（通道 / 用户 / 模板多维限流） */
  private final RateLimitService rateLimitService;

  /** 灰度服务（A/B 实验命中判断） */
  private final CanaryService canaryService;

  /** 消息模块配置属性 */
  private final MessageProperties messageProperties;

  /** 消息指标采集（Prometheus） */
  private final MessageMetrics messageMetrics;

  /** 订阅管理服务（退订校验） */
  private final SubscriptionService subscriptionService;

  /** 用户偏好服务（DND / locale / 聚合） */
  private final PreferenceService preferenceService;

  /** 消息聚合服务（批量摘要发送） */
  private final AggregateService aggregateService;

  /** 敏感词过滤器 */
  private final SensitiveWordFilter sensitiveWordFilter;

  /** 重试策略解析器（按通道解析最大重试次数与退避间隔） */
  private final RetryStrategyResolver retryStrategyResolver;

  /** 去重服务（Redis SET NX EX 幂等去重） */
  private final DedupService dedupService;

  /** 消息全链路追踪服务 */
  private final MessageTraceService messageTraceService;

  /** 智能推送时间优化器（用户活跃度画像） */
  private final DeliveryTimeOptimizer deliveryTimeOptimizer;

  /** 富媒体内容渲染器（HTML / Markdown / 纯文本） */
  private final RichMediaRenderer richMediaRenderer;

  /** P0-1: 用户通道绑定服务（userId → 通道联系方式解析） */
  private final UserChannelBindingService userChannelBindingService;

  /** P0-3: 模板变量校验器 */
  private final TemplateVariableValidator templateVariableValidator;

  /** P0-4: 变量数据源解析器 */
  private final VariableSourceResolver variableSourceResolver;

  /** P2-3: 消息队列操作（可选,未配置 MQ 时为 null） */
  private final ObjectProvider<MessageQueueOperations> mqProducerProvider;

  /** P2-15: 并行批量发送器 */
  private final ParallelBatchSender parallelBatchSender;

  /** P1-1: 发送 / 查询子服务（从本类拆分，降低 God Class 复杂度） */
  private final MessageSendService messageSendService;

  private final MessageQueryService messageQueryService;

  /** P0-5: 聚合路径独立 Service（事务安全） */
  private final AggregatePersistenceService aggregatePersistenceService;

  private final ObjectProvider<DomainEventPublisher> eventPublisherProvider;

  /** P1-H1: 发送管线编排引擎（Handler Chain 模式替代原 preprocess 方法） */
  private final SendPipeline sendPipeline;

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
   * @param request 消息请求
   * @param depth 级联深度(0=顶层消息)
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

    // ① 预处理管线：通道校验 → 绑定 → 路由 → 灰度 → 订阅 → 偏好 → 去重 → 抑制 → 限流 → 配额
    SendContext ctx = new SendContext();
    sendPipeline.execute(request, ctx);
    if (ctx.getErrorResult() != null) {
      return ctx.getErrorResult();
    }

    // ② 渲染内容: 模板加载 → 变量填充 → 渲染 → 敏感词 → 富媒体
    RenderedContent rendered = renderContent(request, ctx);

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
    MsgLog logDO = buildLogDO(request, ctx, rendered);

    // ④ 定时/聚合早期 return 路径
    MessageResult earlyResult = handleEarlyReturns(request, ctx, logDO, rendered);
    if (earlyResult != null) {
      return earlyResult;
    }

    // ⑤ 常规落库 PENDING
    msgLogMapper.insert(logDO);
    messageTraceService.recordTrace(
        logDO.getMsgId(),
        MsgTrace.Node.PERSISTED,
        "SUCCESS",
        ctx.getChannel(),
        "消息已落库: status=" + logDO.getStatus());

    // ⑥ 通道分发 + 级联
    MessageResult result =
        messageSendService.dispatch(logDO, ctx.getMatchedRule(), ctx.getReceiver());
    if (result != null && result.isSuccess()) {
      triggerCascade(request, logDO, depth);
    }
    return result;
  }

  /**
   * P1-H1: 渲染阶段 —— 模板加载/变量填充/渲染/敏感词/富媒体。
   *
   * @param request 消息请求
   * @param ctx 管线上下文
   * @return 渲染后的 content/subject
   */
  private RenderedContent renderContent(MessageRequest request, SendContext ctx) {
    String content = request.getContent();
    String subject = request.getSubject();
    String prefLocale = ctx.getPreference() != null ? ctx.getPreference().getLocale() : null;

    if (StringUtils.hasText(ctx.getTemplateCode())) {
      MsgTemplate template =
          templateService.loadByCodeAndChannel(
              ctx.getTemplateCode(),
              ctx.getChannel(),
              prefLocale,
              TenantContextHolder.getTenantId());
      if (template == null) {
        return new RenderedContent(content, subject, true);
      }
      // P0-3: 模板变量类型校验
      if (StringUtils.hasText(template.getVariableDefs())) {
        var varDefs = templateVariableValidator.parse(template.getVariableDefs());
        if (!varDefs.isEmpty() && request.getParams() != null) {
          templateVariableValidator.validateAndFill(
              request.getParams(), varDefs, ctx.getTemplateCode());
        }
      }
      // P0-4: 变量数据源自动拉取
      if (request.getParams() != null) {
        Map<String, Object> varCtx = new HashMap<>();
        if (StringUtils.hasText(request.getBizId())) {
          varCtx.put("bizId", request.getBizId());
        }
        if (StringUtils.hasText(ctx.getBizType())) {
          varCtx.put("bizType", ctx.getBizType());
        }
        varCtx.put("receiver", ctx.getReceiver());
        variableSourceResolver.resolveVariables(ctx.getTemplateCode(), request.getParams(), varCtx);
      }
      if (StringUtils.hasText(template.getContent())) {
        content = templateEngine.render(template.getContent(), request.getParams());
      }
      if (!StringUtils.hasText(subject) && StringUtils.hasText(template.getSubject())) {
        subject = templateEngine.render(template.getSubject(), request.getParams());
      }
    }

    // ⑦-2 敏感词过滤
    if (StringUtils.hasText(content)) {
      content = sensitiveWordFilter.filter(content);
    }

    // P1-2: 富媒体消息渲染
    RichMediaContent richMedia = richMediaRenderer.extractFromParams(request.getParams());
    if (richMedia != null) {
      String renderedContent =
          switch (ctx.getChannel() == null ? "" : ctx.getChannel().toUpperCase()) {
            case "EMAIL" -> richMediaRenderer.renderHtml(richMedia);
            case "INAPP", "DINGTALK", "WECOM", "FEISHU" ->
                richMediaRenderer.renderMarkdown(richMedia);
            case "SMS" -> richMediaRenderer.renderPlainText(richMedia);
            default -> richMediaRenderer.renderPlainText(richMedia);
          };
      if (StringUtils.hasText(renderedContent)) {
        content = renderedContent;
      }
      if (!StringUtils.hasText(subject) && StringUtils.hasText(richMedia.getTitle())) {
        subject = richMedia.getTitle();
      }
    }
    return new RenderedContent(content, subject, false);
  }

  /** P1-3: 构造落库 MsgLog。 */
  private MsgLog buildLogDO(MessageRequest request, SendContext ctx, RenderedContent rendered) {
    MsgLog logDO = new MsgLog();
    logDO.setChannel(ctx.getChannel());
    logDO.setBizType(ctx.getBizType());
    logDO.setBizId(request.getBizId());
    logDO.setReceiver(ctx.getReceiver());
    logDO.setTemplateCode(ctx.getTemplateCode());
    logDO.setTemplateParams(YdszJson.toJson(request.getParams()));
    logDO.setContent(rendered.content);
    logDO.setStatus(MessageStatusEnum.PENDING.name());
    logDO.setPriority(resolvePriority(request));
    logDO.setSenderId(SystemConstants.SYSTEM_USER_ID);
    logDO.setCanary(ctx.getCanaryFlag());
    logDO.setCanaryKey(ctx.getCanaryKeyForLog);
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
      MessageRequest request, SendContext ctx, MsgLog logDO, RenderedContent rendered) {
    // 模板缺失: renderContent 标记 templateMissing=true 时直接返回失败
    if (rendered.templateMissing()) {
      return MessageResult.fail(ctx.getChannel(), "模板不存在: " + ctx.getTemplateCode());
    }
    // ⑧-2 P0-3: 定时消息 —— scheduledAt 非空且在未来时,落库 SCHEDULED 不立即发送
    if (request.getScheduledAt() != null && request.getScheduledAt().isAfter(LocalDateTime.now())) {
      logDO.setStatus(MessageStatusEnum.SCHEDULED.name());
      msgLogMapper.insert(logDO);
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
        if (optimalTime != null && optimalTime.isAfter(LocalDateTime.now().plusMinutes(5))) {
          request.setScheduledAt(optimalTime);
          logDO.setScheduledAt(optimalTime);
          logDO.setStatus(MessageStatusEnum.SCHEDULED.name());
          msgLogMapper.insert(logDO);
          messageTraceService.recordTrace(
              logDO.getMsgId(),
              MsgTrace.Node.SCHEDULED,
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
   * P1-H1: 渲染阶段产出。
   *
   * @param content 渲染后内容
   * @param subject 渲染后标题
   * @param templateMissing 模板缺失标志(渲染阶段无法返回 fail,由调用方检查)
   */
  private record RenderedContent(String content, String subject, boolean templateMissing) {}

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
  private void triggerCascade(MessageRequest request, MsgLog parentLog, int depth) {
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
    for (MessageRequest child : cascadeTo) {
      if (child == null) {
        continue;
      }
      child.setParentMsgId(parentLog.getMsgId());
      try {
        sendInternal(child, depth + 1);
      } catch (Exception e) {
        log.warn(
            "[Message] 级联消息发送失败,不影响其他级联: parentMsgId={} err={}",
            parentLog.getMsgId(),
            e.getMessage());
      }
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
    // P2-15: 使用并行批量发送器（通道级线程池 + Semaphore 流控）
    String channel =
        batch.stream()
            .filter(r -> r != null && StringUtils.hasText(r.getChannel()))
            .map(MessageRequest::getChannel)
            .findFirst()
            .orElse("INAPP");
    BatchSendResult result = parallelBatchSender.sendBatch(batch, channel, this::send);
    result.setBatchId(batchId);
    log.info(
        "[Message] 批量发送完成: batchId={} total={} success={} failed={} skipped={}",
        batchId,
        result.getTotal(),
        result.getSuccess(),
        result.getFailed(),
        result.getSkipped());
    return result;
  }

  @Override
  public Page<MsgLog> pageLog(MessageLogQueryDTO query) {
    return messageQueryService.pageLog(query);
  }

  @Override
  public MessageResult cancelScheduledMessage(String msgId) {
    if (!StringUtils.hasText(msgId)) {
      return MessageResult.fail(null, "消息 ID 不能为空");
    }
    MsgLog logDO =
        msgLogMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MsgLog>()
                .eq(MsgLog::getMsgId, msgId)
                .last("LIMIT 1"));
    if (logDO == null) {
      return MessageResult.fail(null, "消息不存在: " + msgId);
    }
    if (!MessageStatusEnum.SCHEDULED.name().equals(logDO.getStatus())) {
      return MessageResult.fail(logDO.getChannel(), "仅允许取消定时消息（当前状态: " + logDO.getStatus() + "）");
    }
    logDO.setStatus(MessageStatusEnum.SKIPPED.name());
    logDO.setErrorMessage("USER_CANCELLED");
    msgLogMapper.updateById(logDO);
    log.info("[Message] 定时消息已取消: msgId={} channel={}", msgId, logDO.getChannel());
    return MessageResult.ok(logDO.getChannel(), msgId);
  }

  private String resolvePriority() {
    try {
      String p = messageProperties.getDefaultPriority();
      return StringUtils.hasText(p) ? p : "NORMAL";
    } catch (Exception e) {
      return "NORMAL";
    }
  }

  /** P0-3: 解析发送优先级,优先使用请求中的 priority,回退全局配置。 */
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
          .resultCode(BaseResultCode.BAD_REQUEST)
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
   * P0-3: 异步发送消息（先落库 PENDING → 再投递 MQ）。
   *
   * <p>可靠性保证流程：
   *
   * <ol>
   *   <li>生成 messageId（雪花 ID）
   *   <li>落库 PENDING 记录（DB 是 Source of Truth）
   *   <li>投递到 MQ，消费端处理后更新状态
   *   <li>MQ 投递失败 → 落库 PENDING 记录仍存在，由恢复扫描器补偿
   * </ol>
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
    // ① 先落库 PENDING（DB 是 Source of Truth）
    MsgLog logDO = new MsgLog();
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
      msgLogMapper.insert(logDO);
      log.info(
          "[Message] 异步消息已落库 PENDING: msgId={} channel={}", logDO.getMsgId(), request.getChannel());
    } catch (Exception e) {
      log.error(
          "[Message] 异步消息落库失败,降级直接投递 MQ: msgId={} err={}", request.getMessageId(), e.getMessage());
    }
    // ② 投递到 MQ
    MessageQueueOperations mqOps = mqProducerProvider.getIfAvailable();
    if (mqOps == null) {
      // MQ 未配置，降级为同步发送
      log.warn("[Message] MQ 未配置,异步消息降级同步发送: msgId={}", request.getMessageId());
      return send(request);
    }
    try {
      mqOps.asyncSend(request);
      log.info(
          "[Message] 异步消息已投递 MQ: msgId={} channel={}",
          request.getMessageId(),
          request.getChannel());
      return MessageResult.ok(request.getChannel(), request.getMessageId());
    } catch (Exception e) {
      log.error(
          "[Message] 异步投递 MQ 失败,降级同步发送: msgId={} err={}", request.getMessageId(), e.getMessage());
      return send(request);
    }
  }

  /** 发布领域事件到 Outbox（DomainEventPublisher 不可用时静默跳过）。 */
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
