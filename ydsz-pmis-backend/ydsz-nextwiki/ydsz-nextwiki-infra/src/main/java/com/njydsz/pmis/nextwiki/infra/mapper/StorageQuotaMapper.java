package com.njydsz.nextwiki.infra.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.nextwiki.domain.entity.StorageQuota;

/**
 * 存储配额 MyBatis Mapper
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Mapper
public interface StorageQuotaMapper extends BaseMapper<StorageQuota> {

    StorageQuota selectByScope(@Param("scopeType") String scopeType, @Param("scopeId") String scopeId);

    @Update("UPDATE nw_storage_quota SET quota_used = quota_used + #{bytesDelta}, " +
            "file_count_used = file_count_used + #{fileCountDelta}, updated_at = NOW() " +
            "WHERE scope_type = #{scopeType} AND scope_id = #{scopeId} " +
            "AND quota_used + #{bytesDelta} <= quota_limit " +
            "AND file_count_used + #{fileCountDelta} <= file_count_limit")
    int addUsage(@Param("scopeType") String scopeType, @Param("scopeId") String scopeId,
                 @Param("bytesDelta") long bytesDelta, @Param("fileCountDelta") int fileCountDelta);

    @Update("UPDATE nw_storage_quota SET quota_used = GREATEST(quota_used - #{bytesDelta}, 0), " +
            "file_count_used = GREATEST(file_count_used - #{fileCountDelta}, 0), updated_at = NOW() " +
            "WHERE scope_type = #{scopeType} AND scope_id = #{scopeId}")
    int subtractUsage(@Param("scopeType") String scopeType, @Param("scopeId") String scopeId,
                      @Param("bytesDelta") long bytesDelta, @Param("fileCountDelta") int fileCountDelta);

    /**
     * 带 revision 乐观锁的更新（更新失败返回 0）
     */
    int updateWithRevision(@Param("quota") StorageQuota quota);
}
