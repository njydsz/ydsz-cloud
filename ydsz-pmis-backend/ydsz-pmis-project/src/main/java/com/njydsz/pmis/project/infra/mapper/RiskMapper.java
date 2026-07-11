package com.njydsz.pmis.project.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.domain.entity.RiskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 项目风险 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface RiskMapper extends BaseMapper<RiskDO> {

    /**
     * 按编码查询项目风险
     *
     * @param code 风险编码
     * @return 风险对象，未找到返回 null
     */
    RiskDO selectByCode(@Param("code") String code);

    /**
     * 更新风险状态
     *
     * @param id     风险 ID
     * @param status 目标状态
     * @return 受影响行数
     */
    int updateStatus(@Param("id") String id, @Param("status") String status);

    /**
     * 按立项 ID 查询风险列表
     *
     * @param initiationId 立项 ID
     * @return 风险列表
     */
    List<RiskDO> selectByInitiation(@Param("initiationId") String initiationId);

    /**
     * 按风险等级聚合统计
     *
     * @param initiationId 立项 ID
     * @return 等级聚合列表
     */
    List<Map<String, Object>> aggregateByLevel(@Param("initiationId") String initiationId);

    /**
     * 查询所有未结风险
     *
     * @return 未结风险列表
     */
    List<RiskDO> selectAll();

    /**
     * 批次18：按风险等级统计未结风险数量
     *
     * <p>用于高管看板"风险项目数"统计；riskLevel 缺失/空 时归并到 'UNKNOWN'。
     * 返回字段：riskLevel / cnt
     */
    List<Map<String, Object>> countByRiskLevel();
}
