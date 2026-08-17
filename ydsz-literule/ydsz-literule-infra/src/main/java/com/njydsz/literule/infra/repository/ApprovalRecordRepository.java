package com.njydsz.literule.infra.repository;

import java.util.List;

import com.njydsz.literule.server.approval.ApprovalRecord;

/**
 * 审批记录持久化仓库接口
 *
 * <p>定义审批记录的持久化操作，消费方可提供自定义实现（如数据库存储）以替代默认的内存存储。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface ApprovalRecordRepository {

  /**
   * 保存审批记录
   *
   * @param record 审批记录
   */
  void save(ApprovalRecord record);

  /**
   * 根据规则编码查询审批记录
   *
   * @param ruleCode 规则编码
   * @return 审批记录列表
   */
  List<ApprovalRecord> findByRuleCode(String ruleCode);

  /**
   * 根据审批人查询审批记录
   *
   * @param approver 审批人
   * @return 审批记录列表
   */
  List<ApprovalRecord> findByApprover(String approver);

  /**
   * 查询所有审批记录
   *
   * @return 全部审批记录
   */
  List<ApprovalRecord> findAll();
}
