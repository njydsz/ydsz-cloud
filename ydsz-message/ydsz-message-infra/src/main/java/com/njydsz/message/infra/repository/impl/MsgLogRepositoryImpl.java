package com.njydsz.message.infra.repository.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.message.domain.entity.core.MsgLog;
import com.njydsz.message.domain.enums.core.MessageChannelEnum;
import com.njydsz.message.domain.enums.core.MessagePriorityEnum;
import com.njydsz.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.message.domain.enums.receipt.ReceiptStatusEnum;
import com.njydsz.message.domain.enums.receipt.RecallStatusEnum;
import com.njydsz.message.infra.entity.MsgLogDO;
import com.njydsz.message.infra.mapper.core.MsgLogMapper;
import com.njydsz.message.infra.repository.MsgLogRepository;

/**
 * 消息发送日志 Repository 实现。
 *
 * <p>基于 MyBatis-Plus 的 {@link MsgLogMapper} 实现 {@link MsgLogRepository} 接口。
 *
 * <p>负责领域实体 {@link MsgLog} 与数据库实体 {@link MsgLogDO} 之间的转换，确保领域层不依赖持久化细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MsgLogRepositoryImpl implements MsgLogRepository {

  private final MsgLogMapper msgLogMapper;

  @Override
  public int insert(MsgLog entity) {
    MsgLogDO po = toDO(entity);
    return msgLogMapper.insert(po);
  }

  @Override
  public MsgLog selectById(String id) {
    MsgLogDO po = msgLogMapper.selectById(id);
    return toEntity(po);
  }

  @Override
  public int updateById(MsgLog entity) {
    MsgLogDO po = toDO(entity);
    return msgLogMapper.updateById(po);
  }

  @Override
  public int update(LambdaUpdateWrapper<MsgLog> wrapper) {
    return msgLogMapper.update(null, wrapper);
  }

  @Override
  public List<MsgLog> selectList(LambdaQueryWrapper<MsgLog> wrapper) {
    return msgLogMapper.selectList(wrapper).stream().map(this::toEntity).toList();
  }

  @Override
  public Long selectCount(LambdaQueryWrapper<MsgLog> wrapper) {
    return msgLogMapper.selectCount(wrapper);
  }

  @Override
  public Page<MsgLog> selectPage(Page<MsgLog> page, LambdaQueryWrapper<MsgLog> wrapper) {
    Page<MsgLogDO> poPage = new Page<>(page.getCurrent(), page.getSize());
    poPage = msgLogMapper.selectPage(poPage, wrapper);
    Page<MsgLog> entityPage = new Page<>(poPage.getCurrent(), poPage.getSize(), poPage.getTotal());
    entityPage.setRecords(poPage.getRecords().stream().map(this::toEntity).toList());
    return entityPage;
  }

  // ===== 领域实体 ↔ 数据库实体转换 =====

  private MsgLogDO toDO(MsgLog entity) {
    if (entity == null) {
      return null;
    }
    return MsgLogDO.builder()
        .id(entity.getId())
        .tenantId(entity.getTenantId())
        .createdBy(entity.getCreatedBy())
        .createdAt(entity.getCreatedAt())
        .updatedBy(entity.getUpdatedBy())
        .updatedAt(entity.getUpdatedAt())
        .deleted(entity.getDeleted())
        .channel(entity.getChannel() != null ? entity.getChannel().name() : null)
        .bizType(entity.getBizType())
        .bizId(entity.getBizId())
        .receiver(entity.getReceiver())
        .templateCode(entity.getTemplateCode())
        .templateParams(entity.getTemplateParams())
        .content(entity.getContent())
        .status(entity.getStatus() != null ? entity.getStatus().name() : null)
        .errorMessage(entity.getErrorMessage())
        .priority(entity.getPriority() != null ? entity.getPriority().name() : null)
        .senderId(entity.getSenderId())
        .messageGroup(entity.getMessageGroup())
        .batchId(entity.getBatchId())
        .routeRuleId(entity.getRouteRuleId())
        .canary(entity.getCanary())
        .canaryKey(entity.getCanaryKey())
        .dedupKey(entity.getDedupKey())
        .recallStatus(entity.getRecallStatus() != null ? entity.getRecallStatus().name() : null)
        .recallAt(entity.getRecallAt())
        .receiptStatus(entity.getReceiptStatus() != null ? entity.getReceiptStatus().name() : null)
        .receiptAt(entity.getReceiptAt())
        .retryCount(entity.getRetryCount())
        .nextRetryAt(entity.getNextRetryAt())
        .providerTraceId(entity.getProviderTraceId())
        .costMs(entity.getCostMs())
        .cost(entity.getCost())
        .traceId(entity.getTraceId())
        .msgId(entity.getMsgId())
        .topic(entity.getTopic())
        .reconsumeTimes(entity.getReconsumeTimes())
        .parentMsgId(entity.getParentMsgId())
        .scheduledAt(entity.getScheduledAt())
        .build();
  }

  private MsgLog toEntity(MsgLogDO po) {
    if (po == null) {
      return null;
    }
    MsgLog entity = new MsgLog();
    entity.setId(po.getId());
    entity.setTenantId(po.getTenantId());
    entity.setCreatedBy(po.getCreatedBy());
    entity.setCreatedAt(po.getCreatedAt());
    entity.setUpdatedBy(po.getUpdatedBy());
    entity.setUpdatedAt(po.getUpdatedAt());
    entity.setDeleted(po.getDeleted());
    entity.setChannel(MessageChannelEnum.parse(po.getChannel()));
    entity.setBizType(po.getBizType());
    entity.setBizId(po.getBizId());
    entity.setReceiver(po.getReceiver());
    entity.setTemplateCode(po.getTemplateCode());
    entity.setTemplateParams(po.getTemplateParams());
    entity.setContent(po.getContent());
    entity.setStatus(MessageStatusEnum.valueOf(po.getStatus()));
    entity.setErrorMessage(po.getErrorMessage());
    entity.setPriority(MessagePriorityEnum.valueOf(po.getPriority()));
    entity.setSenderId(po.getSenderId());
    entity.setMessageGroup(po.getMessageGroup());
    entity.setBatchId(po.getBatchId());
    entity.setRouteRuleId(po.getRouteRuleId());
    entity.setCanary(po.getCanary());
    entity.setCanaryKey(po.getCanaryKey());
    entity.setDedupKey(po.getDedupKey());
    entity.setRecallStatus(RecallStatusEnum.valueOf(po.getRecallStatus()));
    entity.setRecallAt(po.getRecallAt());
    entity.setReceiptStatus(ReceiptStatusEnum.valueOf(po.getReceiptStatus()));
    entity.setReceiptAt(po.getReceiptAt());
    entity.setRetryCount(po.getRetryCount());
    entity.setNextRetryAt(po.getNextRetryAt());
    entity.setProviderTraceId(po.getProviderTraceId());
    entity.setCostMs(po.getCostMs());
    entity.setCost(po.getCost());
    entity.setTraceId(po.getTraceId());
    entity.setMsgId(po.getMsgId());
    entity.setTopic(po.getTopic());
    entity.setReconsumeTimes(po.getReconsumeTimes());
    entity.setParentMsgId(po.getParentMsgId());
    entity.setScheduledAt(po.getScheduledAt());
    return entity;
  }
}
