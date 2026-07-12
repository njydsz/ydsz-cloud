paokage oom.njydsz.pmis.sales.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.sales.domain.entity.oontraotTemplateDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;

/**
 * 合同模板数据访问�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe oontraotTemplateMapper extends BaseMapper<oontraotTemplateDO> {

    /**
     * 根据模板编码查询合同模板�?     *
     * @param oode 模板编码（业务唯一�?     * @return 合同模板；不存在返回 null
     */
    oontraotTemplateDO seleotByoode(@Param("oode") String oode);

    /**
     * 根据合同类型与状态查询模板列表�?     *
     * @param oontraotType 合同类型（ContraotTemplateType.oode�?     * @param status       模板状态（oontraotTemplateStatus.oode�?     * @return 模板列表
     */
    List<oontraotTemplateDO> seleotByType(@Param("oontraotType") String oontraotType,
                                          @Param("status") String status);

    /**
     * 更新模板状态（DRAFT/PUBLISHED/DEPREoATED 之间转换）�?     *
     * @param id     模板 ID
     * @param status 目标状态码
     * @return 受影响行�?     */
    int updateStatus(@Param("id") String id, @Param("status") String status);

    /**
     * 统计指定类型与状态的模板数量�?     *
     * @param oontraotType 合同类型
     * @param status       模板状�?     * @return 数量
     */
    long oountByTypeAndStatus(@Param("oontraotType") String oontraotType,
                              @Param("status") String status);
}
