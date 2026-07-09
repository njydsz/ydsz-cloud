package com.njydsz.pmis.project.mapper.resource;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.resource.BillableUtilizationSnapshotDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 可计费利用率快照 Mapper
 *
 * <p>Cronjob 写入 + Cockpit / 报表读取。
 */
@Mapper
public interface BillableUtilizationSnapshotMapper extends BaseMapper<BillableUtilizationSnapshotDO> {

    /**
     * 按 (period, employeeId) UPSERT（PostgreSQL ON CONFLICT）。
     *
     * @param row 快照行数据
     * @return 受影响行数
     */
    int upsert(@Param("row") BillableUtilizationSnapshotDO row);

    /**
     * 按周期删除（重算时使用）
     *
     * @param period 周期
     * @return 受影响行数
     */
    int deleteByPeriod(@Param("period") String period);

    /**
     * 查询某周期所有快照
     *
     * @param period 周期
     * @return 快照列表
     */
    List<BillableUtilizationSnapshotDO> selectByPeriod(@Param("period") String period);

    /**
     * 查询某区间的所有快照（用于跨月聚合）
     *
     * @param from 起始日期
     * @param to   截止日期
     * @return 快照列表
     */
    List<BillableUtilizationSnapshotDO> selectByRange(@Param("from") LocalDate from,
                                                      @Param("to") LocalDate to);

    /**
     * 周期平均值（公司/团队级）
     *
     * @param period 周期
     * @return 周期平均值数据
     */
    Map<String, Object> averageByPeriod(@Param("period") String period);

    /**
     * 区间平均
     *
     * @param from 起始日期
     * @param to   截止日期
     * @return 区间平均值数据
     */
    Map<String, Object> averageByRange(@Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    /**
     * 部门维度（取最新周期）
     *
     * @param period 周期
     * @return 部门维度聚合列表
     */
    List<Map<String, Object>> groupByDepartment(@Param("period") String period);

    /**
     * 等级分布（用于驾驶舱健康仪表盘）
     *
     * @param period 周期
     * @return 等级分布列表
     */
    List<Map<String, Object>> gradeDistribution(@Param("period") String period);

    /**
     * 排行榜 top N
     *
     * @param period 周期
     * @param top    返回前 N 条
     * @return 快照排行榜列表
     */
    List<BillableUtilizationSnapshotDO> rankTop(@Param("period") String period,
                                                @Param("top") int top);

    /**
     * 预警员工（grade IN WARN/CRITICAL）
     *
     * @param period 周期
     * @return 预警员工快照列表
     */
    List<BillableUtilizationSnapshotDO> alertEmployees(@Param("period") String period);
}
