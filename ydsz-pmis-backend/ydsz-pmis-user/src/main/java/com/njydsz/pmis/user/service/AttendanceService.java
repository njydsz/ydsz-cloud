package com.njydsz.pmis.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.user.dto.AttendanceCreateDTO;
import com.njydsz.pmis.user.dto.LeaveCreateDTO;
import com.njydsz.pmis.user.dto.OvertimeCreateDTO;
import com.njydsz.pmis.user.entity.AttendanceDO;
import com.njydsz.pmis.user.entity.LeaveDO;
import com.njydsz.pmis.user.entity.OvertimeDO;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 考勤服务
 *
 * <p>覆盖出勤/加班/请假 三类记录。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface AttendanceService {

    // ===== 出勤 =====

    /**
     * 登记出勤 (打卡/手动)
     */
    Long recordAttendance(AttendanceCreateDTO dto);

    /**
     * 查询出勤记录
     */
    Page<AttendanceDO> pageAttendance(Long employeeId, LocalDate startDate, LocalDate endDate, int page, int size);

    /**
     * 月度出勤统计
     */
    List<Map<String, Object>> statByStatus(Long employeeId, LocalDate startDate, LocalDate endDate);

    // ===== 加班 =====

    /**
     * 提交加班申请
     */
    Long submitOvertime(OvertimeCreateDTO dto);

    /**
     * 审批加班
     */
    void approveOvertime(Long id, String action, String approverId, String approverName, String remark);

    /**
     * 加班分页
     */
    Page<OvertimeDO> pageOvertime(Long employeeId, String approvalStatus, int page, int size);

    /**
     * 加班详情
     */
    OvertimeDO getOvertime(Long id);

    // ===== 请假 =====

    /**
     * 提交请假
     */
    Long submitLeave(LeaveCreateDTO dto);

    /**
     * 审批请假
     */
    void approveLeave(Long id, String action, String approverId, String approverName, String remark);

    /**
     * 请假分页
     */
    Page<LeaveDO> pageLeave(Long employeeId, String approvalStatus, int page, int size);

    /**
     * 请假详情
     */
    LeaveDO getLeave(Long id);

    /**
     * 员工在指定日期范围内已批准的请假 (供出勤模块关联判断)
     */
    List<LeaveDO> listApprovedLeaves(Long employeeId, LocalDate startDate, LocalDate endDate);
}
