package com.njydsz.workflow.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.workflow.domain.entity.FlowThirdPartyLog;

/**
 * 三方审批回调日志 Mapper
 *
 * <p>P0-2: 三方审批回调日志落库与状态更新。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface FlowThirdPartyLogMapper extends BaseMapper<FlowThirdPartyLog> {

    /**
     * 更新处理状态与错误信息
     *
     * @param id       日志 ID
     * @param status   处理状态: SUCCESS/FAIL
     * @param errorMsg 失败原因（成功时为 null）
     * @return 影响行数
     */
    int updateStatus(@Param("id") String id,
                     @Param("status") String status,
                     @Param("errorMsg") String errorMsg);

    /**
     * P2-6: 按业务 ID（本地流程实例 ID）查询关联的三方审批日志
     */
    @Select(
            "SELECT * FROM ydsz_flow_third_party_log WHERE business_id = #{businessId} " +
            "AND platform IS NOT NULL ORDER BY created_at DESC")
    List<FlowThirdPartyLog> selectByBusinessId(@Param("businessId") String businessId);

    /**
     * P2-6: 更新双向同步状态与消息
     */
    int updateSyncBack(@Param("id") String id,
                       @Param("syncBackStatus") String syncBackStatus,
                       @Param("syncBackMsg") String syncBackMsg);

    /**
     * P0-4: 扫描失败待重试的回调日志
     *
     * <p>选取 handle_status='FAIL' 且 retry_count 未超阈值的日志，按 last_retried_at NULLS FIRST 排序
     * （从未重试的优先），单批限量避免一次性加载过多。
     *
     * @param maxRetries 最大重试次数阈值（retry_count &lt; maxRetries）
     * @param batchSize  单批最多扫描条数
     * @return 待重试日志列表
     */
    @Select(
            "SELECT * FROM ydsz_flow_third_party_log " +
            "WHERE handle_status = 'FAIL' AND retry_count < #{maxRetries} " +
            "ORDER BY last_retried_at NULLS FIRST, created_at ASC " +
            "LIMIT #{batchSize}")
    List<FlowThirdPartyLog> selectFailedForRetry(@Param("maxRetries") int maxRetries,
                                                    @Param("batchSize") int batchSize);

    /**
     * P0-4: 更新重试结果
     *
     * <p>重试成功：handle_status=SUCCESS、error_msg=null、retry_count++、last_retried_at=now。
     * 重试失败：handle_status 保持 FAIL、error_msg 更新、retry_count++、last_retried_at=now。
     *
     * @param id           日志 ID
     * @param status       新状态（SUCCESS 或 FAIL）
     * @param errorMsg     错误信息（成功时为 null）
     * @param retryCount   新的重试次数
     * @return 影响行数
     */
    int updateRetryResult(@Param("id") String id,
                          @Param("status") String status,
                          @Param("errorMsg") String errorMsg,
                          @Param("retryCount") int retryCount);
}
