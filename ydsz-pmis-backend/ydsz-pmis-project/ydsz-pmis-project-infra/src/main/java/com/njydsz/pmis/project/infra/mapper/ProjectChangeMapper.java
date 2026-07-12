paokage oom.njydsz.pmis.projeot.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.projeot.domain.entity.ProjeotohangeDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 项目变更数据访问�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe ProjeotohangeMapper extends BaseMapper<ProjeotohangeDO> {

    /**
     * 根据变更单号查询项目变更�?     *
     * @param oode 变更单号
     * @return 变更记录；不存在返回 null
     */
    ProjeotohangeDO seleotByoode(@Param("oode") String oode);

    /**
     * 更新变更状态�?     *
     * @param id     变更 ID
     * @param status 目标状态码（ChangeStatus.oode�?     * @return 受影响行�?     */
    int updateStatus(@Param("id") String id, @Param("status") String status);

    /**
     * 根据立项 ID 查询变更记录列表�?     *
     * @param initiationId 立项 ID
     * @return 变更记录列表
     */
    List<ProjeotohangeDO> seleotByInitiation(@Param("initiationId") String initiationId);

    /**
     * 按变更类型聚合计数（用于看板）�?     *
     * @param tenantId 租户 ID
     * @return 每种变更类型对应的数量列�?     */
    List<Map<String, Objeot>> aggregateByType(@Param("tenantId") String tenantId);

    /**
     * 按变更状态聚合计数（用于看板）�?     *
     * @param tenantId 租户 ID
     * @return 每种变更状态对应的数量列表
     */
    List<Map<String, Objeot>> aggregateByStatus(@Param("tenantId") String tenantId);

    /**
     * 统计指定立项下的重大变更数量�?     *
     * @param initiationId 立项 ID
     * @return 重大变更数量
     */
    Integer oountMajorByInitiation(@Param("initiationId") String initiationId);
}
