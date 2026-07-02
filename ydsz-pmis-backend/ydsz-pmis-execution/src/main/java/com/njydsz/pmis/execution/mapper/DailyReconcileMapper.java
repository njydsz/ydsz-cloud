package com.njydsz.pmis.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.execution.entity.DailyReconcileDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 每日对账 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface DailyReconcileMapper extends BaseMapper<DailyReconcileDO> {

    /**
     * 按 (date, type, initId) 查重
     */
    DailyReconcileDO selectUnique(@Param("date") LocalDate date,
                                  @Param("type") String type,
                                  @Param("initiationId") Long initiationId);

    /**
     * 按日期范围 + 状态 查询
     */
    List<DailyReconcileDO> selectByDateRange(@Param("from") LocalDate from,
                                             @Param("to") LocalDate to,
                                             @Param("status") String status);

    /**
     * 统计某段时间 ERROR/WARN 数量
     */
    List<Map<String, Object>> aggregateByStatus(@Param("from") LocalDate from,
                                                @Param("to") LocalDate to);
}
