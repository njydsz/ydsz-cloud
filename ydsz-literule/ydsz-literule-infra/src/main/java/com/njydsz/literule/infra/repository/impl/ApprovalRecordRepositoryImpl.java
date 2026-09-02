package com.njydsz.literule.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.literule.domain.dto.ApprovalRecordDTO;
import com.njydsz.literule.domain.repository.ApprovalRecordRepository;
import com.njydsz.literule.domain.vo.ApprovalRecordVO;

/**
 * 审批记录仓储实现（Infra 层，基于 ConcurrentHashMap 的内存实现）。
 *
 * <p>作为 {@link com.njydsz.literule.server.approval.RuleApprovalService} 的持久化备份存储。
 * Service 层维护主内存缓存（{@code recordRecord}），本仓库作为二级存储提供崩溃恢复能力。
 *
 * <p><b>局限性：</b>
 *
 * <ul>
 *   <li>进程重启后数据丢失，生产环境应替换为数据库实现
 *   <li>不支持集群多节点共享
 * </ul>
 *
 * <p>如需数据库实现，可新建 {@code ApprovalRecordMapper} 与 {@code ApprovalRecordDO}，
 * 并通过 {@code @Primary} 注解替换本 Bean。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Repository
public class ApprovalRecordRepositoryImpl implements ApprovalRecordRepository {

  /** 主存储：ruleCode → 审批记录 DTO */
  private final Map<String, ApprovalRecordDTO> store = new ConcurrentHashMap<>();

  @Override
  public void save(ApprovalRecordDTO saveDTO) {
    if (saveDTO == null || saveDTO.getRuleCode() == null) {
      log.warn("[ApprovalRecord] 保存参数忽略：saveDTO 或 ruleCode 为空");
      return;
    }
    // 写入前设置更新时间
    saveDTO.setUpdatedAt(LocalDateTime.now());
    // 设置创建时间（仅首次）
    if (saveDTO.getCreatedAt() == null) {
      saveDTO.setCreatedAt(LocalDateTime.now());
    }
    store.put(saveDTO.getRuleCode(), saveDTO);
    log.debug("[ApprovalRecord] 审批记录已保存：ruleCode={}, status={}",
        saveDTO.getRuleCode(), saveDTO.getCurrentStatus());
  }

  @Override
  public List<ApprovalRecordVO> findByRuleCode(String ruleCode) {
    if (ruleCode == null) {
      return List.of();
    }
    ApprovalRecordDTO dto = store.get(ruleCode);
    if (dto == null) {
      return List.of();
    }
    return List.of(toVO(dto));
  }

  /**
   * DTO → VO 转换
   *
   * @param dto 审批记录 DTO
   * @return 审批记录 VO
   */
  private ApprovalRecordVO toVO(ApprovalRecordDTO dto) {
    ApprovalRecordVO vo = new ApprovalRecordVO();
    vo.setRecordId(dto.getRecordId());
    vo.setRuleCode(dto.getRuleCode());
    vo.setFlowCode(dto.getFlowCode());
    vo.setCurrentLevel(dto.getCurrentLevel());
    vo.setCurrentStatus(dto.getCurrentStatus());
    vo.setCreatedAt(dto.getCreatedAt());
    vo.setUpdatedAt(dto.getUpdatedAt());
    return vo;
  }
}



