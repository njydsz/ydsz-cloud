package com.njydsz.pmis.workflow.server.service;

import java.util.List;

import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.workflow.domain.dto.FlowAttachmentDTO;
import com.njydsz.pmis.workflow.domain.dto.FlowAttachmentPreviewVO;
import com.njydsz.pmis.workflow.domain.entity.FlowAttachmentDO;

/**
 * 自建工作流引擎 - 审批附件 Service
 *
 * <p>P1-6 (GAP-51): 审批时由前端提交的附件统一落库，支持按任务/实例维度查询与删除。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface FlowAttachmentService {

    /**
     * 批量保存审批附件
     *
     * @param instanceId    流程实例 ID
     * @param taskId        任务 ID
     * @param nodeCode      节点编码
     * @param bizType       业务类型: TASK / INSTANCE / COMMENT
     * @param uploaderId    上传人 ID
     * @param uploaderName  上传人姓名
     * @param attachments   附件列表
     * @param tenantId      租户 ID
     * @param traceId       链路追踪 ID
     */
    void saveBatch(String instanceId, String taskId, String nodeCode, String bizType,
                   String uploaderId, String uploaderName,
                   List<FlowAttachmentDTO> attachments, String tenantId, String traceId);

    /**
     * 查询任务关联的附件列表
     *
     * @param taskId 任务 ID
     * @return 附件列表
     */
    List<FlowAttachmentDO> listByTask(String taskId);

    /**
     * 查询实例关联的附件列表
     *
     * @param instanceId 实例 ID
     * @return 附件列表
     */
    List<FlowAttachmentDO> listByInstance(String instanceId);

    /**
     * 删除附件（逻辑删除）
     *
     * @param attachmentId 附件 ID
     * @param operatorId   操作人 ID
     */
    void delete(String attachmentId, String operatorId);

    /**
     * P2-3: 附件在线预览 — 根据文件类型返回预览策略与预览 URL。
     *
     * <p>预览策略：
     * <ul>
     *   <li>IMAGE/PDF/VIDEO/TEXT → previewUrl 即 downloadUrl，前端原生渲染</li>
     *   <li>OFFICE → previewUrl 为外部预览服务 URL（kkFileView/Office Online），
     *       需配置 {@code workflow.attachment.preview-server-url}；未配置时降级为下载</li>
     *   <li>UNSUPPORTED → previewable=false，前端引导下载</li>
     * </ul>
     *
     * @param attachmentId 附件 ID
     * @return 预览 VO（含 previewType / previewUrl / downloadUrl / previewable）
     * @throws SysException 附件不存在时抛 NOT_FOUND
     * @since 1.7.0
     */
    FlowAttachmentPreviewVO previewAttachment(String attachmentId);
}
