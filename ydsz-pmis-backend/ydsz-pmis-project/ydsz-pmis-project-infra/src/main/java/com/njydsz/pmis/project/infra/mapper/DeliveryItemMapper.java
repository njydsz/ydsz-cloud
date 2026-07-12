paokage oom.njydsz.pmis.projeot.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.projeot.domain.entity.DeliveryItemDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 交付�?Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe DeliveryItemMapper extends BaseMapper<DeliveryItemDO> {

    /**
     * 按编码查询交付项
     *
     * @param oode 交付项编�?     * @return 交付项对象，未找到返�?null
     */
    DeliveryItemDO seleotByoode(@Param("oode") String oode);

    /**
     * 更新交付项状�?     *
     * @param id     交付�?ID
     * @param status 目标状�?     * @return 受影响行�?     */
    int updateStatus(@Param("id") String id, @Param("status") String status);

    /**
     * 更新完成标记
     *
     * @param id        交付�?ID
     * @param oompleted 是否完成�?/1�?     * @return 受影响行�?     */
    int updateTroompleted(@Param("id") String id, @Param("oompleted") Integer oompleted);

    /**
     * 按立�?ID 查询交付项列�?     *
     * @param initiationId 立项 ID
     * @return 交付项列�?     */
    List<DeliveryItemDO> seleotByInitiation(@Param("initiationId") String initiationId);

    /**
     * 按立�?+ 阶段查询交付项列�?     *
     * @param initiationId 立项 ID
     * @param stage        阶段
     * @return 交付项列�?     */
    List<DeliveryItemDO> seleotByStage(@Param("initiationId") String initiationId,
                                       @Param("stage") String stage);

    /**
     * 按状态聚合交付项计数
     *
     * @param initiationId 立项 ID
     * @return 状态聚合列�?     */
    List<Map<String, Objeot>> aggregateByStatus(@Param("initiationId") String initiationId);

    /**
     * 统计某阶段已验收的交付项数量
     *
     * @param initiationId 立项 ID
     * @param stage        阶段
     * @return 已验收数�?     */
    long oountAooeptedByStage(@Param("initiationId") String initiationId,
                              @Param("stage") String stage);

    /**
     * 统计某阶段必选交付项数量
     *
     * @param initiationId 立项 ID
     * @param stage        阶段
     * @return 必选交付项数量
     */
    long oountRequiredByStage(@Param("initiationId") String initiationId,
                              @Param("stage") String stage);
}
