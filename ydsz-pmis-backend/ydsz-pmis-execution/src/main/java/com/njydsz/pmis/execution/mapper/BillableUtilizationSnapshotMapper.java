package com.njydsz.pmis.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.execution.entity.BillableUtilizationSnapshotDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 可计费利用率快照 Mapper
 *
 * <p>Scheduler 写入 + Cockpit / 报表读取。
 */
@Mapper
public interface BillableUtilizationSnapshotMapper extends BaseMapper<BillableUtilizationSnapshotDO> {

    /**
     * 按 (period, employeeId) UPSERT（PostgreSQL ON CONFLICT）。
     */
    int upsert(@Param("row") BillableUtilizationSnapshotDO row);

    /**
     * 按周期删除（重算时使用）
     */
    int deleteByPeriod(@Param("period") String period);

    /**
     * 查询某周期所有快照
     */
    List<BillableUtilizationSnapshotDO> selectByPeriod(@Param("period") String period);

    /**
     * 查询某区间的所有快照（用于跨月聚合）
     */
    List<BillableUtilizationSnapshotDO> selectByRange(@Param("from") LocalDate from,
                                                      @Param("to") LocalDate to);

    /**
     * 周期平均值（公司/团队级）
     */
    Map<String, Object> averageByPeriod(@Param("period") String period);

    /**
     * 区间平均
     */
    Map<String, Object> averageByRange(@Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    /**
     * 部门维度（取最新周期）
     */
    List<Map<String, Object>> groupByDepartment(@Param("period") String period);

    /**
     * 等级分布（用于驾驶舱健康仪表盘）
     */
    List<Map<String, Object>> gradeDistribution(@Param("period") String period);

    /**
     * 排行榜 top N
     */
    List<BillableUtilizationSnapshotDO> rankTop(@Param("period") String period,
                                                @Param("top") int top);

    /**
     * 预警员工（grade IN WARN/CRITICAL）
     */
    List<BillableUtilizationSnapshotDO> alertEmployees(@Param("period") String period);
}
