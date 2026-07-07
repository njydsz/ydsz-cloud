package com.njydsz.pmis.project.service;

import com.njydsz.pmis.project.dto.AlertDispatchDTO;
import com.njydsz.pmis.project.entity.AlertDispatchDO;

import java.util.List;
import java.util.Map;

/**
 * 预警分级推送服务（P4-2）
 *
 * <p>支持黄/红色预警分别推送到不同角色：
 * <ul>
 *   <li>YELLOW → PM + PMO</li>
 *   <li>RED    → PMO + GM + CFO</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface AlertDispatchService {

    /**
     * 提交一条预警，自动根据 level 解析目标角色
     *
     * @param dto 预警请求
     * @return 预警记录 ID
     */
    String submit(AlertDispatchDTO dto);

    /**
     * 立即发送（占位：与通知中心解耦，本地仅标记 SENT）
     *
     * @param id 预警 ID
     * @return true 表示发送成功
     */
    boolean dispatchNow(String id);

    /**
     * 扫描并重试 FAILED 的预警
     *
     * @param maxRetry 最大重试次数
     * @return 成功重试的条数
     */
    int retryFailed(int maxRetry);

    /**
     * 解析预警等级对应的角色集合（黄/红 → 角色列表）
     *
     * @param level 预警等级（YELLOW/RED）
     * @return 目标角色编码列表
     */
    List<String> resolveTargetRoles(String level);

    /**
     * 按等级 + 状态查询
     *
     * @param level  预警等级
     * @param status 预警状态
     * @return 预警记录列表
     */
    List<AlertDispatchDO> listByLevelAndStatus(String level, String status);

    /**
     * 统计：按类型 × 等级
     *
     * @param tenantId 租户 ID
     * @return 聚合统计结果
     */
    List<Map<String, Object>> aggregateByTypeAndLevel(String tenantId);

    /**
     * 取消预警
     *
     * @param id     预警 ID
     * @param reason 取消原因
     */
    void cancel(String id, String reason);
}
