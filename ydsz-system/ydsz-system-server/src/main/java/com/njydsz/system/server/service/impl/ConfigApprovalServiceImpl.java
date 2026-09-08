package com.njydsz.system.server.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.system.domain.approval.ConfigApproval;
import com.njydsz.system.domain.approval.ConfigApprovalQuery;
import com.njydsz.system.domain.approval.ConfigApprovalRepository;
import com.njydsz.system.domain.approval.ConfigApprovalSubmitDTO;
import com.njydsz.system.server.exception.ConfigApprovalException;
import com.njydsz.system.server.service.ConfigApprovalService;

/**
 * 配置变更审批单服务实现。
 *
 * <p>实现审批单的完整业务逻辑：提交 → 审批（通过/拒绝/撤回）。
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
    record.setId(UUID.randomUUID().toString());
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
    record.setCreatedAt(LocalDateTime.now());
    record.setUpdatedAt(LocalDateTime.now());

    boolean success = approvalRepository.save(record);
    if (!success) {
      throw new ConfigApprovalException("提交审批单失败");
    }
    log.info("用户 {} 提交配置变更审批，resourceKey={}", userName, dto.getResourceKey());
    return record.getId();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void approve(String id, String approverId, String comment) {
    ConfigApproval record = getAndCheckPending(id);
    record.setStatus("APPROVED");
    record.setClosedAt(LocalDateTime.now());
    record.setUpdatedAt(LocalDateTime.now());
    boolean success = approvalRepository.updateStatus(record);
    if (!success) {
      throw new ConfigApprovalException("审批通过操作失败");
    }
    log.info("审批单 {} 已通过，审批人={}", id, approverId);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void reject(String id, String approverId, String reason) {
    if (reason == null || reason.isBlank()) {
      throw new ConfigApprovalException("拒绝原因不能为空");
    }
    ConfigApproval record = getAndCheckPending(id);
    record.setStatus("REJECTED");
    record.setRejectionReason(reason);
    record.setClosedAt(LocalDateTime.now());
    record.setUpdatedAt(LocalDateTime.now());
    boolean success = approvalRepository.updateStatus(record);
    if (!success) {
      throw new ConfigApprovalException("审批拒绝操作失败");
    }
    log.info("审批单 {} 已拒绝，审批人={}，原因={}", id, approverId, reason);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void withdraw(String id, String submitterId) {
    ConfigApproval record = getAndCheckPending(id);
    if (!submitterId.equals(record.getSubmitterId())) {
      throw new ConfigApprovalException("仅发起人可撤回审批单");
    }
    record.setStatus("WITHDRAWN");
    record.setClosedAt(LocalDateTime.now());
    record.setUpdatedAt(LocalDateTime.now());
    boolean success = approvalRepository.updateStatus(record);
    if (!success) {
      throw new ConfigApprovalException("审批撤回操作失败");
    }
    log.info("审批单 {} 已撤回，发起人={}", id, submitterId);
  }

  @Override
  public ConfigApproval findById(String id) {
    ConfigApproval record = approvalRepository.findById(id);
    if (record == null) {
      throw new ConfigApprovalException("审批单不存在: " + id);
    }
    return record;
  }

  @Override
  public List<ConfigApproval> findPendingByApprover(String approverId, ConfigApprovalQuery query) {
    query.setStatus("PENDING");
    // TODO: 实际应按 approverId 过滤（当前简化实现为所有 PENDING）
    return approvalRepository.findByQuery(query);
  }

  @Override
  public List<ConfigApproval> findBySubmitter(String submitterId, ConfigApprovalQuery query) {
    query.setSubmitterId(submitterId);
    return approvalRepository.findByQuery(query);
  }

  @Override
  public List<ConfigApproval> findAll(ConfigApprovalQuery query) {
    return approvalRepository.findByQuery(query);
  }

  /**
   * 获取并校验审批单是否为 PENDING 状态。
   *
   * @param id 审批单 ID
   * @return 审批单实体
   */
  private ConfigApproval getAndCheckPending(String id) {
    ConfigApproval record = approvalRepository.findById(id);
    if (record == null) {
      throw new ConfigApprovalException("审批单不存在: " + id);
    }
    if (!"PENDING".equals(record.getStatus())) {
      throw new ConfigApprovalException("审批单状态不允许此操作，当前状态: " + record.getStatus());
    }
    return record;
  }
}
