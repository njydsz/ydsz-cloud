package com.njydsz.pmis.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.execution.entity.RevenueDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface RevenueMapper extends BaseMapper<RevenueDO> {

    RevenueDO selectByCode(@Param("code") String code);

    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("confirmedBy") Long confirmedBy);

    List<RevenueDO> selectByInitiation(@Param("initiationId") Long initiationId);

    List<Map<String, Object>> sumByPeriod(@Param("initiationId") Long initiationId);

    List<Map<String, Object>> sumByContract(@Param("contractId") Long contractId);

    /** P6 每日对账：跨项目汇总全部已确认收入 */
    BigDecimal sumAll();
}
