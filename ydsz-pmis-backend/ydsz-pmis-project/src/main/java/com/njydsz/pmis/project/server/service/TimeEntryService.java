package com.njydsz.pmis.project.server.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.project.domain.dto.TimeEntryApprovalDTO;
import com.njydsz.pmis.project.domain.dto.TimeEntryCreateDTO;
import com.njydsz.pmis.project.domain.entity.TimeEntryDO;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 工时服务
 *
 * <p>提供工时录入、审批、查询与聚合统计能力，支撑成本归集与可计费利用率计算。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface TimeEntryService {

    /**
     * 创建工时录入
     *
     * @param dto 工时创建参数
     * @return 工时记录ID
     */
    String create(TimeEntryCreateDTO dto);

    /**
     * 提交审批
     *
     * @param id 工时记录ID
     */
    void submit(String id);

    /**
     * 审批通过/驳回
     *
     * @param dto 审批参数
     */
    void approve(TimeEntryApprovalDTO dto);

    /**
     * 删除工时记录
     *
     * @param id 工时记录ID
     */
    void delete(String id);

    /**
     * 根据ID查询工时记录
     *
     * @param id 工时记录ID
     * @return 工时实体
     */
    TimeEntryDO getById(String id);

    /**
     * 分页查询工时
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词
     * @param status       状态过滤
     * @param employeeId   员工ID
     * @param initiationId 项目立项ID
     * @param taskId       任务ID
     * @param from         起始日期
     * @param to           截止日期
     * @return 分页结果
     */
    Page<TimeEntryDO> page(int page, int size, String keyword, String status,
                           String employeeId, String initiationId, String taskId,
                           LocalDate from, LocalDate to);

    /**
     * 按人员+日期范围查询
     *
     * @param employeeId 员工ID
     * @param from       起始日期
     * @param to         截止日期
     * @return 工时列表
     */
    List<TimeEntryDO> listByEmployeeAndDateRange(String employeeId, LocalDate from, LocalDate to);

    /**
     * 按项目+日期范围查询
     *
     * @param initiationId 项目立项ID
     * @param from         起始日期
     * @param to           截止日期
     * @return 工时列表
     */
    List<TimeEntryDO> listByInitiationAndDateRange(String initiationId, LocalDate from, LocalDate to);

    /**
     * 项目工时聚合（按员工与职级）
     *
     * @param initiationId 项目立项ID
     * @param from         起始日期
     * @param to           截止日期
     * @return 聚合结果列表
     */
    List<Map<String, Object>> aggregateHoursByEmployeeAndLevel(String initiationId,
                                                               LocalDate from, LocalDate to);

    /**
     * 跨项目冲突检测
     *
     * @param employeeId 员工ID
     * @param entryDate  填报日期
     * @return 冲突明细列表
     */
    List<Map<String, Object>> detectCrossProject(String employeeId, LocalDate entryDate);

    /**
     * 工时异常统计（按项目 + 月份）
     *
     * <p>聚合指定项目在指定月份的工时异常情况，供 Agent 工具 / 周报月报场景调用：
     * <ul>
     *   <li>{@code overtimeCount}：加班记录数（overtime &gt; 0）</li>
     *   <li>{@code missingCount}：漏报记录数（状态为 DRAFT 视为未提交/漏报）</li>
     *   <li>{@code abnormalCount}：异常记录数（状态为 REJECTED）</li>
     *   <li>{@code totalHours}：总工时（小时）</li>
     * </ul>
     *
     * @param initiationId 项目立项ID
     * @param month        月份（yyyy-MM），为 null 时取当前月
     * @return 异常统计 Map
     */
    Map<String, Object> abnormalStat(String initiationId, String month);
}
