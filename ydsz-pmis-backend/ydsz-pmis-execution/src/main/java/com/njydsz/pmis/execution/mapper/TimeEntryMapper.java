package com.njydsz.pmis.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.execution.entity.TimeEntryDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 工时录入 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface TimeEntryMapper extends BaseMapper<TimeEntryDO> {

    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("approverId") Long approverId, @Param("approverName") String approverName,
                     @Param("rejectReason") String rejectReason);

    List<TimeEntryDO> selectByEmployeeAndDateRange(@Param("employeeId") Long employeeId,
                                                    @Param("from") LocalDate from,
                                                    @Param("to") LocalDate to);

    List<TimeEntryDO> selectByInitiationAndDateRange(@Param("initiationId") Long initiationId,
                                                     @Param("from") LocalDate from,
                                                     @Param("to") LocalDate to);

    List<TimeEntryDO> selectByTask(@Param("taskId") Long taskId);

    List<TimeEntryDO> selectByStatus(@Param("status") String status);

    /**
     * 聚合工时（按人员+职级）
     */
    List<Map<String, Object>> aggregateHoursByEmployeeAndLevel(@Param("initiationId") Long initiationId,
                                                               @Param("from") LocalDate from,
                                                               @Param("to") LocalDate to);

    /**
     * 按天聚合作业时长，用于异常校验（连续 3 天 0 填报）
     */
    List<Map<String, Object>> aggregateByDay(@Param("employeeId") Long employeeId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    /**
     * 跨项目冲突检测：员工同一天在多个项目填写工时
     */
    List<Map<String, Object>> detectCrossProject(@Param("employeeId") Long employeeId,
                                                 @Param("entryDate") LocalDate entryDate);

    /**
     * P4-1 可计费利用率：按员工 × 月份聚合 billable / total / overtime / leave
     */
    List<Map<String, Object>> aggregateBillableByEmployee(@Param("from") LocalDate from,
                                                          @Param("to") LocalDate to);

    /**
     * P4-1 单员工可计费利用率
     */
    Map<String, Object> aggregateBillableOne(@Param("employeeId") Long employeeId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    /**
     * P4-3 已审批工时总小时数
     */
    BigDecimal sumApprovedHours();
}
