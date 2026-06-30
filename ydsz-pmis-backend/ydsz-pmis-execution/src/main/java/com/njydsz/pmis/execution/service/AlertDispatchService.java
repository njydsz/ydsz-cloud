package com.njydsz.pmis.execution.service;

import com.njydsz.pmis.execution.dto.AlertDispatchDTO;
import com.njydsz.pmis.execution.entity.AlertDispatchDO;

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
     */
    Long submit(AlertDispatchDTO dto);

    /**
     * 立即发送（占位：与通知中心解耦，本地仅标记 SENT）
     */
    boolean dispatchNow(Long id);

    /**
     * 扫描并重试 FAILED 的预警
     */
    int retryFailed(int maxRetry);

    /**
     * 解析预警等级对应的角色集合（黄/红 → 角色列表）
     */
    List<String> resolveTargetRoles(String level);

    /**
     * 按等级 + 状态查询
     */
    List<AlertDispatchDO> listByLevelAndStatus(String level, String status);

    /**
     * 统计：按类型 × 等级
     */
    List<Map<String, Object>> aggregateByTypeAndLevel(Long tenantId);

    /**
     * 取消预警
     */
    void cancel(Long id, String reason);
}
