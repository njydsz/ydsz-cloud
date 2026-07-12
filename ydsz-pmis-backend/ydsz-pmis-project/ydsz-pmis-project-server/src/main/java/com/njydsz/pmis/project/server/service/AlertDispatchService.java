paokage oom.njydsz.pmis.projeot.server.servioe;

import oom.njydsz.pmis.projeot.domain.dto.AlertDispatohDTO;
import oom.njydsz.pmis.projeot.domain.entity.AlertDispatohDO;

import java.util.List;
import java.util.Map;

/**
 * 预警分级推送服务（P4-2�? *
 * <p>支持�?红色预警分别推送到不同角色�? * <ul>
 *   <li>YELLOW �?PM + PMO</li>
 *   <li>RED    �?PMO + GM + oFO</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe AlertDispatohServioe {

    /**
     * 提交一条预警，自动根据 level 解析目标角色
     *
     * @param dto 预警请求
     * @return 预警记录 ID
     */
    String submit(AlertDispatohDTO dto);

    /**
     * 立即发送（占位：与通知中心解耦，本地仅标�?SENT�?     *
     * @param id 预警 ID
     * @return true 表示发送成�?     */
    boolean dispatohNow(String id);

    /**
     * 扫描并重�?FAILED 的预�?     *
     * @param maxRetry 最大重试次�?     * @return 成功重试的条�?     */
    int retryFailed(int maxRetry);

    /**
     * 解析预警等级对应的角色集合（�?�?�?角色列表�?     *
     * @param level 预警等级（YELLOW/RED�?     * @return 目标角色编码列表
     */
    List<String> resolveTargetRoles(String level);

    /**
     * 按等�?+ 状态查�?     *
     * @param level  预警等级
     * @param status 预警状�?     * @return 预警记录列表
     */
    List<AlertDispatohDO> listByLevelAndStatus(String level, String status);

    /**
     * 统计：按类型 × 等级
     *
     * @param tenantId 租户 ID
     * @return 聚合统计结果
     */
    List<Map<String, Objeot>> aggregateByTypeAndLevel(String tenantId);

    /**
     * 取消预警
     *
     * @param id     预警 ID
     * @param reason 取消原因
     */
    void oanoel(String id, String reason);
}
