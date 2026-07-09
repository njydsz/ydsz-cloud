package com.njydsz.pmis.project.mapper.aftersales;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.aftersales.OpsTicketDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 运维工单 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface OpsTicketMapper extends BaseMapper<OpsTicketDO> {

    /**
     * 按编码查询运维工单
     *
     * @param code 工单编码
     * @return 工单对象，未找到返回 null
     */
    OpsTicketDO selectByCode(@Param("code") String code);

    /**
     * 按立项 ID 查询工单列表
     *
     * @param initiationId 立项 ID
     * @return 工单列表
     */
    List<OpsTicketDO> selectByInitiation(@Param("initiationId") String initiationId);

    /**
     * 按质保期 ID 查询工单列表
     *
     * @param warrantyId 质保期 ID
     * @return 工单列表
     */
    List<OpsTicketDO> selectByWarranty(@Param("warrantyId") String warrantyId);

    /**
     * 按经办人 + 状态查询工单列表
     *
     * @param assigneeId 经办人 ID
     * @param status     工单状态
     * @return 工单列表
     */
    List<OpsTicketDO> selectByAssignee(@Param("assigneeId") String assigneeId,
                                       @Param("status") String status);

    /**
     * 未完成的工单（用于 SLA 扫描）
     *
     * @param now 当前时间
     * @return 未完成工单列表
     */
    List<OpsTicketDO> selectActiveTickets(@Param("now") LocalDateTime now);

    /**
     * 更新工单状态
     *
     * @param id     工单 ID
     * @param status 目标状态
     * @return 受影响行数
     */
    int updateStatus(@Param("id") String id, @Param("status") String status);

    /**
     * 更新经办人
     *
     * @param id           工单 ID
     * @param assigneeId   经办人 ID
     * @param assigneeName 经办人姓名
     * @param status       目标状态
     * @param acceptedAt   受理时间
     * @return 受影响行数
     */
    int updateAssignee(@Param("id") String id, @Param("assigneeId") String assigneeId,
                       @Param("assigneeName") String assigneeName,
                       @Param("status") String status,
                       @Param("acceptedAt") LocalDateTime acceptedAt);

    /**
     * 标记响应超时
     *
     * @param id 工单 ID
     * @return 受影响行数
     */
    int markResponseBreached(@Param("id") String id);

    /**
     * 标记解决超时
     *
     * @param id 工单 ID
     * @return 受影响行数
     */
    int markResolveBreached(@Param("id") String id);

    /**
     * 按状态聚合统计
     *
     * @param initiationId 立项 ID
     * @return 状态聚合列表
     */
    List<Map<String, Object>> aggregateByStatus(@Param("initiationId") String initiationId);

    /**
     * 按优先级 + 是否超时 聚合 SLA 达成率
     *
     * @return SLA 达成率聚合列表
     */
    List<Map<String, Object>> aggregateSlaBreach();
}
