package com.njydsz.pmis.workflow.service.impl.delegate;

import com.njydsz.pmis.workflow.dto.delegate.FlowDelegateMessageDTO;
import com.njydsz.pmis.workflow.entity.delegate.FlowDelegateMessageDO;
import com.njydsz.pmis.workflow.mapper.delegate.FlowDelegateMessageMapper;
import com.njydsz.pmis.workflow.service.delegate.FlowDelegateMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 自建工作流引擎 - 委派沟通记录服务实现
 *
 * <p>P2-1 (GAP-08)
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowDelegateMessageServiceImpl implements FlowDelegateMessageService {

    private final FlowDelegateMessageMapper messageMapper;

    @Override
    public FlowDelegateMessageDO send(FlowDelegateMessageDTO dto, String senderId, String senderName,
                                      String senderRole, String tenantId, String traceId) {
        if (dto == null || dto.getTaskId() == null || dto.getTaskId().isBlank()) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        FlowDelegateMessageDO entity = new FlowDelegateMessageDO();
        entity.setTaskId(dto.getTaskId());
        entity.setInstanceId(dto.getInstanceId());
        entity.setNodeCode(dto.getNodeCode());
        entity.setSenderId(senderId);
        entity.setSenderName(senderName);
        entity.setSenderRole(senderRole == null ? "OWNER" : senderRole);
        entity.setContent(dto.getContent());
        entity.setAttachmentKey(dto.getAttachmentKey());
        entity.setReadFlag(0);
        entity.setTenantId(tenantId == null ? "1" : tenantId);
        entity.setProviderTraceId(traceId);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        messageMapper.insert(entity);
        log.info("[FlowDelegateMsg] 委派沟通留言: taskId={} sender={}({})",
                dto.getTaskId(), senderId, entity.getSenderRole());
        return entity;
    }

    @Override
    public List<FlowDelegateMessageDO> listByTask(String taskId) {
        return messageMapper.selectByTask(taskId);
    }

    @Override
    public void markRead(String taskId, String viewerRole) {
        if (taskId == null) {
            return;
        }
        messageMapper.markRead(taskId, viewerRole);
    }
}
