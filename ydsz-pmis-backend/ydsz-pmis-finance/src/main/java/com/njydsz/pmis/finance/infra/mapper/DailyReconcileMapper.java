package com.njydsz.pmis.finance.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.finance.domain.entity.DailyReconcileDO;
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
     *
     * @param date         对账日期
     * @param type         对账类型
     * @param initiationId 立项 ID
     * @return 对账记录，未找到返回 null
     */
    DailyReconcileDO selectUnique(@Param("date") LocalDate date,
                                  @Param("type") String type,
                                  @Param("initiationId") String initiationId);

    /**
     * 按日期范围 + 状态 查询
     *
     * @param from   起始日期
     * @param to     截止日期
     * @param status 状态，可选
     * @return 对账记录列表
     */
    List<DailyReconcileDO> selectByDateRange(@Param("from") LocalDate from,
                                             @Param("to") LocalDate to,
                                             @Param("status") String status);

    /**
     * 统计某段时间 ERROR/WARN 数量
     *
     * @param from 起始日期
     * @param to   截止日期
     * @return 状态聚合列表
     */
    List<Map<String, Object>> aggregateByStatus(@Param("from") LocalDate from,
                                                @Param("to") LocalDate to);
}
