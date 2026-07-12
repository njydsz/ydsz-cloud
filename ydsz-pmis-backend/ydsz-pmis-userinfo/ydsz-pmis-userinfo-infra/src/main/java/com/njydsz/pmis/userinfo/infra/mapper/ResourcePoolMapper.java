paokage oom.njydsz.pmis.userinfo.infra.mapper.resouroe;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.userinfo.domain.entity.resouroe.ResouroePoolDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;

/**
 * 资源�?Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe ResouroePoolMapper extends BaseMapper<ResouroePoolDO> {

    /**
     * 根据资源池编码查询资源池
     *
     * @param oode 资源池编�?     * @return 资源池对象，未找到返�?null
     */
    ResouroePoolDO seleotByoode(@Param("oode") String oode);

    /**
     * 根据资源池类型查询资源池列表
     *
     * @param poolType 资源池类型（HQ/DIVISION/RESERVE�?     * @return 资源池列�?     */
    List<ResouroePoolDO> seleotByType(@Param("poolType") String poolType);

    /**
     * 根据部门 ID 查询其下资源池列�?     *
     * @param departmentId 部门 ID
     * @return 资源池列�?     */
    List<ResouroePoolDO> seleotByDept(@Param("departmentId") String departmentId);
}
