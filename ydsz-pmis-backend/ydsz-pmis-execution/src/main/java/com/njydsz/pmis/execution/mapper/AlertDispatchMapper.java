package com.njydsz.pmis.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.execution.entity.AlertDispatchDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AlertDispatchMapper extends BaseMapper<AlertDispatchDO> {

    /**
     * 按等级 / 状态扫描待发送/已发送预警
     */
    List<AlertDispatchDO> selectByLevelAndStatus(@Param("level") String level,
                                                 @Param("status") String status);

    /**
     * 扫描 PENDING 且已超过重试时间的预警
     */
    List<AlertDispatchDO> selectRetryable(@Param("now") LocalDateTime now,
                                          @Param("maxRetry") int maxRetry);

    /**
     * 标记发送结果
     */
    int markSent(@Param("id") Long id, @Param("sentAt") LocalDateTime sentAt);

    int markFailed(@Param("id") Long id, @Param("reason") String reason);

    int incrementRetry(@Param("id") Long id);

    /**
     * 按类型 + 等级 聚合
     */
    List<Map<String, Object>> aggregateByTypeAndLevel(@Param("tenantId") Long tenantId);
}
