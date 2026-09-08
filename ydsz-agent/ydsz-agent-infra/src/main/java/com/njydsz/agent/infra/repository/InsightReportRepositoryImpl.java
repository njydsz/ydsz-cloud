package com.njydsz.agent.infra.repository;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.njydsz.agent.domain.insight.InsightReport;
import com.njydsz.agent.domain.insight.InsightReportRepository;
import com.njydsz.agent.infra.mapper.InsightReportMapper;

/**
 * 洞察报告仓储实现。
 *
 * <p>基于 MyBatis-Plus 实现 {@link InsightReportRepository} 接口，
 * 完成 {@link InsightReport} 实体的持久化操作（查找、插入、更新、删除）。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li>通过 reportId 查找时，优先精确匹配业务 ID</li>
 *   <li>findByUserId 使用 LambdaQueryWrapper 按创建时间倒序排列，并限制返回条数</li>
 *   <li>save 方法根据 id 是否存在区分插入/更新语义</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.07
 */
@Repository
@RequiredArgsConstructor
public class InsightReportRepositoryImpl implements InsightReportRepository {

  /** 默认返回条数上限 */
  private static final int DEFAULT_LIMIT = 20;

  /** 最大返回条数上限（防止滥用） */
  private static final int MAX_LIMIT = 100;

  private final InsightReportMapper insightReportMapper;

  /**
   * {@inheritDoc}
   *
   * <p>当 id 为 null 时执行插入（MyBatis-Plus fill 策略触发 createdAt/updatedAt）；
   * 当 id 不为 null 时执行更新（updatedAt 自动填充）。
   */
  @Override
  public void save(InsightReport report) {
    if (report.getId() == null) {
      insightReportMapper.insert(report);
    } else {
      insightReportMapper.updateById(report);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>通过业务 ID 字段 reportId 查找（非主键 id），使用 LambdaQueryWrapper 精确匹配。
   */
  @Override
  public Optional<InsightReport> findById(String reportId) {
    LambdaQueryWrapper<InsightReport> wrapper =
        new LambdaQueryWrapper<InsightReport>()
            .eq(InsightReport::getReportId, reportId);
    return Optional.ofNullable(insightReportMapper.selectOne(wrapper));
  }

  /**
   * {@inheritDoc}
   *
   * <p>按创建时间倒序：最近生成的报告排在前面；limit 限制返回条数（不超过 MAX_LIMIT）。
   */
  @Override
  public List<InsightReport> findByUserId(String userId, int limit) {
    int safeLimit = Math.min(Math.max(limit, 0), MAX_LIMIT);
    if (safeLimit == 0) {
      safeLimit = DEFAULT_LIMIT;
    }
    LambdaQueryWrapper<InsightReport> wrapper =
        new LambdaQueryWrapper<InsightReport>()
            .eq(InsightReport::getUserId, userId)
            .orderByDesc(InsightReport::getCreatedAt)
            .last("LIMIT " + safeLimit);
    return insightReportMapper.selectList(wrapper);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteById(String reportId) {
    LambdaQueryWrapper<InsightReport> wrapper =
        new LambdaQueryWrapper<InsightReport>()
            .eq(InsightReport::getReportId, reportId);
    insightReportMapper.delete(wrapper);
  }
}
