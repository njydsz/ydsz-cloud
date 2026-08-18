package com.njydsz.literule.infra.repository.impl;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.literule.domain.dto.post.ApprovalRecordSaveDTO;
import com.njydsz.literule.domain.repository.ApprovalRecordRepository;
import com.njydsz.literule.domain.vo.ApprovalRecordVO;
import com.njydsz.literule.infra.converter.LiteruleConverter;

/**
 * 审批记录仓储实现（Infra 层默认内存实现）。
 *
 * <p>提供基于内存的审批记录存储实现，适用于轻量级场景。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class ApprovalRecordRepositoryImpl implements ApprovalRecordRepository {

  // 注意：此处使用具体字段而非注入 Mapper，因为是内存实现
  // 如果需要数据库实现，注入对应的 Mapper 和 Converter

  @Override
  public void save(ApprovalRecordSaveDTO saveDTO) {
    // TODO: 实现保存逻辑（当前为内存实现占位）
    throw new UnsupportedOperationException("ApprovalRecordRepositoryImpl.save() 尚未实现");
  }

  @Override
  public List<ApprovalRecordVO> findByRuleCode(String ruleCode) {
    // TODO: 实现查询逻辑
    throw new UnsupportedOperationException("ApprovalRecordRepositoryImpl.findByRuleCode() 尚未实现");
  }

  @Override
  public List<ApprovalRecordVO> findByApprover(String approver) {
    // TODO: 实现查询逻辑
    throw new UnsupportedOperationException("ApprovalRecordRepositoryImpl.findByApprover() 尚未实现");
  }

  @Override
  public List<ApprovalRecordVO> findAll() {
    // TODO: 实现查询逻辑
    throw new UnsupportedOperationException("ApprovalRecordRepositoryImpl.findAll() 尚未实现");
  }
}
