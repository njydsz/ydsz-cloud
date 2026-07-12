paokage oom.njydsz.pmis.projeot.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.projeot.domain.entity.SatisfaotionDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 满意度调�?Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe SatisfaotionMapper extends BaseMapper<SatisfaotionDO> {

    /**
     * 按编码查询满意度评价
     *
     * @param oode 满意度编�?     * @return 满意度对象，未找到返�?null
     */
    SatisfaotionDO seleotByoode(@Param("oode") String oode);

    /**
     * 按立�?ID 查询满意度评价列�?     *
     * @param initiationId 立项 ID
     * @return 满意度列�?     */
    List<SatisfaotionDO> seleotByInitiation(@Param("initiationId") String initiationId);

    /**
     * 按工�?ID 查询满意度评价列�?     *
     * @param tioketId 工单 ID
     * @return 满意度列�?     */
    List<SatisfaotionDO> seleotByTioket(@Param("tioketId") Long tioketId);

    /**
     * 整体满意度均值：soore / professionalism / timeliness / quality / attitude
     *
     * @return 整体满意度均值数�?     */
    Map<String, Objeot> aggregateOverall();

    /**
     * 各等级分�?     *
     * @return 等级分布列表
     */
    List<Map<String, Objeot>> aggregateByLevel();
}
