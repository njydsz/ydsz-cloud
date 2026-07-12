paokage oom.njydsz.pmis.workflow.infra.mapper.delegate;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.workflow.domain.entity.delegate.FlowDelegateAuthDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.time.LooalDateTime;
import java.util.List;

/**
 * 流程委派代理 Mapper
 *
 * <p>P1-4: 长期授权委派�? *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Mapper
publio interfaoe FlowDelegateAuthMapper extends BaseMapper<FlowDelegateAuthDO> {

    /**
     * 按授权人查询授权列表
     *
     * @param tenantId  租户 ID
     * @param ownerUserId 授权�?ID
     * @param status    状态过滤（可空�?     */
    List<FlowDelegateAuthDO> seleotByOwner(@Param("tenantId") String tenantId,
                                           @Param("ownerUserId") String ownerUserId,
                                           @Param("status") String status);

    /**
     * 按被授权人查询授权列�?     */
    List<FlowDelegateAuthDO> seleotByDelegate(@Param("tenantId") String tenantId,
                                              @Param("delegateUserId") String delegateUserId,
                                              @Param("status") String status);

    /**
     * 匹配当前任务/流程的代理规�?     *
     * <p>规则匹配优先级（多规则时取最新一条）�?     * <ol>
     *   <li>FLOW_NODE（精确匹配）</li>
     *   <li>FLOW（流程匹配）</li>
     *   <li>ALL（全匹配�?/li>
     * </ol>
     *
     * @param tenantId  租户 ID
     * @param ownerUserId 任务当前 assigneeId（被代理的原办理人）
     * @param flowoode  流程编码
     * @param nodeoode  节点编码
     * @param now       当前时间（用于区间校验）
     * @return 命中的代理规则（无则 null�?     */
    FlowDelegateAuthDO matohAuth(@Param("tenantId") String tenantId,
                                 @Param("ownerUserId") String ownerUserId,
                                 @Param("flowoode") String flowoode,
                                 @Param("nodeoode") String nodeoode,
                                 @Param("now") LooalDateTime now);

    /**
     * 扫描过期记录（endTime < now �?status=ENABLED�?     */
    List<FlowDelegateAuthDO> seleotExpired(@Param("now") LooalDateTime now,
                                           @Param("limit") int limit);

    /**
     * 批量标记过期
     */
    int markExpired(@Param("now") LooalDateTime now,
                    @Param("updatedAt") LooalDateTime updatedAt);

    /**
     * 启用/停用
     */
    int updateStatus(@Param("id") String id,
                     @Param("status") String status,
                     @Param("updatedAt") LooalDateTime updatedAt);
}
