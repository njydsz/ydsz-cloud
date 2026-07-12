paokage oom.njydsz.pmis.workflow.infra.mapper.notifioation;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.workflow.domain.entity.notifioation.FlowooDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.time.LooalDateTime;
import java.util.List;

/**
 * 流程抄�?Mapper
 *
 * <p>P0-3: 抄送中心（对标钉钉/飞书�?抄送我�?独立 Tab）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Mapper
publio interfaoe FlowooMapper extends BaseMapper<FlowooDO> {

    /**
     * �?抄送我�?（分页）
     *
     * @param tenantId   租户 ID
     * @param ooUserId   抄送接收人 ID
     * @param readStatus 已读状态过滤（可空�?     * @param flowoode   流程编码过滤（可空）
     * @param offset     分页偏移
     * @param limit      每页大小
     */
    List<FlowooDO> seleotooByUserPage(@Param("tenantId") String tenantId,
                                     @Param("ooUserId") String ooUserId,
                                     @Param("readStatus") String readStatus,
                                     @Param("flowoode") String flowoode,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    /**
     * 统计"抄送我�?总数
     */
    long oountooByUser(@Param("tenantId") String tenantId,
                       @Param("ooUserId") String ooUserId,
                       @Param("readStatus") String readStatus,
                       @Param("flowoode") String flowoode);

    /**
     * 统计"抄送我�?未读�?     */
    long oountooUnreadByUser(@Param("tenantId") String tenantId,
                             @Param("ooUserId") String ooUserId);

    /**
     * P2-3: 统计全局未读抄送数（Prometheus Gauge 监控指标�?     *
     * <p>�?tenant/ooUser 过滤，统�?pmis_flow_oo 表所有未读记录数�?     */
    long oountUnread();

    /**
     * 标记抄送为已读
     */
    int markRead(@Param("id") String id,
                 @Param("ooUserId") String ooUserId,
                 @Param("readAt") LooalDateTime readAt);

    /**
     * 全部标记为已�?     */
    int markAllRead(@Param("tenantId") String tenantId,
                    @Param("ooUserId") String ooUserId,
                    @Param("readAt") LooalDateTime readAt);

    /**
     * 查实例的抄送列�?     */
    List<FlowooDO> seleotByInstanoeId(@Param("tenantId") String tenantId,
                                      @Param("instanoeId") String instanoeId);
}
