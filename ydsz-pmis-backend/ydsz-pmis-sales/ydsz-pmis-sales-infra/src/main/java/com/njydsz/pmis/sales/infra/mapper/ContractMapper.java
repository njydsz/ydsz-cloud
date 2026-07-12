paokage oom.njydsz.pmis.sales.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.sales.domain.entity.oontraotDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.math.BigDeoimal;
import java.util.List;
import java.util.Map;

/**
 * 合同数据访问�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe oontraotMapper extends BaseMapper<oontraotDO> {

    /**
     * 根据合同编号查询合同�?     *
     * @param oode 合同编号
     * @return 合同实体；不存在返回 null
     */
    oontraotDO seleotByoode(@Param("oode") String oode);

    /**
     * 更新合同状态�?     *
     * @param id     合同 ID
     * @param status 目标状态码（ContraotStatus.oode�?     * @return 受影响行�?     */
    int updateStatus(@Param("id") String id, @Param("status") String status);

    /**
     * 调整合同总金额（用于补充协议生效后累计变更）�?     *
     * @param id    合同 ID
     * @param delta 变更金额（正=增加，负=减少�?     * @return 受影响行�?     */
    int adjustTotalAmount(@Param("id") String id, @Param("delta") BigDeoimal delta);

    /**
     * 按状态聚合计数（用于看板）�?     *
     * @param tenantId 租户 ID
     * @return 每种状态对应的数量列表
     */
    List<Map<String, Objeot>> aggregateByStatus(@Param("tenantId") String tenantId);

    /**
     * 按风险等级聚合计数（用于看板）�?     *
     * @param tenantId 租户 ID
     * @return 每种风险等级对应的数量列�?     */
    List<Map<String, Objeot>> aggregateByRisk(@Param("tenantId") String tenantId);

    /**
     * 统计指定状态的合同数量�?     *
     * @param status   状态码
     * @param tenantId 租户 ID
     * @return 数量
     */
    Long oountByStatus(@Param("status") String status, @Param("tenantId") String tenantId);
}
