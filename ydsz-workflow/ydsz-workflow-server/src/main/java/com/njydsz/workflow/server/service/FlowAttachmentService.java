package com.njydsz.workflow.server.service;

import java.util.List;

import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.domain.dto.FlowAttachmentDTO;
import com.njydsz.workflow.domain.dto.FlowAttachmentPreviewDTO;
import com.njydsz.workflow.domain.vo.FlowAttachmentVO;

/**
 * 流程附件服务 — 审批过程中的文件上传、下载与关联管理
 *
 * <p>审批中心「附件」能力。审批人在办理任务时可上传图片、文档等附件， 附件与流程实例和任务节点关联存储，支持按任务/实例维度查询。
 *
 * <p><b>关联维度：</b>
 *
 * <ul>
 *   <li>{@code TASK} — 任务级附件，仅关联到具体待办任务
 *   <li>{@code INSTANCE} — 实例级附件，关联到整个流程实例
 *   <li>{@code COMMENT} — 评论级附件，关联到审批意见评论
 * </ul>
 *
 * <p><b>存储策略：</b>附件文件存储在 common-file 模块提供的对象存储（MinIO/S3/Local）中，
 * 数据库仅记录元数据（文件名、大小、存储路径、上传人等）。删除为逻辑删除（deleted=1）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface FlowAttachmentService {

  /**
   * 批量保存审批附件
   *
   * @param instanceId 流程实例 ID
   * @param taskId 任务 ID
   * @param nodeCode 节点编码
   * @param bizType 业务类型: TASK / INSTANCE / COMMENT
   * @param uploaderId 上传人 ID
   * @param uploaderName 上传人姓名
   * @param attachments 附件列表
   * @param tenantId 租户 ID
   * @param traceId 链路追踪 ID
   */
  void saveBatch(
      String instanceId,
      String taskId,
      String nodeCode,
      String bizType,
      String uploaderId,
      String uploaderName,
      List<FlowAttachmentDTO> attachments,
      String tenantId,
      String traceId);

  /**
   * 查询任务关联的附件列表
   *
   * @param taskId 任务 ID
   * @return 附件 VO 列表
   */
  List<FlowAttachmentVO> listByTask(String taskId);

  /**
   * 查询实例关联的附件列表
   *
   * @param instanceId 实例 ID
   * @return 附件 VO 列表
   */
  List<FlowAttachmentVO> listByInstance(String instanceId);

  /**
   * 删除附件（逻辑删除）
   *
   * @param attachmentId 附件 ID
   * @param operatorId 操作人 ID
   */
  void delete(String attachmentId, String operatorId);

  /**
   * P2-3: 附件在线预览 — 根据文件类型返回预览策略与预览 URL。
   *
   * <p>预览策略：
   *
   * <ul>
   *   <li>IMAGE/PDF/VIDEO/TEXT → previewUrl 即 downloadUrl，前端原生渲染
   *   <li>OFFICE → previewUrl 为外部预览服务 URL（kkFileView/Office Online）， 需配置 {@code
   *       workflow.attachment.preview-server-url}；未配置时降级为下载
   *   <li>UNSUPPORTED → previewable=false，前端引导下载
   * </ul>
   *
   * @param attachmentId 附件 ID
   * @return 预览 VO（含 previewType / previewUrl / downloadUrl / previewable）
   * @throws SysException 附件不存在时抛 NOT_FOUND
   * @since 26.09.01
   */
  FlowAttachmentPreviewDTO previewAttachment(String attachmentId);
}
