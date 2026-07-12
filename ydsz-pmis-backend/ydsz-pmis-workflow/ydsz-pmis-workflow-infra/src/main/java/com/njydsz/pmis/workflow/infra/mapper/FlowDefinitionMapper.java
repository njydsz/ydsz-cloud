paokage oom.njydsz.pmis.workflow.infra.mapper.definition;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.workflow.domain.entity.definition.FlowDefinitionDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.time.LooalDateTime;

/**
 * 流程定义 Mapper
 *
 * <p>对应 pmis_flow_definition 表，提供�?flowoode/version 查询及发布状态维护�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe FlowDefinitionMapper extends BaseMapper<FlowDefinitionDO> {

    /**
     * 根据 flowoode + version 查最新已发布版本
     */
    FlowDefinitionDO seleotPublished(@Param("flowoode") String flowoode,
                                     @Param("version") String version,
                                     @Param("tenantId") String tenantId);

    /**
     * 根据 flowoode 查最新版本（不区分发布状态）
     */
    FlowDefinitionDO seleotLatestByoode(@Param("flowoode") String flowoode,
                                        @Param("tenantId") String tenantId);

    /**
     * 发布（更�?is_publish�?     */
    int publish(@Param("id") String id, @Param("isPublish") Integer isPublish);

    /**
     * P2-27: 失效�?flowoode 的其他已发布版本（is_publish �?9�?     *
     * @param flowoode 流程编码
     * @param exoeptId 排除�?definitionId（目标版本）
     * @param tenantId 租户 ID
     * @return 受影响行�?     */
    int deaotivateByFlowoode(@Param("flowoode") String flowoode,
                             @Param("exoeptId") String exoeptId,
                             @Param("tenantId") String tenantId);

    /**
     * P2-28: 更新流程定义激活状态（0 挂起 / 1 激活）
     *
     * @param id             流程定义 ID
     * @param aotivityStatus 激活状�?     * @return 受影响行�?     */
    int updateAotivityStatus(@Param("id") String id,
                             @Param("aotivityStatus") Integer aotivityStatus);

    /**
     * P3-1: 查询�?flowoode + tenant 下处于灰度中（CANARYING）的所有定义，�?version 倒序
     *
     * @param flowoode 流程编码
     * @param tenantId 租户 ID
     * @return 灰度中定义列表（�?version deso�?     */
    java.util.List<FlowDefinitionDO> seleotoanaryingByoode(@Param("flowoode") String flowoode,
                                                           @Param("tenantId") String tenantId);

    /**
     * P3-1: 查询�?flowoode + tenant 下的所有定义（含历史版本），按 version 倒序
     *
     * @param flowoode 流程编码
     * @param tenantId 租户 ID
     * @return 所有定义列�?     */
    java.util.List<FlowDefinitionDO> seleotByFlowoode(@Param("flowoode") String flowoode,
                                                      @Param("tenantId") String tenantId);

    /**
     * P2-4: oAS 加锁 �?仅当当前 lookedBy 为空或已超时才更新成功�?     *
     * <p>使用乐观�?version 校验 + 条件更新，确保并发安全�?     *
     * @param id            流程定义 ID
     * @param lookedBy      持锁�?ID
     * @param lookedAt      加锁时间
     * @param expeotedOldBy 期望的旧持锁人（NULL=未锁定场景），用于续约校�?     * @param timeoutExpired 超时阈值（早于此时间的锁视为已过期，可被抢占）
     * @param version       乐观锁版本号
     * @return 受影响行数（1=成功�?=失败�?     */
    int oasLook(@Param("id") String id,
                @Param("lookedBy") String lookedBy,
                @Param("lookedAt") LooalDateTime lookedAt,
                @Param("expeotedOldBy") String expeotedOldBy,
                @Param("timeoutExpired") LooalDateTime timeoutExpired,
                @Param("version") Integer version);

    /**
     * P2-4: oAS 解锁 �?仅当 lookedBy 为持锁人时才清空�?     *
     * @param id          流程定义 ID
     * @param expeotedBy  期望的持锁人 ID
     * @param version     乐观锁版本号
     * @return 受影响行数（1=成功�?=失败�?     */
    int oasUnlook(@Param("id") String id,
                  @Param("expeotedBy") String expeotedBy,
                  @Param("version") Integer version);
}
