package com.njydsz.pmis.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.execution.entity.OpsTicketDO;
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

    OpsTicketDO selectByCode(@Param("code") String code);

    List<OpsTicketDO> selectByInitiation(@Param("initiationId") Long initiationId);

    List<OpsTicketDO> selectByWarranty(@Param("warrantyId") Long warrantyId);

    List<OpsTicketDO> selectByAssignee(@Param("assigneeId") Long assigneeId,
                                       @Param("status") String status);

    /** 未完成的工单（用于 SLA 扫描） */
    List<OpsTicketDO> selectActiveTickets(@Param("now") LocalDateTime now);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int updateAssignee(@Param("id") Long id, @Param("assigneeId") Long assigneeId,
                       @Param("assigneeName") String assigneeName,
                       @Param("status") String status,
                       @Param("acceptedAt") LocalDateTime acceptedAt);

    int markResponseBreached(@Param("id") Long id);

    int markResolveBreached(@Param("id") Long id);

    /** 按状态聚合统计 */
    List<Map<String, Object>> aggregateByStatus(@Param("initiationId") Long initiationId);

    /** 按优先级 + 是否超时 聚合 SLA 达成率 */
    List<Map<String, Object>> aggregateSlaBreach();
}
