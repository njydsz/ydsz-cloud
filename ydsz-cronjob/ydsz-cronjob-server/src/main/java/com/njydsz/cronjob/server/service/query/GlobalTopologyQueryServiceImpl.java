package com.njydsz.cronjob.server.service.query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.cronjob.domain.repository.JobDagRepository;
import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.domain.service.GlobalTopologyQueryService;
import com.njydsz.cronjob.domain.vo.JobDagVO;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.server.core.dag.DagDefinition;
import com.njydsz.cronjob.server.core.dag.DagDefinitionCodec;
import com.njydsz.cronjob.server.core.dag.DagEdge;

/**
 * 全局拓扑查询 Service 实现（server 层）。
 *
 * <p>组装全局任务拓扑图数据，遵循 DDD 分层。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalTopologyQueryServiceImpl implements GlobalTopologyQueryService {

  private static final int COLLECTION_CAPACITY = 16;
  private static final int MAX_TOPOLOGY_JOBS = 500;
  private static final int JOB_MAP_INITIAL_CAPACITY = 128;
  private static final int TOPOLOGY_LIST_INITIAL_CAPACITY = 64;
  private static final int STATS_MAP_INITIAL_CAPACITY = 16;

  private final JobRepository jobRepository;
  private final JobDagRepository dagRepository;
  private final DagDefinitionCodec dagDefinitionCodec;

  @Override
  public Map<String, Object> getGlobalTopology() {
    PageResponse<List<JobVO>> pageResult =
        jobRepository.page(null, null, null, 1, MAX_TOPOLOGY_JOBS);
    List<JobVO> jobs = pageResult.getData();

    if (jobs.size() >= MAX_TOPOLOGY_JOBS) {
      log.warn(
          "[GlobalTopology] 任务数量达到上限 {}, 可能存在截断。建议拆分 DAG 或增加筛选条件。",
          MAX_TOPOLOGY_JOBS);
    }

    Map<String, JobVO> jobMap = new HashMap<>(JOB_MAP_INITIAL_CAPACITY);
    for (JobVO job : jobs) {
      if (job.getJobKey() != null) {
        jobMap.put(job.getJobKey(), job);
      }
    }

    List<Map<String, Object>> nodes = new ArrayList<>(TOPOLOGY_LIST_INITIAL_CAPACITY);
    for (JobVO job : jobs) {
      Map<String, Object> node = new LinkedHashMap<>(COLLECTION_CAPACITY);
      node.put("id", job.getId());
      node.put("jobKey", job.getJobKey());
      node.put("jobName", job.getJobName());
      node.put("jobGroup", job.getJobGroup());
      node.put("status", job.getStatus());
      node.put("scheduleType", job.getScheduleType());
      node.put("nextFireTime", job.getNextFireTime());
      nodes.add(node);
    }

    List<Map<String, String>> links = new ArrayList<>(TOPOLOGY_LIST_INITIAL_CAPACITY);
    List<JobDagVO> dags = dagRepository.findEnabledDags();
    for (JobDagVO dag : dags) {
      if (dag.getDagDefinition() == null) {
        continue;
      }
      DagDefinition definition = dagDefinitionCodec.fromJson(dag.getDagDefinition());
      if (definition == null || definition.edges() == null) {
        continue;
      }
      for (DagEdge edge : definition.edges()) {
        if (jobMap.containsKey(edge.from()) && jobMap.containsKey(edge.to())) {
          Map<String, String> link = new LinkedHashMap<>(COLLECTION_CAPACITY);
          link.put("source", jobMap.get(edge.from()).getId());
          link.put("target", jobMap.get(edge.to()).getId());
          links.add(link);
        }
      }
    }

    Map<String, Object> topologyData = new LinkedHashMap<>(COLLECTION_CAPACITY);
    topologyData.put("nodes", nodes);
    topologyData.put("links", links);
    topologyData.put("stats", buildStats(jobs));

    return topologyData;
  }

  private Map<String, Object> buildStats(List<JobVO> jobs) {
    Map<String, Object> stats = new LinkedHashMap<>(STATS_MAP_INITIAL_CAPACITY);
    long total = jobs.size();
    long running = jobs.stream().filter(j -> "RUNNING".equals(j.getStatus())).count();
    long paused = jobs.stream().filter(j -> "PAUSED".equals(j.getStatus())).count();
    long failed = jobs.stream().filter(j -> "FAILED".equals(j.getStatus()) || "ERROR".equals(j.getStatus())).count();
    stats.put("total", total);
    stats.put("running", running);
    stats.put("paused", paused);
    stats.put("failed", failed);
    return stats;
  }
}
