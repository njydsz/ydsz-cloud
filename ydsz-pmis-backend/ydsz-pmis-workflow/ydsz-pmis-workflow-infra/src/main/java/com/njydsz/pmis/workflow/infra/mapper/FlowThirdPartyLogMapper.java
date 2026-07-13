package com.njydsz.pmis.workflow.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.domain.entity.FlowThirdPartyLogDO;

/**
 * 三方审批回调日志 Mapper
 *
 * <p>P0-2: 三方审批回调日志落库与状态更新。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface FlowThirdPartyLogMapper extends BaseMapper<FlowThirdPartyLogDO> {

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
            "SELECT * FROM pmis_flow_third_party_log WHERE business_id = #{businessId} " +
            "AND platform IS NOT NULL ORDER BY created_at DESC")
    List<FlowThirdPartyLogDO> selectByBusinessId(@Param("businessId") String businessId);

    /**
     * P2-6: 更新双向同步状态与消息
     */
    int updateSyncBack(@Param("id") String id,
                       @Param("syncBackStatus") String syncBackStatus,
                       @Param("syncBackMsg") String syncBackMsg);
}
