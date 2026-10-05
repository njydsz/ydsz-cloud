package com.njydsz.cronjob.server.service.query;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.cronjob.domain.repository.JobDagInstanceRepository;
import com.njydsz.cronjob.domain.repository.JobDagNodeInstanceRepository;
import com.njydsz.cronjob.domain.repository.JobDagRepository;
import com.njydsz.cronjob.domain.repository.JobLogRepository;
import com.njydsz.cronjob.domain.service.DagTopologyQueryService;
import com.njydsz.cronjob.domain.vo.JobDagInstanceVO;
import com.njydsz.cronjob.domain.vo.JobDagNodeInstanceVO;
import com.njydsz.cronjob.domain.vo.JobDagVO;
import com.njydsz.cronjob.domain.vo.JobLogVO;
import com.njydsz.cronjob.server.core.dag.DagCytoscapeHelper;
import com.njydsz.cronjob.server.core.dag.DagDefinition;
import com.njydsz.cronjob.server.core.dag.DagDefinitionCodec;

/**
 * DAG 拓扑查询 Service 实现（server 层）。
 *
 * <p>组装 DAG 拓扑图数据，遵循 DDD 分层。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DagTopologyQueryServiceImpl implements DagTopologyQueryService {

  private static final int COLLECTION_CAPACITY = 16;
  private static final int DEFAULT_HISTORY_LIMIT = 20;

  private final JobDagInstanceRepository dagInstanceRepository;
  private final JobDagNodeInstanceRepository dagNodeInstanceRepository;
  private final JobDagRepository dagRepository;
  private final JobLogRepository jobLogRepository;
  private final DagDefinitionCodec dagDefinitionCodec;

  @Override
  public Map<String, Object> getDagInstanceTopology(String dagInstanceId) {
    Optional<JobDagInstanceVO> instanceOpt = dagInstanceRepository.findById(dagInstanceId);
    if (instanceOpt.isEmpty()) {
      log.debug("[TaskTopology] DAG 实例不存在: dagInstanceId={}", dagInstanceId);
      return null;
    }
    JobDagInstanceVO instance = instanceOpt.get();
    Optional<JobDagVO> dagOpt = dagRepository.findById(instance.getDagId());
    DagDefinition definition =
        dagOpt.isPresent() && dagOpt.get().getDagDefinition() != null
            ? dagDefinitionCodec.fromJson(dagOpt.get().getDagDefinition())
            : DagDefinition.empty();
    List<JobDagNodeInstanceVO> nodeInstances =
        dagNodeInstanceRepository.findByDagInstanceId(dagInstanceId);
    Map<String, Object> topology = new LinkedHashMap<>(COLLECTION_CAPACITY);
    topology.put("dagDefinition", definition);
    topology.put("dagInstance", instance);
    topology.put("nodeInstances", nodeInstances);
    return topology;
  }

  @Override
  public Map<String, Object> getDagInstanceCytoscape(String dagInstanceId) {
    Optional<JobDagInstanceVO> instanceOpt = dagInstanceRepository.findById(dagInstanceId);
    if (instanceOpt.isEmpty()) {
      log.debug("[TaskTopology] DAG 实例不存在: dagInstanceId={}", dagInstanceId);
      return null;
    }
    JobDagInstanceVO instance = instanceOpt.get();
    Optional<JobDagVO> dagOpt = dagRepository.findById(instance.getDagId());
    DagDefinition definition =
        dagOpt.isPresent() && dagOpt.get().getDagDefinition() != null
            ? dagDefinitionCodec.fromJson(dagOpt.get().getDagDefinition())
            : DagDefinition.empty();
    List<JobDagNodeInstanceVO> nodeInstances =
        dagNodeInstanceRepository.findByDagInstanceId(dagInstanceId);
    Map<String, String> statusMap = new HashMap<>(COLLECTION_CAPACITY);
    Map<String, Long> durationMap = new HashMap<>(COLLECTION_CAPACITY);
    for (JobDagNodeInstanceVO ni : nodeInstances) {
      if (ni.getJobKey() != null && ni.getNodeStatus() != null) {
        statusMap.put(ni.getJobKey(), ni.getNodeStatus());
      }
      if (ni.getDurationMs() != null && ni.getDurationMs() > 0) {
        durationMap.put(ni.getJobKey(), ni.getDurationMs());
      }
    }
    return DagCytoscapeHelper.toCytoscapeFormat(definition, statusMap, durationMap);
  }

  @Override
  public List<JobLogVO> getJobExecutionHistory(String jobKey, int limit) {
    return jobLogRepository.findByJobKey(jobKey, limit);
  }
}
