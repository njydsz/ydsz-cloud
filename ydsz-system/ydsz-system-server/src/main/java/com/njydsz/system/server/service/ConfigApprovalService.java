package com.njydsz.system.server.service;

import java.util.List;

import com.njydsz.system.domain.approval.ConfigApprovalQuery;

/**
 * 配置变更审批单服务接口。
 *
 * <p>定义审批单的业务操作契约：提交、通过、拒绝、撤回、查询。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
public interface ConfigApprovalService {

  /**
   * 提交配置变更审批。
   *
   * @param dto  提交参数
   * @param userId 当前用户 ID（发起人）
   * @param userName 当前用户姓名
   * @return 审批单 ID
   */
  String submit(String userId, String userName, com.njydsz.system.domain.approval.ConfigApprovalSubmitDTO dto);

  /**
   * 通过审批单。
   *
   * @param id 审批单 ID
   * @param approverId 审批人 ID
   * @param comment 审批意见（可选）
   */
  void approve(String id, String approverId, String comment);

  /**
   * 拒绝审批单。
   *
   * @param id 审批单 ID
   * @param approverId 审批人 ID
   * @param reason 拒绝原因（必填）
   */
  void reject(String id, String approverId, String reason);

  /**
   * 撤回审批单。
   *
   * @param id 审批单 ID
   * @param submitterId 发起人 ID（权限校验）
   */
  void withdraw(String id, String submitterId);

  /**
   * 根据 ID 查询审批单。
   *
   * @param id 审批单 ID
   * @return 审批单实体
   */
  com.njydsz.system.domain.approval.ConfigApproval findById(String id);

  /**
   * 分页查询待当前用户审批的审批单。
   *
   * @param approverId 当前审批人 ID
   * @param query 分页参数
   * @return 审批单列表
   */
  List<com.njydsz.system.domain.approval.ConfigApproval> findPendingByApprover(
      String approverId, ConfigApprovalQuery query);

  /**
   * 分页查询当前用户发起的审批单。
   *
   * @param submitterId 发起人 ID
   * @param query 分页参数
   * @return 审批单列表
   */
  List<com.njydsz.system.domain.approval.ConfigApproval> findBySubmitter(
      String submitterId, ConfigApprovalQuery query);

  /**
   * 分页查询所有审批单（管理视角）。
   *
   * @param query 分页 + 筛选参数
   * @return 审批单列表
   */
  List<com.njydsz.system.domain.approval.ConfigApproval> findAll(ConfigApprovalQuery query);
}
