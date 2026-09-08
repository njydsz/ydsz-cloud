package com.njydsz.system.infra.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.system.domain.approval.ConfigApproval;
import com.njydsz.system.domain.approval.ConfigApprovalQuery;
import com.njydsz.system.domain.approval.ConfigApprovalRepository;
import com.njydsz.system.infra.mapper.ConfigApprovalMapper;

/**
 * 配置变更审批单 Repository 实现。
 *
 * <p>基于 MyBatis-Plus Mapper 实现持久化操作；排序字段统一命名为 {@code sort}（详见 YDIZ-DB-001）。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Repository
@RequiredArgsConstructor
public class ConfigApprovalRepositoryImpl implements ConfigApprovalRepository {

  private final ConfigApprovalMapper configApprovalMapper;

  @Override
  public boolean save(ConfigApproval record) {
    return configApprovalMapper.insert(record) > 0;
  }

  @Override
  public boolean updateStatus(ConfigApproval record) {
    LambdaUpdateWrapper<ConfigApproval> wrapper = new LambdaUpdateWrapper<>();
    wrapper.eq(ConfigApproval::getId, record.getId())
        .set(ConfigApproval::getStatus, record.getStatus())
        .set(ConfigApproval::getRejectionReason, record.getRejectionReason())
        .set(ConfigApproval::getClosedAt, record.getClosedAt())
        .set(ConfigApproval::getUpdatedAt, record.getUpdatedAt());
    return configApprovalMapper.update(null, wrapper) > 0;
  }

  @Override
  public ConfigApproval findById(String id) {
    return configApprovalMapper.selectById(id);
  }

  @Override
  public List<ConfigApproval> findByQuery(ConfigApprovalQuery query) {
    LambdaQueryWrapper<ConfigApproval> wrapper = buildQueryWrapper(query);
    wrapper.orderByDesc(ConfigApproval::getSubmittedAt);
    int offset = (query.getPageNum() - 1) * query.getPageSize();
    wrapper.last("LIMIT " + offset + ", " + query.getPageSize());
    return configApprovalMapper.selectList(wrapper);
  }

  @Override
  public long countByQuery(ConfigApprovalQuery query) {
    LambdaQueryWrapper<ConfigApproval> wrapper = buildQueryWrapper(query);
    return configApprovalMapper.selectCount(wrapper);
  }

  /**
   * 构建查询条件。
   *
   * @param query 查询参数
   * @return LambdaQueryWrapper
   */
  private LambdaQueryWrapper<ConfigApproval> buildQueryWrapper(ConfigApprovalQuery query) {
    LambdaQueryWrapper<ConfigApproval> wrapper = new LambdaQueryWrapper<>();
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq(ConfigApproval::getStatus, query.getStatus());
    }
    if (query.getResourceType() != null && !query.getResourceType().isBlank()) {
      wrapper.eq(ConfigApproval::getResourceType, query.getResourceType());
    }
    if (query.getSubmitterId() != null && !query.getSubmitterId().isBlank()) {
      wrapper.eq(ConfigApproval::getSubmitterId, query.getSubmitterId());
    }
    return wrapper;
  }
}
