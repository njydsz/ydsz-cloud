package com.njydsz.message.server.service.impl.batch;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.core.constant.PageConstants;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.lock.core.DistributedLocker;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.message.domain.constant.MessageConstants;
import com.njydsz.message.domain.enums.batch.AggregateBatchStatusEnum;
import com.njydsz.message.domain.query.MsgAggregateQuery;
import com.njydsz.message.domain.repository.MsgAggregateRepository;
import com.njydsz.message.domain.vo.MsgAggregateVO;
import com.njydsz.message.domain.vo.MsgTemplateVO;
import com.njydsz.message.server.service.TemplateService;
import com.njydsz.message.server.service.batch.AggregateService;
import com.njydsz.message.server.service.core.MessageService;
import com.njydsz.message.server.template.TemplateEngine;

/**
 * 消息聚合服务实现。
 *
 * <p>按 (bizKey, channel, user) 维度对短时间内高频触发的同一类消息进行合并去重，
 *
 * <p>对应实体 {@code ydsz_msg_aggregate}。窗口期内同一业务键仅发送 1 条聚合消息，
 *
 * <p>避免对用户造成骚扰。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AggregateServiceImpl implements AggregateService {
  /** 锁等待时间（秒） */
  private static final int LOCK_WAIT_SECONDS = 3;

  /** 锁 TTL（秒） */
  private static final int LOCK_TTL_SECONDS = 10;


  /** 默认聚合频率窗口(分钟) */
  private static final long DEFAULT_FREQUENCY_MINUTES = 30L;

  /** 摘要模板编码前缀,完整编码 = 前缀 + aggregateGroup(bizType) */
  private static final String DIGEST_TEMPLATE_PREFIX = "DIGEST_";

  /** 默认摘要模板内容(未配置摘要模板时回退) */
  private static final String DEFAULT_DIGEST_TEMPLATE = "您有 ${count} 条 ${group} 相关消息,请及时查看";

  /** 聚合批次 Repository */
  private final MsgAggregateRepository msgAggregateRepository;

  /** 消息发送服务（flush 时回调发送） */
  private final MessageService messageService;

  /** 模板引擎（摘要渲染） */
  private final TemplateEngine templateEngine;

  /** 模板管理服务（加载摘要模板） */
  private final TemplateService templateService;

  /** 分布式锁 */
  private final DistributedLocker distributedLocker;

  @Override
  public MsgAggregateVO appendOrStart(
      String group, String receiver, String channel, String tenantId) {
    if (!StringUtils.hasText(group) || !StringUtils.hasText(receiver)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("聚合组与接收人不能为空")
          .build();
    }
    String tid = StringUtils.hasText(tenantId) ? tenantId : TenantContextHolder.getTenantId();
    String lockKey = MessageConstants.AGGREGATE_LOCK_PREFIX + group + ":" + receiver;
    String lockValue = null;
    try {
      lockValue = distributedLocker.tryLock(lockKey, LOCK_WAIT_SECONDS, LOCK_TTL_SECONDS, TimeUnit.SECONDS);
      if (lockValue == null) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .message("获取聚合锁失败: " + group)
            .build();
      }
      // 查 PENDING 批次
      MsgAggregateQuery pendingQuery = new MsgAggregateQuery();
      pendingQuery.setAggregateGroup(group);
      pendingQuery.setReceiver(receiver);
      pendingQuery.setBatchStatus(AggregateBatchStatusEnum.PENDING.name());
      MsgAggregateVO batch = msgAggregateRepository.findOne(pendingQuery).orElse(null);
      LocalDateTime now = LocalDateTime.now();
      if (batch != null) {
        batch.setMessageCount((batch.getMessageCount() == null ? 0 : batch.getMessageCount()) + 1);
        batch.setLastMessageAt(now);
        msgAggregateRepository.update(batch);
        return batch;
      }
      // 新建 PENDING 批次
      MsgAggregateVO entity = new MsgAggregateVO();
      entity.setAggregateGroup(group);
      entity.setReceiver(receiver);
      entity.setChannel(channel);
      entity.setBatchStatus(AggregateBatchStatusEnum.PENDING.name());
      entity.setMessageCount(1);
      entity.setFirstMessageAt(now);
      entity.setLastMessageAt(now);
      entity.setScheduledSendAt(now.plusMinutes(DEFAULT_FREQUENCY_MINUTES));
      entity.setTenantId(tid);
      msgAggregateRepository.save(entity);
      log.info(
          "[Aggregate] 新建批次: group={} receiver={} scheduledAt={}",
          group,
          receiver,
          entity.getScheduledSendAt());
      return entity;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("聚合锁等待中断")
          .build();
    } finally {
      if (lockValue != null) {
        distributedLocker.unlock(lockKey, lockValue);
      }
    }
  }

  @Override
  public int flushDue() {
    LocalDateTime now = LocalDateTime.now();
    MsgAggregateQuery dueQuery = new MsgAggregateQuery();
    dueQuery.setBatchStatus(AggregateBatchStatusEnum.READY.name());
    dueQuery.setScheduledSendAtBefore(now);
    List<MsgAggregateVO> due = msgAggregateRepository.findList(dueQuery);
    int sent = 0;
    for (MsgAggregateVO batch : due) {
      if (sendBatch(batch)) {
        sent++;
      }
    }
    if (sent > 0) {
      log.info("[Aggregate] flushDue 发送 {} 个到期批次", sent);
    }
    return sent;
  }

  @Override
  public int flushByGroup(String group, String receiver) {
    if (!StringUtils.hasText(group) || !StringUtils.hasText(receiver)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("聚合组与接收人不能为空")
          .build();
    }
    // 先把 PENDING 批次流转为 READY,统一由 sendBatch 的 CAS 占有发送
    msgAggregateRepository.updateStatusByGroup(
        group, receiver, AggregateBatchStatusEnum.PENDING.name(), AggregateBatchStatusEnum.READY.name());
    MsgAggregateQuery readyQuery = new MsgAggregateQuery();
    readyQuery.setAggregateGroup(group);
    readyQuery.setReceiver(receiver);
    readyQuery.setBatchStatus(AggregateBatchStatusEnum.READY.name());
    List<MsgAggregateVO> batches = msgAggregateRepository.findList(readyQuery);
    int sent = 0;
    for (MsgAggregateVO batch : batches) {
      if (sendBatch(batch)) {
        sent++;
      }
    }
    log.info("[Aggregate] flushByGroup 发送 {} 个批次: group={} receiver={}", sent, group, receiver);
    return sent;
  }

  @Override
  public PageResponse<List<MsgAggregateVO>> page(PageQuery query) {
    MsgAggregateQuery aggQuery = new MsgAggregateQuery();
    if (query != null) {
      aggQuery.setPageNum(query.getPageNum());
      aggQuery.setPageSize(Math.min(query.getPageSize(), PageConstants.MAX_PAGE_SIZE));
    }
    return msgAggregateRepository.findPage(aggQuery);
  }

  /**
   * 发送单个聚合批次:CAS 占有(READY→SENDING) → 渲染摘要 → 调 MessageService 发送 → 更新 SENT。
   *
   * <p>P1-2: 通过 CAS 占有 SENDING 中间态,保证多实例并发调用 flushDue/flushByGroup 时
   * 同一批次只会被一个实例发送,避免重复发送。发送失败/异常时回退 READY 等待下一轮重试。
   *
   * @param batch 聚合批次
   * @return true 表示发送成功
   */
  private boolean sendBatch(MsgAggregateVO batch) {
    // CAS 占有: READY → SENDING,updated=0 表示已被其他实例占有
    int claimed =
        msgAggregateRepository.updateStatus(
            batch.getId(), AggregateBatchStatusEnum.READY.name(), AggregateBatchStatusEnum.SENDING.name());
    if (claimed == 0) {
      log.debug("[Aggregate] 批次已被其他实例占有,跳过: id={}", batch.getId());
      return false;
    }
    batch.setBatchStatus(AggregateBatchStatusEnum.SENDING.name());
    try {
      // 渲染摘要内容：优先按 bizType 查找摘要模板 DIGEST_{group},回退默认模板
      Map<String, Object> params = new HashMap<>();
      params.put("count", batch.getMessageCount());
      params.put("group", batch.getAggregateGroup());
      String digestTemplate = loadDigestTemplate(batch);
      String digest = templateEngine.render(digestTemplate, params);
      batch.setDigestContent(digest);
      MessageRequest request = new MessageRequest();
      request.setChannel(batch.getChannel());
      request.setReceiver(batch.getReceiver());
      request.setContent(digest);
      request.setBizType("AGGREGATE");
      request.setBizId(batch.getId());
      MessageResult result = messageService.send(request);
      boolean ok = result != null && result.isSuccess();
      if (ok) {
        batch.setBatchStatus(AggregateBatchStatusEnum.SENT.name());
        batch.setSentAt(LocalDateTime.now());
        msgAggregateRepository.update(batch);
        return true;
      }
      log.warn(
          "[Aggregate] 批次发送失败,回退 READY: id={} err={}",
          batch.getId(),
          result == null ? "无响应" : result.getUserMessage());
      revertToReady(batch.getId());
      return false;
    } catch (Exception e) {
      log.error("[Aggregate] 批次发送异常,回退 READY: id={} err={}", batch.getId(), e.getMessage(), e);
      revertToReady(batch.getId());
      return false;
    }
  }

  /**
   * 发送失败时将批次状态从 SENDING 回退到 READY,等待下一轮重试。
   *
   * @param batchId 批次 ID
   */
  private void revertToReady(String batchId) {
    try {
      msgAggregateRepository.updateStatus(
          batchId, AggregateBatchStatusEnum.SENDING.name(), AggregateBatchStatusEnum.READY.name());
    } catch (Exception revertEx) {
      log.error(
          "[Aggregate] 回退 READY 失败,批次滞留 SENDING: id={} err={}", batchId, revertEx.getMessage());
    }
  }

  /**
   * 加载摘要模板：按约定编码 DIGEST_{aggregateGroup} 查找，找到则用模板 content，否则回退默认摘要文案。
   *
   * @param batch 聚合批次（取 aggregateGroup/channel/tenantId 查模板）
   * @return 摘要模板内容字符串
   */
  private String loadDigestTemplate(MsgAggregateVO batch) {
    String group = batch.getAggregateGroup();
    if (!StringUtils.hasText(group)) {
      return DEFAULT_DIGEST_TEMPLATE;
    }
    try {
      MsgTemplateVO tpl =
          templateService.loadByCodeAndChannel(
              DIGEST_TEMPLATE_PREFIX + group, batch.getChannel(), null, batch.getTenantId());
      if (tpl != null && StringUtils.hasText(tpl.getContent())) {
        return tpl.getContent();
      }
    } catch (Exception e) {
      log.debug("[Aggregate] 摘要模板加载失败,回退默认: group={} err={}", group, e.getMessage());
    }
    return DEFAULT_DIGEST_TEMPLATE;
  }
}
