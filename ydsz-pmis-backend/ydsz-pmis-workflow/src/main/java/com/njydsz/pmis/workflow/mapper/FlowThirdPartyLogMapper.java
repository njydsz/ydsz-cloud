package com.njydsz.pmis.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.entity.FlowThirdPartyLogDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 三方审批回调日志 Mapper
 *
 * <p>P0-2: 三方审批回调日志落库与状态更新。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
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
}
