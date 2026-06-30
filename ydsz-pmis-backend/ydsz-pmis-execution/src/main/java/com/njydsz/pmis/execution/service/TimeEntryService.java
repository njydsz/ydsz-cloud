package com.njydsz.pmis.execution.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.execution.dto.TimeEntryApprovalDTO;
import com.njydsz.pmis.execution.dto.TimeEntryCreateDTO;
import com.njydsz.pmis.execution.entity.TimeEntryDO;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 工时服务
 */
public interface TimeEntryService {

    Long create(TimeEntryCreateDTO dto);

    /**
     * 提交审批
     */
    void submit(Long id);

    /**
     * 审批通过/驳回
     */
    void approve(TimeEntryApprovalDTO dto);

    void delete(Long id);

    TimeEntryDO getById(Long id);

    Page<TimeEntryDO> page(int page, int size, String keyword, String status,
                           Long employeeId, Long initiationId, Long taskId,
                           LocalDate from, LocalDate to);

    /**
     * 按人员+日期范围
     */
    List<TimeEntryDO> listByEmployeeAndDateRange(Long employeeId, LocalDate from, LocalDate to);

    /**
     * 按项目+日期范围
     */
    List<TimeEntryDO> listByInitiationAndDateRange(Long initiationId, LocalDate from, LocalDate to);

    /**
     * 项目工时聚合
     */
    List<Map<String, Object>> aggregateHoursByEmployeeAndLevel(Long initiationId,
                                                               LocalDate from, LocalDate to);

    /**
     * 跨项目冲突检测
     */
    List<Map<String, Object>> detectCrossProject(Long employeeId, LocalDate entryDate);
}
