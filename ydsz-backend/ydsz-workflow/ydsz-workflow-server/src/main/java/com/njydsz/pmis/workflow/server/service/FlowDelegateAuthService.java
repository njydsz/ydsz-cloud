package com.njydsz.workflow.server.service;

import java.util.List;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.workflow.domain.entity.FlowDelegateAuthDO;

/**
 * 流程委派代理（长期授权）服务
 *
 * <p>P1-4: 长期授权委派。
 * <p>对标钉钉/飞书的"代理人"功能：用户预先设置规则，
 * 在生效区间内到达的匹配任务自动转给被代理人。
 *
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
    String create(FlowDelegateAuthDO auth);

    /**
     * 撤回授权
     *
     * @param authId   授权 ID
     * @param ownerUserId 授权人 ID（用于权限校验）
     */
    void revoke(String authId, String ownerUserId);

    /**
     * 启用/停用
     */
    void updateStatus(String authId, String status, String operatorId);

    /**
     * 查"我设置的"授权列表
     */
    List<FlowDelegateAuthDO> listMine(String ownerUserId, String tenantId, String status);

    /**
     * 查"代理给我的"授权列表
     */
    List<FlowDelegateAuthDO> listAsDelegate(String delegateUserId, String tenantId, String status);

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
    FlowDelegateAuthDO matchAuth(String tenantId, String ownerUserId, String flowCode, String nodeCode);

    /**
     * 扫描并标记过期授权（每 5 分钟一次）
     *
     * @return 本次过期条数
     */
    int scanAndMarkExpired();

    /**
     * 分页查询"我代理处理的日志"
     */
    PageResponse<?> listDelegateLog(String delegateUserId, int page, int size);

    /**
     * 分页查询"我的被代理日志"
     */
    PageResponse<?> listOwnerLog(String ownerUserId, int page, int size);

    /**
     * P1-7: 链式解析代理人
     *
     * <p>对标钉钉/飞书"代理链"能力。当 A 委派给 B，B 又委派给 C 时，
     * A 的任务最终应流转到 C。本方法递归匹配代理人，直到：
     * <ul>
     *   <li>被代理人无进一步委派 → 返回最终代理人</li>
     *   <li>达到最大链深度（5） → 返回当前代理人（防止循环）</li>
     *   <li>检测到循环（A→B→A） → 返回当前代理人并记录警告</li>
     * </ul>
     *
     * @param tenantId    租户 ID
     * @param ownerUserId 原始办理人 ID
     * @param flowCode    流程编码
     * @param nodeCode    节点编码
     * @return 最终代理人 ID（无委派时返回 ownerUserId 本身）
     */
    String resolveDelegateChain(String tenantId, String ownerUserId,
                                 String flowCode, String nodeCode);
}
