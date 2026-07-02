package com.njydsz.pmis.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.user.entity.BenchRecordDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Bench 闲置记录 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface BenchRecordMapper extends BaseMapper<BenchRecordDO> {

    /**
     * 根据记录编码查询 Bench 记录
     *
     * @param code 记录编码
     * @return Bench 记录，未找到返回 null
     */
    BenchRecordDO selectByCode(@Param("code") String code);

    /**
     * 员工当前活跃（未出池）的 Bench 记录
     *
     * @param employeeId 员工 ID
     * @return 活跃 Bench 记录，未找到返回 null
     */
    BenchRecordDO selectActiveByEmployee(@Param("employeeId") Long employeeId);

    /**
     * 根据状态查询 Bench 记录列表
     *
     * @param status 状态编码
     * @return Bench 记录列表
     */
    List<BenchRecordDO> selectByStatus(@Param("status") String status);

    /**
     * 根据资源池 ID 与状态查询 Bench 记录列表
     *
     * @param poolId 资源池 ID
     * @param status 状态编码
     * @return Bench 记录列表
     */
    List<BenchRecordDO> selectByPool(@Param("poolId") Long poolId, @Param("status") String status);

    /**
     * 闲置池汇总：按池统计当前人数/总成本/平均天数
     *
     * @param status 状态编码
     * @return 汇总结果列表（每行包含池维度与统计指标）
     */
    List<Map<String, Object>> aggregateByPool(@Param("status") String status);

    /**
     * 指定时间区间内的入职/出池次数
     *
     * @param from 起始日期
     * @param to 截止日期
     * @return 流量统计列表
     */
    List<Map<String, Object>> flowByDateRange(@Param("from") LocalDate from,
                                              @Param("to") LocalDate to);
}
