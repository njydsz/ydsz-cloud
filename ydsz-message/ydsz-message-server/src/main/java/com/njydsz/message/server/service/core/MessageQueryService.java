package com.njydsz.message.server.service.core;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.njydsz.common.core.constant.PageConstants;
import com.njydsz.message.domain.dto.core.MessageLogQueryDTO;
import com.njydsz.message.domain.entity.core.MsgLog;
import com.njydsz.message.infra.mapper.core.MsgLogMapper;

/**
 * 消息查询服务。
 *
 * <p>负责消息发送日志的分页查询、统计聚合等只读操作。
 * 从 {@link MessageServiceImpl}（原 God Class）中提取，与发送职责解耦。
 *
 * <p>TODO: 导出（Excel / CSV）、聚合统计（ChannelStats / CostStats / FunnelStats）待从 Controller 层下沉。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageQueryService {

    private final MsgLogMapper msgLogMapper;

    /**
     * 分页查询消息发送日志。
     *
     * @param query 查询参数（pageNum / pageSize / 多条件）
     * @return 分页结果
     */
    public Page<MsgLog> pageLog(MessageLogQueryDTO query) {
        Page<MsgLog> page = new Page<>(
                query == null ? 1 : query.getPageNum(),
                Math.min(query == null ? 10 : query.getPageSize(), PageConstants.MAX_PAGE_SIZE));
        LambdaQueryWrapper<MsgLog> w = new LambdaQueryWrapper<>();
        if (query != null) {
            w.eq(StringUtils.hasText(query.getChannel()), MsgLog::getChannel, query.getChannel());
            w.eq(StringUtils.hasText(query.getBizType()), MsgLog::getBizType, query.getBizType());
            w.eq(StringUtils.hasText(query.getBizId()), MsgLog::getBizId, query.getBizId());
            w.eq(StringUtils.hasText(query.getStatus()), MsgLog::getStatus, query.getStatus());
            w.eq(StringUtils.hasText(query.getReceiver()), MsgLog::getReceiver, query.getReceiver());
            w.eq(StringUtils.hasText(query.getPriority()), MsgLog::getPriority, query.getPriority());
            w.eq(StringUtils.hasText(query.getRecallStatus()), MsgLog::getRecallStatus, query.getRecallStatus());
            w.eq(StringUtils.hasText(query.getTenantId()), MsgLog::getTenantId, query.getTenantId());
            if (StringUtils.hasText(query.getKeyword())) {
                String kw = query.getKeyword().trim();
                w.and(wrapper -> wrapper
                        .like(MsgLog::getContent, kw)
                        .or().like(MsgLog::getReceiver, kw)
                        .or().like(MsgLog::getTemplateCode, kw)
                        .or().like(MsgLog::getMsgId, kw)
                        .or().like(MsgLog::getBizId, kw));
            }
            if (StringUtils.hasText(query.getStartTime())) {
                w.ge(MsgLog::getCreatedAt, LocalDateTime.parse(query.getStartTime()));
            }
            if (StringUtils.hasText(query.getEndTime())) {
                w.le(MsgLog::getCreatedAt, LocalDateTime.parse(query.getEndTime()));
            }
        }
        w.orderByDesc(MsgLog::getCreatedAt);
        return msgLogMapper.selectPage(page, w);
    }
}
