package com.njydsz.message.server.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.njydsz.message.domain.entity.batch.MsgAggregate;
import com.njydsz.message.domain.entity.core.MsgLog;
import com.njydsz.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.message.infra.mapper.core.MsgLogMapper;
import com.njydsz.message.server.service.batch.AggregateService;

/**
 * P0-5: 聚合路径持久化独立 Service。
 *
 * <p>将原 {@code MessageServiceImpl.handleEarlyReturns()} 中的聚合路径
 * （insert + appendOrStart + updateById 三步）提取到此类，
 * 通过 Spring 代理调用使 {@code @Transactional} 生效。
 *
 * <p>原实现中同类 self-invocation 导致事务不生效，
 * 提取到独立 Bean 后 Spring AOP 代理可正常拦截事务边界。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AggregatePersistenceService {

    private final MsgLogMapper msgLogMapper;
    private final AggregateService aggregateService;

    /**
     * 原子性地执行聚合路径：insert PENDING + appendOrStart + updateById 标记 AGGREGATED。
     *
     * <p>三步操作在同一个事务中，任一步失败回滚，不产生不一致的 PENDING 记录。
     *
     * @param logDO     待落库的消息日志
     * @param bizType   聚合组
     * @param receiver  接收人
     * @param channel   通道
     * @param tenantId  租户 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void persistAggregated(MsgLog logDO, String bizType, String receiver,
                                  String channel, String tenantId) {
        msgLogMapper.insert(logDO);
        aggregateService.appendOrStart(bizType, receiver, channel, tenantId);
        logDO.setStatus(MessageStatusEnum.PENDING.name());
        logDO.setErrorMessage("AGGREGATED");
        msgLogMapper.updateById(logDO);
        log.info("[Aggregate] 已加入聚合批次(事务): msgId={} group={} receiver={}",
                logDO.getMsgId(), bizType, receiver);
    }
}
