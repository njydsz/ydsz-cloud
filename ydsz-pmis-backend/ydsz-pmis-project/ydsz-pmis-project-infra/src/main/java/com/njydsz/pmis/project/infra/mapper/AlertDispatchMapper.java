package com.njydsz.pmis.project.infra.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.domain.entity.AlertDispatchDO;

/**
 * 预警派发 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface AlertDispatchMapper extends BaseMapper<AlertDispatchDO> {

    /**
     * 按等级 / 状态扫描待发送/已发送预警
     *
     * @param level  预警等级，可选
     * @param status 预警状态，可选
     * @return 预警记录列表
     */
    List<AlertDispatchDO> selectByLevelAndStatus(@Param("level") String level,
                                                 @Param("status") String status);

    /**
     * 扫描 PENDING 且已超过重试时间的预警
     *
     * @param now      当前时间
     * @param maxRetry 最大重试次数
     * @return 可重试的预警记录列表
     */
    List<AlertDispatchDO> selectRetryable(@Param("now") LocalDateTime now,
                                          @Param("maxRetry") int maxRetry);

    /**
     * 标记发送成功
     *
     * @param id     预警记录 ID
     * @param sentAt 发送时间
     * @return 受影响行数
     */
    int markSent(@Param("id") String id, @Param("sentAt") LocalDateTime sentAt);

    /**
     * 标记发送失败
     *
     * @param id     预警记录 ID
     * @param reason 失败原因
     * @return 受影响行数
     */
    int markFailed(@Param("id") String id, @Param("reason") String reason);

    /**
     * 递增重试次数
     *
     * @param id 预警记录 ID
     * @return 受影响行数
     */
    int incrementRetry(@Param("id") String id);

    /**
     * 按类型 + 等级 聚合
     *
     * @param tenantId 租户 ID，可选
     * @return 聚合统计列表
     */
    List<Map<String, Object>> aggregateByTypeAndLevel(@Param("tenantId") String tenantId);
}
