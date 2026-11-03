package com.njydsz.cronjob.infra.repository.export;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.entity.export.ExportTask;
import com.njydsz.cronjob.domain.repository.export.ExportTaskRepository;
import com.njydsz.cronjob.domain.vo.ExportTaskVO;
import com.njydsz.cronjob.infra.mapper.export.ExportTaskMapper;

/**
 * 异步导出任务仓储实现（YDIZ-DDD-006：实现在 infra/repository/）。
 *
 * @author ydsz-team
 * @since 26.10.13
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ExportTaskRepositoryImpl implements ExportTaskRepository {

  private final ExportTaskMapper exportTaskMapper;

  @Override
  public boolean saveOrUpdate(ExportTask task) {
    if (task.getId() == null) {
      return exportTaskMapper.insert(task) > 0;
    }
    return exportTaskMapper.updateById(task) > 0;
  }

  @Override
  public Optional<ExportTask> findById(String id) {
    return Optional.ofNullable(exportTaskMapper.selectById(id));
  }

  @Override
  public Optional<ExportTaskVO> findByIdVO(String id) {
    ExportTask entity = exportTaskMapper.selectById(id);
    return Optional.ofNullable(entity).map(this::entityToVO);
  }

  @Override
  public boolean updateWithVersion(ExportTask task) {
    return exportTaskMapper.updateById(task) > 0;
  }

  @Override
  public List<ExportTask> listByUser(String tenantId, String userId, int limit) {
    LambdaQueryWrapper<ExportTask> wrapper = new LambdaQueryWrapper<ExportTask>()
        .eq(ExportTask::getTenantId, tenantId)
        .eq(ExportTask::getCreatedBy, userId)
        .orderByDesc(ExportTask::getCreatedAt)
        .last("LIMIT " + Math.min(limit, 100));
    return exportTaskMapper.selectList(wrapper);
  }

  @Override
  public List<ExportTaskVO> listByUserVO(String tenantId, String userId, int limit) {
    return listByUser(tenantId, userId, limit).stream()
        .map(this::entityToVO)
        .toList();
  }

  @Override
  public List<ExportTask> findTimeoutProcessing(LocalDateTime threshold) {
    LambdaQueryWrapper<ExportTask> wrapper = new LambdaQueryWrapper<ExportTask>()
        .eq(ExportTask::getStatus, "PROCESSING")
        .lt(ExportTask::getStartedAt, threshold);
    return exportTaskMapper.selectList(wrapper);
  }

  @Override
  public int deleteExpired(LocalDateTime expireBefore) {
    LambdaQueryWrapper<ExportTask> wrapper = new LambdaQueryWrapper<ExportTask>()
        .ne(ExportTask::getStatus, "PROCESSING")
        .lt(ExportTask::getUpdatedAt, expireBefore);
    return exportTaskMapper.delete(wrapper);
  }

  /**
   * Entity → VO 转换（手动映射，收口在 infra 内部，满足 YDIZ-DDD-007）。
   */
  private ExportTaskVO entityToVO(ExportTask entity) {
    ExportTaskVO vo = new ExportTaskVO();
    vo.setId(entity.getId());
    vo.setModule(entity.getModule());
    vo.setTaskName(entity.getTaskName());
    vo.setTaskType(entity.getTaskType());
    vo.setParams(entity.getParams());
    vo.setProgressPercent(entity.getProgressPercent());
    vo.setErrorMessage(entity.getErrorMessage());
    vo.setFileName(entity.getFileName());
    vo.setFileSize(entity.getFileSize());
    vo.setStorageBucket(entity.getStorageBucket());
    vo.setStorageKey(entity.getStorageKey());
    vo.setMimeType(entity.getMimeType());
    vo.setDownloadUrl(entity.getDownloadUrl());
    vo.setExpireAt(entity.getExpireAt());
    vo.setStartedAt(entity.getStartedAt());
    vo.setCompletedAt(entity.getCompletedAt());
    vo.setStatus(entity.getStatus());
    vo.setTenantId(entity.getTenantId());
    vo.setCreatedAt(entity.getCreatedAt());
    vo.setUpdatedAt(entity.getUpdatedAt());
    return vo;
  }
}
