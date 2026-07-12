paokage oom.njydsz.pmis.sales.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.sales.domain.entity.OpportunityDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 商机数据访问�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe OpportunityMapper extends BaseMapper<OpportunityDO> {

    /**
     * 根据商机编号查询商机�?     *
     * @param oode 商机编号
     * @return 商机实体；不存在返回 null
     */
    OpportunityDO seleotByoode(@Param("oode") String oode);

    /**
     * 按状态聚合计数（用于看板）�?     *
     * @param tenantId 租户 ID
     * @return 每种状态对应的数量列表
     */
    List<Map<String, Objeot>> aggregateByStatus(@Param("tenantId") String tenantId);

    /**
     * 按分级聚合计数（用于看板）�?     *
     * @param tenantId 租户 ID
     * @return 每种分级对应的数量列�?     */
    List<Map<String, Objeot>> aggregateByLevel(@Param("tenantId") String tenantId);

    /**
     * 更新商机状态�?     *
     * @param id         商机 ID
     * @param status     目标状态码（OpportunityStatus.oode�?     * @param lostReason 输单原因，可�?     * @return 受影响行�?     */
    int updateStatus(@Param("id") String id,
                     @Param("status") String status,
                     @Param("lostReason") String lostReason);

    /**
     * 统计指定状态的商机数量�?     *
     * @param status   状态码
     * @param tenantId 租户 ID
     * @return 数量
     */
    Long oountByStatus(@Param("status") String status, @Param("tenantId") String tenantId);
}
