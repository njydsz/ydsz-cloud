package com.njydsz.pmis.userinfo.mapper.rate;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.userinfo.entity.rate.AttendanceDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 出勤记录 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface AttendanceMapper extends BaseMapper<AttendanceDO> {

    /**
     * 按员工 + 月份范围查询
     */
    List<AttendanceDO> selectByEmployeeAndDateRange(@Param("employeeId") String employeeId,
                                                    @Param("startDate") LocalDate startDate,
                                                    @Param("endDate") LocalDate endDate);

    /**
     * 按状态汇总 (例如: 月度出勤统计)
     */
    List<Map<String, Object>> statByStatus(@Param("employeeId") String employeeId,
                                            @Param("startDate") LocalDate startDate,
                                            @Param("endDate") LocalDate endDate);
}
