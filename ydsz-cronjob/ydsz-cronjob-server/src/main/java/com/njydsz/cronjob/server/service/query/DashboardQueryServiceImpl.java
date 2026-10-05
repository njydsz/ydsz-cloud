package com.njydsz.cronjob.server.service.query;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.domain.service.DashboardQueryService;

/**
 * Dashboard 查询 Service 实现（server 层）。
 *
 * <p>聚合任务状态分布和分组统计数据，通过 JobRepository 查询。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardQueryServiceImpl implements DashboardQueryService {

  private static final int COLLECTION_CAPACITY = 16;

  private final JobRepository jobRepository;

  @Override
  public Map<String, Object> getOverview() {
    Map<String, Object> data = new LinkedHashMap<>(COLLECTION_CAPACITY);

    // 1. 任务状态分布
    Map<String, Long> statusDistribution = new LinkedHashMap<>(COLLECTION_CAPACITY);
    statusDistribution.put("NORMAL", jobRepository.countByStatus("NORMAL"));
    statusDistribution.put("PAUSED", jobRepository.countByStatus("PAUSED"));
    statusDistribution.put("AUTO_PAUSED", jobRepository.countByStatus("AUTO_PAUSED"));
    statusDistribution.put("ERROR", jobRepository.countByStatus("ERROR"));
    data.put("statusDistribution", statusDistribution);

    // 2. 分组任务数量统计
    List<String> groups = jobRepository.listDistinctGroups();
    Map<String, Long> groupStats = new LinkedHashMap<>(COLLECTION_CAPACITY);
    for (String group : groups) {
      groupStats.put(group, jobRepository.countByGroup(group));
    }
    data.put("groupStats", groupStats);

    // 3. 汇总指标
    Map<String, Object> summary = new LinkedHashMap<>(COLLECTION_CAPACITY);
    summary.put("total", jobRepository.countAll());
    summary.put("normalCount", jobRepository.countByStatus("NORMAL"));
    summary.put("pausedCount", jobRepository.countByStatus("PAUSED"));
    summary.put("errorCount", jobRepository.countByStatus("ERROR"));
    data.put("summary", summary);

    return data;
  }
}
