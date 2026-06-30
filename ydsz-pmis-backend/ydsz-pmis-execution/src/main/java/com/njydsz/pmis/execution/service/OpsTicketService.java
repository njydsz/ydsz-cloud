package com.njydsz.pmis.execution.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.execution.dto.OpsTicketAssignDTO;
import com.njydsz.pmis.execution.dto.OpsTicketCreateDTO;
import com.njydsz.pmis.execution.dto.OpsTicketStatusDTO;
import com.njydsz.pmis.execution.entity.OpsTicketDO;

import java.util.List;
import java.util.Map;

/**
 * 运维工单服务
 *
 * <p>P1-P4 SLA 跟踪、超时自动标记 breached；工单关闭后可触发满意度评价。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface OpsTicketService {

    /** 创建工单（按优先级自动计算 SLA 截止） */
    Long create(OpsTicketCreateDTO dto);

    /** 派单 */
    void assign(OpsTicketAssignDTO dto);

    /** 状态变更（含 SLA 计时刷新） */
    void changeStatus(OpsTicketStatusDTO dto);

    /** SLA 扫描：标记超时工单（用于定时任务） */
    int scanSlaBreaches();

    /** 关闭工单并允许评价 */
    void closeAndEvaluate(OpsTicketStatusDTO dto);

    /** 分页查询 */
    Page<OpsTicketDO> page(int page, int size, String status, String priority,
                           Long initiationId, Long assigneeId, String keyword);

    /** 按项目查询 */
    List<OpsTicketDO> listByInitiation(Long initiationId);

    /** 按质保期查询 */
    List<OpsTicketDO> listByWarranty(Long warrantyId);

    /** 按处理人查询 */
    List<OpsTicketDO> listByAssignee(Long assigneeId, String status);

    /** SLA 达成率统计 */
    List<Map<String, Object>> slaSummary();

    /** 状态聚合 */
    List<Map<String, Object>> aggregateByStatus(Long initiationId);
}
