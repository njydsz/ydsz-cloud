package com.njydsz.nextwiki.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.njydsz.nextwiki.domain.entity.StorageQuota;

/**
 * 存储配额 Mapper
 *
 * <p>对应数据表 <code>ydsz_storage_quota</code>。
 *
 * <p>配额按租户限制总存储容量/单文件大小/文件数量，是多租户资源隔离的关键。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_tenant_id — 租户 ID 唯一索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.nextwiki.domain.entity.StorageQuota 存储配额实体
 * @see com.njydsz.nextwiki.server.service.StorageQuotaService 配额 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface StorageQuotaMapper extends BaseMapper<StorageQuota> {

  /**
   * 按配额维度查询配额记录，用于上传/删除前的容量校验与用量读取。
   *
   * @param scopeType 配额维度：user / tenant / project
   * @param scopeId 维度 ID（对应维度的具体对象 ID）
   * @return 命中的配额实体；不存在则返回 null
   */
  StorageQuota selectByScope(
      @Param("scopeType") String scopeType, @Param("scopeId") String scopeId);

  /**
   * 原子增加已用容量与文件数；SQL 内同时做容量上限校验（quota_used+增量 <= quota_limit 等）， 仅当不超过上限时才更新，返回 0
   * 即表示本次增量将导致超配额（调用方需据此拒绝写入）。
   *
   * @param scopeType 配额维度：user / tenant / project
   * @param scopeId 维度 ID
   * @param bytesDelta 新增字节数（正数）
   * @param fileCountDelta 新增文件数（正数）
   * @return 受影响行数（0 表示超配额未更新）
   */
  @Update(
      "UPDATE nw_storage_quota SET quota_used = quota_used + #{bytesDelta}, "
          + "file_count_used = file_count_used + #{fileCountDelta}, updated_at = NOW() "
          + "WHERE scope_type = #{scopeType} AND scope_id = #{scopeId} "
          + "AND quota_used + #{bytesDelta} <= quota_limit "
          + "AND file_count_used + #{fileCountDelta} <= file_count_limit")
  int addUsage(
      @Param("scopeType") String scopeType,
      @Param("scopeId") String scopeId,
      @Param("bytesDelta") long bytesDelta,
      @Param("fileCountDelta") int fileCountDelta);

  /**
   * 原子扣减已用容量与文件数（文件删除/移出回收站时调用）；使用 GREATEST(..., 0) 防止用量被减成负数。
   *
   * @param scopeType 配额维度：user / tenant / project
   * @param scopeId 维度 ID
   * @param bytesDelta 释放字节数（正数）
   * @param fileCountDelta 释放文件数（正数）
   * @return 受影响行数
   */
  @Update(
      "UPDATE nw_storage_quota SET quota_used = GREATEST(quota_used - #{bytesDelta}, 0), "
          + "file_count_used = GREATEST(file_count_used - #{fileCountDelta}, 0), updated_at = NOW() "
          + "WHERE scope_type = #{scopeType} AND scope_id = #{scopeId}")
  int subtractUsage(
      @Param("scopeType") String scopeType,
      @Param("scopeId") String scopeId,
      @Param("bytesDelta") long bytesDelta,
      @Param("fileCountDelta") int fileCountDelta);

  /** 带 revision 乐观锁的更新（更新失败返回 0） */
  int updateWithRevision(@Param("quota") StorageQuota quota);
}
