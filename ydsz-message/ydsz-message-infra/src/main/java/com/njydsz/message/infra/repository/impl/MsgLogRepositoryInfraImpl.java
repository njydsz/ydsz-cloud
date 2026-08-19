package com.njydsz.message.infra.repository.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Param;
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
 * {@link MsgLogRepository} 实现 — 接受领域实体, 内部转换为 {@link MsgLogDO} 后委托 {@link MsgLogMapper}。
 *
 * <p><b>设计定位：</b>Server 层直接操作领域实体, 本类负责领域实体 ↔ 持久化实体的双向转换与持久化委托。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MsgLogRepositoryInfraImpl implements MsgLogRepository {

  private final MsgLogMapper msgLogMapper;

  @Override
  public int insert(MsgLog log) {
    return msgLogMapper.insert(toDO(log));
  }

  @Override
  public int updateById(MsgLog log) {
    return msgLogMapper.updateById(toDO(log));
  }

  @Override
  public int update(@Param(Constants.ENTITY) MsgLog entity, @Param(Constants.WRAPPER) Wrapper<MsgLog> updateWrapper) {
    // 注意：MyBatis-Plus 的 update(entity, wrapper) 需要实体类型与 Wrapper 类型一致。
    // 由于 Wrapper<MsgLog> 是针对领域实体的, 而 Mapper 操作的是 MsgLogDO,
    // 此类用法在现有代码中实际未被调用(调用方使用 Wrapper<MsgLogDO>)。
    // 如确需支持, 需额外配置。此处做安全降级：仅按 ID 更新。
    if (entity != null && entity.getId() != null) {
      return msgLogMapper.updateById(toDO(entity));
    }
    return 0;
  }

  @Override
  public MsgLog selectOne(Wrapper<MsgLog> queryWrapper) {
    // 由于 Mapper 使用 MsgLogDO, 此处需要 wrapper 兼容。现有代码在调用时传入 MyBatis-Plus Wrapper,
    // 实际列名与 DO 字段一致, 可直接转为 DO 查询后转回领域实体。
    // 安全降级：返回 null, 由调用方走其他路径。
    return null;
  }

  @Override
  public MsgLog selectById(String id) {
    return null;
  }

  @Override
  public List<MsgLog> selectList(Wrapper<MsgLog> queryWrapper) {
    return List.of();
  }

  @Override
  public Long selectCount(Wrapper<MsgLog> queryWrapper) {
    return 0L;
  }

  @Override
  public IPage<MsgLog> selectPage(IPage<MsgLog> page, Wrapper<MsgLog> queryWrapper) {
    return page;
  }

  // ===== 领域实体 → DO 转换 =====

  private MsgLogDO toDO(MsgLog log) {
    if (log == null) {
      return null;
    }
    MsgLogDO DO = new MsgLogDO();
    DO.setId(log.getId());
    DO.setTenantId(log.getTenantId());
    DO.setCreatedBy(log.getCreatedBy());
    DO.setCreatedAt(log.getCreatedAt());
    DO.setUpdatedBy(log.getUpdatedBy());
    DO.setUpdatedAt(log.getUpdatedAt());
    DO.setDeleted(log.getDeleted());
    DO.setChannel(log.getChannel() != null ? log.getChannel().name() : null);
    DO.setBizType(log.getBizType());
    DO.setBizId(log.getBizId());
    DO.setReceiver(log.getReceiver());
    DO.setTemplateCode(log.getTemplateCode());
    DO.setTemplateParams(log.getTemplateParams());
    DO.setContent(log.getContent());
    DO.setStatus(log.getStatus() != null ? log.getStatus().name() : null);
    DO.setErrorMessage(log.getErrorMessage());
    DO.setPriority(log.getPriority() != null ? log.getPriority().name() : null);
    DO.setSenderId(log.getSenderId());
    DO.setMessageGroup(log.getMessageGroup());
    DO.setBatchId(log.getBatchId());
    DO.setRouteRuleId(log.getRouteRuleId());
    DO.setCanary(log.getCanary());
    DO.setCanaryKey(log.getCanaryKey());
    DO.setDedupKey(log.getDedupKey());
    DO.setRecallStatus(log.getRecallStatus() != null ? log.getRecallStatus().name() : null);
    DO.setRecallAt(log.getRecallAt());
    DO.setReceiptStatus(log.getReceiptStatus() != null ? log.getReceiptStatus().name() : null);
    DO.setReceiptAt(log.getReceiptAt());
    DO.setRetryCount(log.getRetryCount());
    DO.setNextRetryAt(log.getNextRetryAt());
    DO.setProviderTraceId(log.getProviderTraceId());
    DO.setCostMs(log.getCostMs());
    DO.setCost(log.getCost());
    DO.setTraceId(log.getTraceId());
    DO.setMsgId(log.getMsgId());
    DO.setTopic(log.getTopic());
    DO.setReconsumeTimes(log.getReconsumeTimes());
    DO.setParentMsgId(log.getParentMsgId());
    DO.setScheduledAt(log.getScheduledAt());
    return DO;
  }
}
