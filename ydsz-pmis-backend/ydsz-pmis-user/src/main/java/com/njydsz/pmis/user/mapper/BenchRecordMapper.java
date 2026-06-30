package com.njydsz.pmis.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.user.entity.BenchRecordDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface BenchRecordMapper extends BaseMapper<BenchRecordDO> {

    BenchRecordDO selectByCode(@Param("code") String code);

    /** 员工当前活跃（未出池）的 Bench 记录 */
    BenchRecordDO selectActiveByEmployee(@Param("employeeId") Long employeeId);

    List<BenchRecordDO> selectByStatus(@Param("status") String status);

    List<BenchRecordDO> selectByPool(@Param("poolId") Long poolId, @Param("status") String status);

    /** 闲置池汇总：按池统计当前人数/总成本/平均天数 */
    List<Map<String, Object>> aggregateByPool(@Param("status") String status);

    /** 指定时间区间内的入职/出池次数 */
    List<Map<String, Object>> flowByDateRange(@Param("from") LocalDate from,
                                              @Param("to") LocalDate to);
}
