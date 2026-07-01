package com.njydsz.pmis.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.execution.entity.RiskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface RiskMapper extends BaseMapper<RiskDO> {

    RiskDO selectByCode(@Param("code") String code);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    List<RiskDO> selectByInitiation(@Param("initiationId") Long initiationId);

    List<Map<String, Object>> aggregateByLevel(@Param("initiationId") Long initiationId);

    /** 查询所有未结风险 */
    List<RiskDO> selectAll();

    /**
     * 批次18：按风险等级统计未结风险数量
     *
     * <p>用于高管看板"风险项目数"统计；riskLevel 缺失/空 时归并到 'UNKNOWN'。
     * 返回字段：riskLevel / cnt
     */
    List<Map<String, Object>> countByRiskLevel();
}
