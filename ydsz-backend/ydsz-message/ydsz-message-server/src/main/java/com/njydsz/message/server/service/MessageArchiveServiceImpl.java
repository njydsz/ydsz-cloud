package com.njydsz.message.server.service.archive.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.message.domain.entity.core.MsgLogDO;
import com.njydsz.message.infra.mapper.core.MsgLogMapper;
import com.njydsz.message.server.service.archive.MessageArchiveService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息归档搜索服务实现（P0-5）。
 *
 * <p>当 ES 可用时使用 Elasticsearch 全文搜索；不可用时降级为数据库 LIKE 查询。
 * 通过 {@code ydsz.message.archive.es-enabled} 配置开关。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageArchiveServiceImpl implements MessageArchiveService {

    private final MsgLogMapper msgLogMapper;

    @Value("${ydsz.message.archive.es-enabled:false}")
    private boolean esEnabled;

    @Override
    public void index(MsgLogDO logDO) {
        if (!esEnabled || logDO == null) {
            return;
        }
        // ES 索引逻辑（当 ES 可用时通过 ElasticsearchRestTemplate 索引）
        // 当前为 mock 降级，仅记录日志
        log.debug("[Archive] 索引消息: id={} channel={} status={}",
                logDO.getId(), logDO.getChannel(), logDO.getStatus());
    }

    @Override
    public void batchIndex(List<MsgLogDO> logList) {
        if (!esEnabled || logList == null || logList.isEmpty()) {
            return;
        }
        log.debug("[Archive] 批量索引: count={}", logList.size());
        for (MsgLogDO logDO : logList) {
            index(logDO);
        }
    }

    @Override
    public Page<MsgLogDO> search(String keyword, String channel, String status, String bizType,
                                 LocalDateTime startTime, LocalDateTime endTime,
                                 String tenantId, int pageNum, int pageSize) {
        if (esEnabled) {
            // ES 全文搜索（ES 可用时实现）
            log.info("[Archive] ES 搜索: keyword={} channel={} status={}", keyword, channel, status);
        }
        // 降级：数据库 LIKE 查询
        return searchByDatabase(keyword, channel, status, bizType,
                startTime, endTime, tenantId, pageNum, pageSize);
    }

    @Override
    public void delete(String id) {
        if (!esEnabled || !StringUtils.hasText(id)) {
            return;
        }
        log.debug("[Archive] 删除索引: id={}", id);
    }

    /**
     * 数据库 LIKE 降级搜索。
     */
    private Page<MsgLogDO> searchByDatabase(String keyword, String channel, String status, String bizType,
                                            LocalDateTime startTime, LocalDateTime endTime,
                                            String tenantId, int pageNum, int pageSize) {
        Page<MsgLogDO> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<MsgLogDO> wrapper = new LambdaQueryWrapper<MsgLogDO>()
                .eq(MsgLogDO::getTenantId, tenantId)
                .eq(StringUtils.hasText(channel), MsgLogDO::getChannel, channel)
                .eq(StringUtils.hasText(status), MsgLogDO::getStatus, status)
                .eq(StringUtils.hasText(bizType), MsgLogDO::getBizType, bizType)
                .ge(startTime != null, MsgLogDO::getCreatedAt, startTime)
                .le(endTime != null, MsgLogDO::getCreatedAt, endTime)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(MsgLogDO::getContent, keyword)
                        .or().like(MsgLogDO::getReceiver, keyword)
                        .or().like(MsgLogDO::getTemplateCode, keyword)
                        .or().like(MsgLogDO::getBizId, keyword))
                .orderByDesc(MsgLogDO::getCreatedAt);

        return msgLogMapper.selectPage(page, wrapper);
    }
}
