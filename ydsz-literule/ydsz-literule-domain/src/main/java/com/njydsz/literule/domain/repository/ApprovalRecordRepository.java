package com.njydsz.literule.domain.repository;

import java.util.List;

import com.njydsz.literule.domain.dto.ApprovalRecordDTO;
import com.njydsz.literule.domain.vo.ApprovalRecordVO;

/**
 * 审批记录持久化仓库接口（DDD domain 层）
 *
 * <p>定义审批记录的持久化操作，消费方可提供自定义实现（如数据库存储）以替代默认的内存存储。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface ApprovalRecordRepository {

  /**
   * 保存审批记录
   *
   * @param saveDTO 审批记录保存 DTO
   */
  void save(ApprovalRecordDTO saveDTO);

  /**
   * 根据规则编码查询审批记录
   *
   * @param ruleCode 规则编码
   * @return 审批记录 VO 列表
   */
  List<ApprovalRecordVO> findByRuleCode(String ruleCode);
}
