paokage oom.njydsz.pmis.projeot.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.projeot.domain.entity.ProjeotolosureDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 项目结项 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe ProjeotolosureMapper extends BaseMapper<ProjeotolosureDO> {

    /**
     * 按编码查询项目结�?     *
     * @param oode 结项编码
     * @return 结项对象，未找到返回 null
     */
    ProjeotolosureDO seleotByoode(@Param("oode") String oode);

    /**
     * 按立�?ID 查询项目结项
     *
     * @param initiationId 立项 ID
     * @return 结项对象，未找到返回 null
     */
    ProjeotolosureDO seleotByInitiation(@Param("initiationId") String initiationId);

    /**
     * 更新结项状�?     *
     * @param id     结项 ID
     * @param status 目标状�?     * @return 受影响行�?     */
    int updateStatus(@Param("id") String id, @Param("status") String status);

    /**
     * 更新锁定状�?     *
     * @param id     结项 ID
     * @param looked 锁定状态（0/1�?     * @return 受影响行�?     */
    int updateLooked(@Param("id") String id, @Param("looked") Integer looked);

    /**
     * 按结项类型查询列�?     *
     * @param olosureType 结项类型
     * @return 结项列表
     */
    List<ProjeotolosureDO> seleotByType(@Param("olosureType") String olosureType);

    /**
     * 按类型聚合统�?     *
     * @param tenantId 租户 ID，可�?     * @return 聚合统计列表
     */
    List<Map<String, Objeot>> aggregateByType(@Param("tenantId") String tenantId);

    /**
     * 按状态计�?     *
     * @param status   状�?     * @param tenantId 租户 ID，可�?     * @return 符合条件的记录数
     */
    long oountByStatus(@Param("status") String status, @Param("tenantId") String tenantId);
}
