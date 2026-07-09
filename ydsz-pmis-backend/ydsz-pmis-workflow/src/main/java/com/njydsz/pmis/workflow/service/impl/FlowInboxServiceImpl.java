package com.njydsz.pmis.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.workflow.entity.FlowInboxDO;
import com.njydsz.pmis.workflow.mapper.FlowInboxMapper;
import com.njydsz.pmis.workflow.service.FlowInboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 站内信服务实现（P2-4）
 *
 * <p>基于 {@code pmis_flow_inbox} 表实现工作流模块本地站内信通道。
 * 作为外部通知中心服务的降级方案，确保 Feign 不可用时通知不丢失。
 *
 * @author ydsz-pmis-team
 * @since 1.9.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowInboxServiceImpl implements FlowInboxService {

    private final FlowInboxMapper inboxMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void send(String receiverId, String messageType, String title, String content,
                     String instanceId, String taskId, String level, String tenantId) {
        if (!StringUtils.hasText(receiverId)) {
            return;
        }
        FlowInboxDO inbox = new FlowInboxDO();
        inbox.setReceiverId(receiverId);
        inbox.setMessageType(messageType);
        inbox.setTitle(title);
        inbox.setContent(content);
        inbox.setInstanceId(instanceId);
        inbox.setTaskId(taskId);
        inbox.setLevel(level != null ? level : "INFO");
        inbox.setReadStatus(false);
        inbox.setTenantId(tenantId);
        inboxMapper.insert(inbox);
        log.debug("[Inbox] 站内信写入: receiverId={} type={} title={}", receiverId, messageType, title);
    }

    @Override
    public List<FlowInboxDO> listInbox(String receiverId, String tenantId,
                                        boolean onlyUnread, int offset, int limit) {
        if (!StringUtils.hasText(receiverId)) {
            return List.of();
        }
        return inboxMapper.selectInboxByUser(receiverId, tenantId, onlyUnread, offset, limit);
    }

    @Override
    public long countUnread(String receiverId, String tenantId) {
        if (!StringUtils.hasText(receiverId)) {
            return 0;
        }
        return inboxMapper.countInbox(receiverId, tenantId, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(String inboxId, String receiverId) {
        if (!StringUtils.hasText(inboxId) || !StringUtils.hasText(receiverId)) {
            return;
        }
        FlowInboxDO inbox = inboxMapper.selectById(inboxId);
        if (inbox == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "站内信不存在: " + inboxId);
        }
        if (!receiverId.equals(inbox.getReceiverId())) {
            throw new BizException(BizErrorCode.FORBIDDEN, "无权操作他人的站内信");
        }
        if (Boolean.TRUE.equals(inbox.getReadStatus())) {
            return; // 已读，跳过
        }
        inbox.setReadStatus(true);
        inbox.setReadAt(LocalDateTime.now());
        inboxMapper.updateById(inbox);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchMarkRead(List<String> inboxIds, String receiverId, String tenantId) {
        if (inboxIds == null || inboxIds.isEmpty() || !StringUtils.hasText(receiverId)) {
            return 0;
        }
        return inboxMapper.batchMarkRead(receiverId, tenantId, inboxIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markAllRead(String receiverId, String tenantId) {
        if (!StringUtils.hasText(receiverId)) {
            return 0;
        }
        LambdaUpdateWrapper<FlowInboxDO> wrapper = new LambdaUpdateWrapper<FlowInboxDO>()
                .eq(FlowInboxDO::getReceiverId, receiverId)
                .eq(FlowInboxDO::getReadStatus, false)
                .eq(FlowInboxDO::getDeleted, 0)
                .set(FlowInboxDO::getReadStatus, true)
                .set(FlowInboxDO::getReadAt, LocalDateTime.now());
        if (StringUtils.hasText(tenantId)) {
            wrapper.eq(FlowInboxDO::getTenantId, tenantId);
        }
        return inboxMapper.update(null, wrapper);
    }
}
