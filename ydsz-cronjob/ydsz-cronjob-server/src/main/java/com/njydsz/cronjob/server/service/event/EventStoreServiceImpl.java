package com.njydsz.cronjob.server.service.event;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.cronjob.domain.event.JobEvent;
import com.njydsz.cronjob.domain.repository.event.EventStoreRepository;

/**
 * 事件存储服务实现（P3-1 Event Sourcing）。
 *
 * <p>实现 {@link EventStoreService} 接口，提供事件追加和查询能力。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventStoreServiceImpl implements EventStoreService {

  private final EventStoreRepository eventStoreRepository;
  private final SnowflakeIdGenerator idGenerator;

  @Override
  public void recordJobCreated(String jobId, String payload, String operator) {
    appendEvent(jobId, JobEvent.Type.CREATED.name(), payload, operator);
  }

  @Override
  public void recordJobUpdated(String jobId, String payload, String operator) {
    appendEvent(jobId, JobEvent.Type.UPDATED.name(), payload, operator);
  }

  @Override
  public void recordJobStatusChanged(
      String jobId, String fromStatus, String toStatus, String operator) {
    String payload = String.format("{\"fromStatus\":\"%s\",\"toStatus\":\"%s\"}", fromStatus, toStatus);
    appendEvent(jobId, JobEvent.Type.STATUS_CHANGED.name(), payload, operator);
  }

  @Override
  public void recordJobTriggered(String jobId, String triggerType, String operator) {
    String payload = String.format("{\"triggerType\":\"%s\"}", triggerType);
    appendEvent(jobId, JobEvent.Type.TRIGGERED.name(), payload, operator);
  }

  @Override
  public void recordJobDeleted(String jobId, String operator) {
    appendEvent(jobId, JobEvent.Type.DELETED.name(), "{}", operator);
  }

  @Override
  public void recordJobMigrated(
      String jobId, String fromCluster, String toCluster, String operator) {
    String payload =
        String.format("{\"fromCluster\":\"%s\",\"toCluster\":\"%s\"}", fromCluster, toCluster);
    appendEvent(jobId, JobEvent.Type.MIGRATED.name(), payload, operator);
  }

  @Override
  public List<JobEvent> getJobEventStream(String jobId) {
    return eventStoreRepository.findByAggregateId(jobId);
  }

  @Override
  public PageResponse<List<JobEvent>> pageByType(String eventType, int pageNum, int size) {
    int offset = (Math.max(pageNum, 1) - 1) * size;
    long total = eventStoreRepository.countByType(eventType);
    if (total == 0) {
      return PageResponse.empty((long) pageNum, (long) size);
    }
    List<JobEvent> records = eventStoreRepository.findByType(eventType, size, offset);
    return PageResponse.success(total, (long) pageNum, (long) size, records);
  }

  /**
   * 追加事件到事件存储。
   *
   * @param jobId 任务 ID
   * @param eventType 事件类型
   * @param payload 事件负载 JSON
   * @param operator 操作人
   */
  private void appendEvent(String jobId, String eventType, String payload, String operator) {
    try {
      JobEvent event =
          new JobEvent(
              String.valueOf(idGenerator.nextId()),
              jobId,
              eventType,
              payload,
              operator,
              LocalDateTime.now());
      eventStoreRepository.append(event);
    } catch (Exception e) {
      // 事件存储写入失败不应阻塞主业务流程，仅记录警告
      log.warn(
          "[EventStore] 事件写入失败: jobId={} type={} reason={}", jobId, eventType, e.getMessage());
    }
  }
}
