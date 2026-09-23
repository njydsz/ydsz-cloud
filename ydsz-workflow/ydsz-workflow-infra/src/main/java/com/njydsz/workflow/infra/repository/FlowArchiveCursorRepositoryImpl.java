package com.njydsz.workflow.infra.repository;

import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.njydsz.workflow.domain.entity.FlowArchiveCursor;
import com.njydsz.workflow.domain.repository.FlowArchiveCursorRepository;
import com.njydsz.workflow.infra.mapper.FlowArchiveCursorMapper;

/**
 * 流程归档游标仓储实现。
 *
 * <p>基于 FlowArchiveCursorMapper 提供归档断点续传游标的持久化操作。
 * 通过唯一索引 uk_ydsz_flow_archive_cursor_type_tenant 保证 upsert 语义。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class FlowArchiveCursorRepositoryImpl implements FlowArchiveCursorRepository {

  private final FlowArchiveCursorMapper mapper;

  @Override
  public FlowArchiveCursor findByTypeAndTenant(String archiveType, String tenantId) {
    return mapper.selectOne(
        new LambdaQueryWrapper<FlowArchiveCursor>()
            .eq(FlowArchiveCursor::getArchiveType, archiveType)
            .eq(FlowArchiveCursor::getTenantId, tenantId));
  }

  @Override
  public void saveOrUpdate(FlowArchiveCursor cursor) {
    if (cursor == null) {
      return;
    }
    FlowArchiveCursor existing = findByTypeAndTenant(cursor.getArchiveType(), cursor.getTenantId());
    LocalDateTime now = LocalDateTime.now();
    if (existing == null) {
      cursor.setCreatedAt(now);
      cursor.setUpdatedAt(now);
      mapper.insert(cursor);
    } else {
      LambdaUpdateWrapper<FlowArchiveCursor> wrapper = new LambdaUpdateWrapper<>();
      wrapper.eq(FlowArchiveCursor::getId, existing.getId())
          .set(FlowArchiveCursor::getCursorValue, cursor.getCursorValue())
          .set(FlowArchiveCursor::getCursorData, cursor.getCursorData())
          .set(FlowArchiveCursor::getUpdatedAt, now);
      mapper.update(null, wrapper);
    }
  }
}
