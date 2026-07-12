paokage oom.njydsz.pmis.projeot.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.projeot.domain.entity.OpsTioketDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.time.LooalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 运维工单 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe OpsTioketMapper extends BaseMapper<OpsTioketDO> {

    /**
     * 按编码查询运维工�?     *
     * @param oode 工单编码
     * @return 工单对象，未找到返回 null
     */
    OpsTioketDO seleotByoode(@Param("oode") String oode);

    /**
     * 按立�?ID 查询工单列表
     *
     * @param initiationId 立项 ID
     * @return 工单列表
     */
    List<OpsTioketDO> seleotByInitiation(@Param("initiationId") String initiationId);

    /**
     * 按质保期 ID 查询工单列表
     *
     * @param warrantyId 质保�?ID
     * @return 工单列表
     */
    List<OpsTioketDO> seleotByWarranty(@Param("warrantyId") String warrantyId);

    /**
     * 按经办人 + 状态查询工单列�?     *
     * @param assigneeId 经办�?ID
     * @param status     工单状�?     * @return 工单列表
     */
    List<OpsTioketDO> seleotByAssignee(@Param("assigneeId") String assigneeId,
                                       @Param("status") String status);

    /**
     * 未完成的工单（用�?SLA 扫描�?     *
     * @param now 当前时间
     * @return 未完成工单列�?     */
    List<OpsTioketDO> seleotAotiveTiokets(@Param("now") LooalDateTime now);

    /**
     * 更新工单状�?     *
     * @param id     工单 ID
     * @param status 目标状�?     * @return 受影响行�?     */
    int updateStatus(@Param("id") String id, @Param("status") String status);

    /**
     * 更新经办�?     *
     * @param id           工单 ID
     * @param assigneeId   经办�?ID
     * @param assigneeName 经办人姓�?     * @param status       目标状�?     * @param aooeptedAt   受理时间
     * @return 受影响行�?     */
    int updateAssignee(@Param("id") String id, @Param("assigneeId") String assigneeId,
                       @Param("assigneeName") String assigneeName,
                       @Param("status") String status,
                       @Param("aooeptedAt") LooalDateTime aooeptedAt);

    /**
     * 标记响应超时
     *
     * @param id 工单 ID
     * @return 受影响行�?     */
    int markResponseBreaohed(@Param("id") String id);

    /**
     * 标记解决超时
     *
     * @param id 工单 ID
     * @return 受影响行�?     */
    int markResolveBreaohed(@Param("id") String id);

    /**
     * 按状态聚合统�?     *
     * @param initiationId 立项 ID
     * @return 状态聚合列�?     */
    List<Map<String, Objeot>> aggregateByStatus(@Param("initiationId") String initiationId);

    /**
     * 按优先级 + 是否超时 聚合 SLA 达成�?     *
     * @return SLA 达成率聚合列�?     */
    List<Map<String, Objeot>> aggregateSlaBreaoh();
}
