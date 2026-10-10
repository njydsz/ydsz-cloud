package com.njydsz.system.server.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.locales.util.I18n;
import com.njydsz.system.domain.entity.ConfigApproval;
import com.njydsz.system.domain.approval.ConfigApprovalQuery;
import com.njydsz.system.domain.approval.ConfigApprovalRepository;
import com.njydsz.system.domain.approval.ConfigApprovalSubmitDTO;
import com.njydsz.system.domain.approval.ConfigApprovalVO;
import com.njydsz.system.server.exception.ConfigApprovalException;
import com.njydsz.system.server.service.ConfigApprovalService;

/**
 * 配置变更审批单服务实现。
 *
 * <p>实现审批单的完整业务逻辑：提交 → 审批（通过/拒绝/撤回）。
 *
 * <p><b>主键生成：</b>审批单 ID 由 {@code MpBaseEntity} 的 {@code @TableId(ASSIGN_ID)} 自动分配雪花 ID。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigApprovalServiceImpl implements ConfigApprovalService {

  private final ConfigApprovalRepository approvalRepository;

  @Override
  @Transactional(rollbackFor = Exception.class)
  public String submit(String userId, String userName, ConfigApprovalSubmitDTO dto) {
    ConfigApproval record = new ConfigApproval();
    record.setResourceType(dto.getResourceType());
    record.setResourceKey(dto.getResourceKey());
    record.setResourceGroup(dto.getResourceGroup());
    record.setChangeType(dto.getChangeType());
    record.setBeforeJson(dto.getBeforeJson());
    record.setAfterJson(dto.getAfterJson());
    record.setStatus("PENDING");
    record.setSubmitterId(userId);
    record.setReason(dto.getReason());
    record.setSubmittedAt(LocalDateTime.now());

    boolean success = approvalRepository.save(record);
    if (!success) {
      throw new ConfigApprovalException(I18n.message("system.approval.submit_failed"));
    }
    log.info(I18n.message("system.approval.submit.log", new Object[]{userName, dto.getResourceKey()}));
    return record.getId();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void approve(String id, String approverId, String comment) {
    ConfigApproval record = getAndCheckPending(id);
    record.setStatus("APPROVED");
    record.setClosedAt(LocalDateTime.now());
    boolean success = approvalRepository.updateStatus(record);
    if (!success) {
      throw new ConfigApprovalException(I18n.message("system.approval.approve_failed"));
    }
    log.info(I18n.message("system.approval.approve.log", new Object[]{id, approverId}));
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void reject(String id, String approverId, String reason) {
    if (reason == null || reason.isBlank()) {
      throw new ConfigApprovalException(I18n.message("system.approval.reject_reason_required"));
    }
    ConfigApproval record = getAndCheckPending(id);
    record.setStatus("REJECTED");
    record.setRejectionReason(reason);
    record.setClosedAt(LocalDateTime.now());
    boolean success = approvalRepository.updateStatus(record);
    if (!success) {
      throw new ConfigApprovalException(I18n.message("system.approval.reject_failed"));
    }
    log.info(I18n.message("system.approval.reject.log", new Object[]{id, approverId, reason}));
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void withdraw(String id, String submitterId) {
    ConfigApproval record = getAndCheckPending(id);
    if (!submitterId.equals(record.getSubmitterId())) {
      throw new ConfigApprovalException(I18n.message("system.approval.only_initiator_can_revoke"));
    }
    record.setStatus("WITHDRAWN");
    record.setClosedAt(LocalDateTime.now());
    boolean success = approvalRepository.updateStatus(record);
    if (!success) {
      throw new ConfigApprovalException(I18n.message("system.approval.revoke_op_failed"));
    }
    log.info(I18n.message("system.approval.withdraw.log", new Object[]{id, submitterId}));
  }

  @Override
  public ConfigApprovalVO findById(String id) {
    ConfigApprovalVO vo = approvalRepository.findById(id);
    if (vo == null) {
      throw new ConfigApprovalException(I18n.message("system.approval.not_found", new Object[]{id}));
    }
    return vo;
  }

  @Override
  public List<ConfigApprovalVO> findPendingByApprover(String approverId, ConfigApprovalQuery query) {
    query.setStatus("PENDING");
    // TODO: 实际应按 approverId 过滤（当前简化实现为所有 PENDING）
    return approvalRepository.findByQuery(query);
  }

  @Override
  public List<ConfigApprovalVO> findBySubmitter(String submitterId, ConfigApprovalQuery query) {
    query.setSubmitterId(submitterId);
    return approvalRepository.findByQuery(query);
  }

  @Override
  public List<ConfigApprovalVO> findAll(ConfigApprovalQuery query) {
    return approvalRepository.findByQuery(query);
  }

  /**
   * 获取并校验审批单是否为 PENDING 状态，返回 Entity 用于状态更新。
   *
   * @param id 审批单 ID
   * @return 审批单实体（仅含 id 和 status，供 updateStatus 使用）
   */
  private ConfigApproval getAndCheckPending(String id) {
    ConfigApprovalVO vo = approvalRepository.findById(id);
    if (vo == null) {
      throw new ConfigApprovalException(I18n.message("system.approval.not_found", new Object[]{id}));
    }
    if (!"PENDING".equals(vo.getStatus())) {
      throw new ConfigApprovalException(I18n.message("system.approval.status_invalid", new Object[]{vo.getStatus()}));
    }
    // 将 VO 转回 Entity 以便 updateStatus 使用
    ConfigApproval record = new ConfigApproval();
    record.setId(vo.getId());
    record.setStatus(vo.getStatus());
    record.setSubmitterId(vo.getSubmitterId());
    return record;
  }
}
