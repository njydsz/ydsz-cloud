package com.njydsz.pmis.workflow.flow.service;

import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.workflow.flow.entity.FlowDelegateAuthDO;

import java.util.List;

/**
 * 流程委派代理（长期授权）服务
 *
 * <p>P1-4: 长期授权委派。
 * <p>对标钉钉/飞书的"代理人"功能：用户预先设置规则，
 * 在生效区间内到达的匹配任务自动转给被代理人。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
public interface FlowDelegateAuthService {

    /**
     * 创建授权
     *
     * <p>创建前会校验：
     * <ol>
     *   <li>被授权人 ≠ 授权人</li>
     *   <li>生效时间合理（endTime > startTime）</li>
     *   <li>无时间区间冲突的同 scope 授权</li>
     * </ol>
     *
     * @param auth 授权信息
     * @return 授权 ID
     */
    Long create(FlowDelegateAuthDO auth);

    /**
     * 撤回授权
     *
     * @param authId   授权 ID
     * @param ownerUserId 授权人 ID（用于权限校验）
     */
    void revoke(Long authId, Long ownerUserId);

    /**
     * 启用/停用
     */
    void updateStatus(Long authId, String status, Long operatorId);

    /**
     * 查"我设置的"授权列表
     */
    List<FlowDelegateAuthDO> listMine(Long ownerUserId, Long tenantId, String status);

    /**
     * 查"代理给我的"授权列表
     */
    List<FlowDelegateAuthDO> listAsDelegate(Long delegateUserId, Long tenantId, String status);

    /**
     * 匹配代理规则 — 创建任务前调用
     *
     * <p>任务创建时如果 ownerUserId 命中代理规则，assigneeId 改写为 delegateUserId，
     * 并将原 ownerUserId 写入 assignorId 字段。
     *
     * @param tenantId  租户
     * @param ownerUserId 当前解析出的办理人 ID
     * @param flowCode  流程编码
     * @param nodeCode  节点编码
     * @return 命中的代理规则（无则返回 null）
     */
    FlowDelegateAuthDO matchAuth(Long tenantId, Long ownerUserId, String flowCode, String nodeCode);

    /**
     * 扫描并标记过期授权（每 5 分钟一次）
     *
     * @return 本次过期条数
     */
    int scanAndMarkExpired();

    /**
     * 分页查询"我代理处理的日志"
     */
    PageResult<?> listDelegateLog(Long delegateUserId, int page, int size);

    /**
     * 分页查询"我的被代理日志"
     */
    PageResult<?> listOwnerLog(Long ownerUserId, int page, int size);
}
