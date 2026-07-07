package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.workflow.dto.FlowAttachmentDTO;
import com.njydsz.pmis.workflow.entity.FlowAttachmentDO;
import com.njydsz.pmis.workflow.mapper.FlowAttachmentMapper;
import com.njydsz.pmis.workflow.service.FlowAttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 自建工作流引擎 - 审批附件服务实现
 *
 * <p>P1-6 (GAP-51)
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowAttachmentServiceImpl implements FlowAttachmentService {

    private final FlowAttachmentMapper attachmentMapper;

    @Override
    public void saveBatch(String instanceId, String taskId, String nodeCode, String bizType,
                          String uploaderId, String uploaderName,
                          List<FlowAttachmentDTO> attachments, String tenantId, String traceId) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        List<FlowAttachmentDO> entities = new ArrayList<>(attachments.size());
        for (FlowAttachmentDTO dto : attachments) {
            if (dto == null || dto.getStorageKey() == null || dto.getStorageKey().isBlank()) {
                continue;
            }
            FlowAttachmentDO entity = new FlowAttachmentDO();
            entity.setInstanceId(instanceId);
            entity.setTaskId(taskId);
            entity.setNodeCode(nodeCode);
            entity.setBizType(bizType == null ? "TASK" : bizType);
            entity.setFileName(dto.getFileName());
            String ext = dto.getFileExt();
            if ((ext == null || ext.isBlank()) && dto.getFileName() != null) {
                int idx = dto.getFileName().lastIndexOf('.');
                ext = idx > 0 ? dto.getFileName().substring(idx + 1) : null;
            }
            entity.setFileExt(ext);
            entity.setFileSize(dto.getFileSize() == null ? 0L : dto.getFileSize());
            entity.setContentType(dto.getContentType());
            entity.setStorageKey(dto.getStorageKey());
            entity.setStorageType(dto.getStorageType() == null ? "OSS" : dto.getStorageType());
            entity.setDownloadUrl(dto.getDownloadUrl());
            entity.setMd5(dto.getMd5());
            entity.setUploaderId(uploaderId);
            entity.setUploaderName(uploaderName);
            entity.setTenantId(tenantId == null ? "1" : tenantId);
            entity.setProviderTraceId(traceId);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            entities.add(entity);
        }
        if (!entities.isEmpty()) {
            attachmentMapper.insert(entities);
            log.info("[Flow] 审批附件落库: instanceId={} taskId={} count={}",
                    instanceId, taskId, entities.size());
        }
    }

    @Override
    public List<FlowAttachmentDO> listByTask(String taskId) {
        return attachmentMapper.selectByTask(taskId);
    }

    @Override
    public List<FlowAttachmentDO> listByInstance(String instanceId) {
        return attachmentMapper.selectByInstance(instanceId);
    }

    @Override
    public void delete(String attachmentId, String operatorId) {
        FlowAttachmentDO entity = attachmentMapper.selectById(attachmentId);
        if (entity != null && (entity.getDeleted() == null || entity.getDeleted() == 0)) {
            attachmentMapper.deleteById(attachmentId);
            log.info("[Flow] 附件删除: attachmentId={} operator={}", attachmentId, operatorId);
        }
    }
}
