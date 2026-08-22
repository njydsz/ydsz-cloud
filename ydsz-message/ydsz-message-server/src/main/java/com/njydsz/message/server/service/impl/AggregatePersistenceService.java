package com.njydsz.message.server.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.message.domain.repository.MsgLogRepository;
import com.njydsz.message.domain.vo.MsgLogVO;
import com.njydsz.message.server.service.batch.AggregateService;

/**
 * P0-5: 聚合路径持久化独立 Service。
 *
 * <p>将原 {@code MessageServiceImpl.handleEarlyReturns()} 中的聚合路径 （insert + appendOrStart + updateById
 * 三步）提取到此类， 通过 Spring 代理调用使 {@code @Transactional} 生效。
 *
 * <p>原实现中同类 self-invocation 导致事务不生效， 提取到独立 Bean 后 Spring AOP 代理可正常拦截事务边界。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AggregatePersistenceService {

  private final MsgLogRepository msgLogRepository;
  private final AggregateService aggregateService;

  /**
   * 原子性地执行聚合路径：insert PENDING + appendOrStart + updateById 标记 AGGREGATED。
   *
   * <p>三步操作在同一个事务中，任一步失败回滚，不产生不一致的 PENDING 记录。
   *
   * @param logVO 待落库的消息日志 VO
   * @param bizType 聚合组
   * @param receiver 接收人
   * @param channel 通道
   * @param tenantId 租户 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public void persistAggregated(
      MsgLogVO logVO, String bizType, String receiver, String channel, String tenantId) {
    msgLogRepository.save(logVO);
    aggregateService.appendOrStart(bizType, receiver, channel, tenantId);
    logVO.setStatus(MessageStatusEnum.PENDING.name());
    logVO.setErrorMessage("AGGREGATED");
    msgLogRepository.update(logVO);
    log.info(
        "[Aggregate] 已加入聚合批次(事务): msgId={} group={} receiver={}",
        logVO.getMsgId(),
        bizType,
        receiver);
  }
}
