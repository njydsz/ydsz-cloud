package com.njydsz.nextwiki.infra.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import com.njydsz.nextwiki.domain.entity.StorageQuota;
import com.njydsz.nextwiki.infra.repository.StorageQuotaRepository;
import com.njydsz.nextwiki.infra.mapper.StorageQuotaMapper;

/**
 * 存储配额仓储实现
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class StorageQuotaRepositoryImpl implements StorageQuotaRepository {

  private final StorageQuotaMapper storageQuotaMapper;

  /**
   * 新增或更新存储配额：无主键时插入；有主键时按乐观锁更新（revision 缺失退化为普通更新， 并发冲突抛 {@link
   * OptimisticLockingFailureException}），成功后 revision 自增 1。
   *
   * @param quota 配额实体（scopeType/scopeId 定位维度，quotaLimit/fileCountLimit 为上限）
   * @return 已持久化的配额实体
   */
  @Override
  public StorageQuota save(StorageQuota quota) {
    if (quota.getId() == null) {
      storageQuotaMapper.insert(quota);
    } else {
      if (quota.getRevision() == null) {
        // 兜底：未携带 revision 时退化为普通更新，避免业务阻断
        storageQuotaMapper.updateById(quota);
      } else {
        int affected = storageQuotaMapper.updateWithRevision(quota);
        if (affected == 0) {
          throw new OptimisticLockingFailureException(
              "StorageQuota 乐观锁更新失败，id=" + quota.getId() + ", revision=" + quota.getRevision());
        }
        quota.setRevision(quota.getRevision() + 1);
      }
    }
    return quota;
  }

  /**
   * 按主键查询存储配额。
   *
   * @param id 配额主键
   * @return 配额实体；不存在则返回 null
   */
  @Override
  public StorageQuota findById(String id) {
    return storageQuotaMapper.selectById(id);
  }

  /**
   * 按配额维度查询配额记录，用于上传/删除前的容量校验。
   *
   * @param scopeType 配额维度：user / tenant / project
   * @param scopeId 维度 ID（对应维度的具体对象 ID）
   * @return 命中的配额实体；不存在则返回 null
   */
  @Override
  public StorageQuota findByScope(String scopeType, String scopeId) {
    return storageQuotaMapper.selectByScope(scopeType, scopeId);
  }

  /**
   * 在指定维度上增量增加已用容量与文件数（上传成功时调用）。值为正，由 SQL 原子自增避免并发计数偏差。
   *
   * @param scopeType 配额维度：user / tenant / project
   * @param scopeId 维度 ID
   * @param bytesDelta 新增字节数（正数）
   * @param fileCountDelta 新增文件数（正数）
   * @return 受影响行数
   */
  @Override
  public int addUsage(String scopeType, String scopeId, long bytesDelta, int fileCountDelta) {
    return storageQuotaMapper.addUsage(scopeType, scopeId, bytesDelta, fileCountDelta);
  }

  /**
   * 在指定维度上减量扣减已用容量与文件数（文件删除/移出回收站时调用），与 addUsage 成对出现以维持用量平衡。
   *
   * @param scopeType 配额维度：user / tenant / project
   * @param scopeId 维度 ID
   * @param bytesDelta 释放字节数（正数）
   * @param fileCountDelta 释放文件数（正数）
   * @return 受影响行数
   */
  @Override
  public int subtractUsage(String scopeType, String scopeId, long bytesDelta, int fileCountDelta) {
    return storageQuotaMapper.subtractUsage(scopeType, scopeId, bytesDelta, fileCountDelta);
  }
}
