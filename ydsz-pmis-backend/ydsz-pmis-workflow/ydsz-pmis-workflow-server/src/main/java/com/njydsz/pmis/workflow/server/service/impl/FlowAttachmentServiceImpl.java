package com.njydsz.pmis.workflow.server.service.impl.integration;

import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.domain.dto.integration.FlowAttachmentDTO;
import com.njydsz.pmis.workflow.domain.dto.integration.FlowAttachmentPreviewVO;
import com.njydsz.pmis.workflow.domain.entity.integration.FlowAttachmentDO;
import com.njydsz.pmis.workflow.infra.mapper.integration.FlowAttachmentMapper;
import com.njydsz.pmis.workflow.server.service.integration.FlowAttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Set;
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

    /** 审批附件 Mapper，管理 pmis_flow_attachment 表 */
    private final FlowAttachmentMapper attachmentMapper;

    /** P2-3: 外部预览服务地址（kkFileView/Office Online），如 http://preview.example.com/onlinePreview?url={url} */
    @Value("${workflow.attachment.preview-server-url:}")
    private String previewServerUrl;

    /** 支持在线预览的图片扩展名 */
    private static final Set<String> IMAGE_EXTS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico", "tiff");
    /** 支持在线预览的视频扩展名 */
    private static final Set<String> VIDEO_EXTS = Set.of(
            "mp4", "webm", "ogg", "mov", "m4v");
    /** 支持在线预览的纯文本扩展名 */
    private static final Set<String> TEXT_EXTS = Set.of(
            "txt", "log", "md", "csv", "json", "xml", "yml", "yaml",
            "html", "htm", "css", "js", "java", "py", "go", "rs", "sql", "sh", "bat");
    /** Office 文档扩展名（需外部预览服务转换） */
    private static final Set<String> OFFICE_EXTS = Set.of(
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "wps", "et", "dps");

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

    @Override
    public FlowAttachmentPreviewVO previewAttachment(String attachmentId) {
        FlowAttachmentDO attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || (attachment.getDeleted() != null && attachment.getDeleted() == 1)) {
            throw new BizException(StandardResultCode.NOT_FOUND, "error.workflow.msg_c5d6e7f8", attachmentId);
        }

        String ext = attachment.getFileExt() == null ? "" : attachment.getFileExt().toLowerCase();
        String downloadUrl = attachment.getDownloadUrl();
        String previewType = classifyPreviewType(ext);
        String previewUrl = buildPreviewUrl(previewType, downloadUrl, ext);

        FlowAttachmentPreviewVO vo = new FlowAttachmentPreviewVO();
        vo.setAttachmentId(attachment.getId());
        vo.setFileName(attachment.getFileName());
        vo.setFileExt(ext);
        vo.setContentType(attachment.getContentType());
        vo.setPreviewType(previewType);
        vo.setPreviewUrl(previewUrl);
        vo.setDownloadUrl(downloadUrl);
        vo.setPreviewable(!"UNSUPPORTED".equals(previewType) && StringUtils.hasText(previewUrl));
        log.debug("[Flow] 附件预览: attachmentId={} type={} previewable={}",
                attachmentId, previewType, vo.isPreviewable());
        return vo;
    }

    /**
     * 根据扩展名分类预览类型。
     *
     * @param ext 小写扩展名（无点号）
     * @return IMAGE / PDF / VIDEO / TEXT / OFFICE / UNSUPPORTED
     */
    String classifyPreviewType(String ext) {
        if (!StringUtils.hasText(ext)) {
            return "UNSUPPORTED";
        }
        if (IMAGE_EXTS.contains(ext)) {
            return "IMAGE";
        }
        if ("pdf".equals(ext)) {
            return "PDF";
        }
        if (VIDEO_EXTS.contains(ext)) {
            return "VIDEO";
        }
        if (TEXT_EXTS.contains(ext)) {
            return "TEXT";
        }
        if (OFFICE_EXTS.contains(ext)) {
            return "OFFICE";
        }
        return "UNSUPPORTED";
    }

    /**
     * 根据预览类型构建预览 URL。
     *
     * <p>OFFICE 类型需要配置 {@code workflow.attachment.preview-server-url}：
     * <ul>
     *   <li>配置中含 {@code {url}} 占位符 → 替换为 downloadUrl 的 URL 编码</li>
     *   <li>配置中不含占位符 → 直接拼接 downloadUrl</li>
     *   <li>未配置 → 返回 null（previewable=false，前端降级下载）</li>
     * </ul>
     */
    private String buildPreviewUrl(String previewType, String downloadUrl, String ext) {
        if (!StringUtils.hasText(downloadUrl)) {
            return null;
        }
        if ("OFFICE".equals(previewType)) {
            if (!StringUtils.hasText(previewServerUrl)) {
                return null;
            }
            if (previewServerUrl.contains("{url}")) {
                return previewServerUrl.replace("{url}",
                        java.net.URLEncoder.encode(downloadUrl, java.nio.charset.StandardCharsets.UTF_8));
            }
            return previewServerUrl + downloadUrl;
        }
        // IMAGE / PDF / VIDEO / TEXT 直接返回 downloadUrl
        return downloadUrl;
    }
}

