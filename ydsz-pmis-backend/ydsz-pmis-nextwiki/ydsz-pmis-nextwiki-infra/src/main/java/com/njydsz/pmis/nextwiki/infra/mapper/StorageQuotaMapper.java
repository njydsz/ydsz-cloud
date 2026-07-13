package com.njydsz.pmis.nextwiki.infra.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.nextwiki.domain.entity.StorageQuota;

/**
 * 存储配额 MyBatis Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Mapper
public interface StorageQuotaMapper extends BaseMapper<StorageQuota> {

    StorageQuota selectByScope(@Param("scopeType") String scopeType, @Param("scopeId") String scopeId);

    @Update("UPDATE nw_storage_quota SET quota_used = quota_used + #{bytesDelta}, " +
            "file_count_used = file_count_used + #{fileCountDelta}, updated_at = NOW() " +
            "WHERE scope_type = #{scopeType} AND scope_id = #{scopeId}")
    int addUsage(@Param("scopeType") String scopeType, @Param("scopeId") String scopeId,
                 @Param("bytesDelta") long bytesDelta, @Param("fileCountDelta") int fileCountDelta);

    @Update("UPDATE nw_storage_quota SET quota_used = GREATEST(quota_used - #{bytesDelta}, 0), " +
            "file_count_used = GREATEST(file_count_used - #{fileCountDelta}, 0), updated_at = NOW() " +
            "WHERE scope_type = #{scopeType} AND scope_id = #{scopeId}")
    int subtractUsage(@Param("scopeType") String scopeType, @Param("scopeId") String scopeId,
                      @Param("bytesDelta") long bytesDelta, @Param("fileCountDelta") int fileCountDelta);
}
