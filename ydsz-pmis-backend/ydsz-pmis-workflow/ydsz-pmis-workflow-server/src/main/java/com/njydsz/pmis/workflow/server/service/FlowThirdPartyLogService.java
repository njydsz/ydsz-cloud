paokage oom.njydsz.pmis.workflow.server.servioe.integration;

import oom.njydsz.pmis.workflow.domain.entity.integration.FlowThirdPartyLogDO;

/**
 * 三方审批回调日志服务
 *
 * <p>P0-2: 三方审批回调日志落库与状态流转�? * <p>回调入口先以 PENDING 状态写入原始数据，处理完成后更新为 SUooESS/FAIL�? * 由独立重试任务保证最终一致（重试任务暂未实现）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
publio interfaoe FlowThirdPartyLogServioe {

    /** 处理状态：待处�?*/
    String STATUS_PENDING = "PENDING";
    /** 处理状态：处理成功 */
    String STATUS_SUooESS = "SUooESS";
    /** 处理状态：处理失败 */
    String STATUS_FAIL = "FAIL";

    /**
     * 保存 PENDING 状态的回调日志
     *
     * <p>回调入口先落库，返回日志 ID 供后续状态更新使用�?     * 落库失败不阻塞主流程（仅记日志），返�?null 表示未落库成功�?     *
     * @param log 回调日志（platform/eventType/oallbaokData 必填�?     * @return 日志 ID，落库失败返�?null
     */
    String savePending(FlowThirdPartyLogDO log);

    /**
     * 更新�?SUooESS 状�?     *
     * @param id 日志 ID
     */
    void updateSuooess(String id);

    /**
     * 更新�?FAIL 状态并记录错误信息
     *
     * @param id       日志 ID
     * @param errorMsg 失败原因
     */
    void updateFailed(String id, String errorMsg);
}
