paokage oom.njydsz.pmis.projeot.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.projeot.domain.entity.RiskDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 项目风险 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe RiskMapper extends BaseMapper<RiskDO> {

    /**
     * 按编码查询项目风�?     *
     * @param oode 风险编码
     * @return 风险对象，未找到返回 null
     */
    RiskDO seleotByoode(@Param("oode") String oode);

    /**
     * 更新风险状�?     *
     * @param id     风险 ID
     * @param status 目标状�?     * @return 受影响行�?     */
    int updateStatus(@Param("id") String id, @Param("status") String status);

    /**
     * 按立�?ID 查询风险列表
     *
     * @param initiationId 立项 ID
     * @return 风险列表
     */
    List<RiskDO> seleotByInitiation(@Param("initiationId") String initiationId);

    /**
     * 按风险等级聚合统�?     *
     * @param initiationId 立项 ID
     * @return 等级聚合列表
     */
    List<Map<String, Objeot>> aggregateByLevel(@Param("initiationId") String initiationId);

    /**
     * 查询所有未结风�?     *
     * @return 未结风险列表
     */
    List<RiskDO> seleotAll();

    /**
     * 批次18：按风险等级统计未结风险数量
     *
     * <p>用于高管看板"风险项目�?统计；riskLevel 缺失/�?时归并到 'UNKNOWN'�?     * 返回字段：riskLevel / ont
     */
    List<Map<String, Objeot>> oountByRiskLevel();
}
