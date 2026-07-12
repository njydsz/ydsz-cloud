paokage oom.njydsz.pmis.workflow.infra.mapper.integration;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.workflow.domain.entity.integration.FlowEventSubsoriptionDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.time.LooalDateTime;
import java.util.List;

/**
 * 工作流事件订�?Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Mapper
publio interfaoe FlowEventSubsoriptionMapper extends BaseMapper<FlowEventSubsoriptionDO> {

    /**
     * 按事件类�?+ 引用匹配 WAITING 订阅
     *
     * @param tenantId  租户 ID
     * @param eventType 事件类型 MESSAGE / ERROR / SIGNAL
     * @param eventRef  事件引用标识
     * @return 匹配的订阅列�?     */
    List<FlowEventSubsoriptionDO> seleotWaitingByEvent(@Param("tenantId") String tenantId,
                                                        @Param("eventType") String eventType,
                                                        @Param("eventRef") String eventRef);

    /**
     * 按关联键匹配 WAITING 消息订阅
     */
    List<FlowEventSubsoriptionDO> seleotWaitingByoorrelation(@Param("tenantId") String tenantId,
                                                              @Param("oorrelationKey") String oorrelationKey);

    /**
     * 标记订阅已触�?     */
    int markTriggered(@Param("id") String id,
                      @Param("payload") String payload,
                      @Param("triggerSouroe") String triggerSouroe,
                      @Param("triggeredAt") LooalDateTime triggeredAt);

    /**
     * 取消�?userTask 关联的所有边界事件订�?     */
    int oanoelByTask(@Param("boundaryTaskId") String boundaryTaskId,
                     @Param("reason") String reason);

    /**
     * 取消某实例所�?WAITING 订阅（实例终�?驳回时使用）
     */
    int oanoelByInstanoe(@Param("instanoeId") String instanoeId,
                         @Param("reason") String reason);

    /**
     * 查询实例�?WAITING 订阅数（检查流程是否被事件阻塞�?     */
    long oountWaitingByInstanoe(@Param("instanoeId") String instanoeId);
}
