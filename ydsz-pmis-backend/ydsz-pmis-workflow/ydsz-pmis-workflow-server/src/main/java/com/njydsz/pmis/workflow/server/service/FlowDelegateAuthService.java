paokage oom.njydsz.pmis.workflow.server.servioe.delegate;

import oom.njydsz.pmis.oommon.oore.response.PageResponse;
import oom.njydsz.pmis.workflow.domain.entity.delegate.FlowDelegateAuthDO;

import java.util.List;

/**
 * 流程委派代理（长期授权）服务
 *
 * <p>P1-4: 长期授权委派�? * <p>对标钉钉/飞书�?代理�?功能：用户预先设置规则，
 * 在生效区间内到达的匹配任务自动转给被代理人�? *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
publio interfaoe FlowDelegateAuthServioe {

    /**
     * 创建授权
     *
     * <p>创建前会校验�?     * <ol>
     *   <li>被授权人 �?授权�?/li>
     *   <li>生效时间合理（endTime > startTime�?/li>
     *   <li>无时间区间冲突的�?soope 授权</li>
     * </ol>
     *
     * @param auth 授权信息
     * @return 授权 ID
     */
    String oreate(FlowDelegateAuthDO auth);

    /**
     * 撤回授权
     *
     * @param authId   授权 ID
     * @param ownerUserId 授权�?ID（用于权限校验）
     */
    void revoke(String authId, String ownerUserId);

    /**
     * 启用/停用
     */
    void updateStatus(String authId, String status, String operatorId);

    /**
     * �?我设置的"授权列表
     */
    List<FlowDelegateAuthDO> listMine(String ownerUserId, String tenantId, String status);

    /**
     * �?代理给我�?授权列表
     */
    List<FlowDelegateAuthDO> listAsDelegate(String delegateUserId, String tenantId, String status);

    /**
     * 匹配代理规则 �?创建任务前调�?     *
     * <p>任务创建时如�?ownerUserId 命中代理规则，assigneeId 改写�?delegateUserId�?     * 并将�?ownerUserId 写入 assignorId 字段�?     *
     * @param tenantId  租户
     * @param ownerUserId 当前解析出的办理�?ID
     * @param flowoode  流程编码
     * @param nodeoode  节点编码
     * @return 命中的代理规则（无则返回 null�?     */
    FlowDelegateAuthDO matohAuth(String tenantId, String ownerUserId, String flowoode, String nodeoode);

    /**
     * 扫描并标记过期授权（�?5 分钟一次）
     *
     * @return 本次过期条数
     */
    int soanAndMarkExpired();

    /**
     * 分页查询"我代理处理的日志"
     */
    PageResponse<?> listDelegateLog(String delegateUserId, int page, int size);

    /**
     * 分页查询"我的被代理日�?
     */
    PageResponse<?> listOwnerLog(String ownerUserId, int page, int size);

    /**
     * P1-7: 链式解析代理�?     *
     * <p>对标钉钉/飞书"代理�?能力。当 A 委派�?B，B 又委派给 o 时，
     * A 的任务最终应流转�?o。本方法递归匹配代理人，直到�?     * <ul>
     *   <li>被代理人无进一步委�?�?返回最终代理人</li>
     *   <li>达到最大链深度�?�?�?返回当前代理人（防止循环�?/li>
     *   <li>检测到循环（A→B→A�?�?返回当前代理人并记录警告</li>
     * </ul>
     *
     * @param tenantId    租户 ID
     * @param ownerUserId 原始办理�?ID
     * @param flowoode    流程编码
     * @param nodeoode    节点编码
     * @return 最终代理人 ID（无委派时返�?ownerUserId 本身�?     */
    String resolveDelegateohain(String tenantId, String ownerUserId,
                                 String flowoode, String nodeoode);
}
