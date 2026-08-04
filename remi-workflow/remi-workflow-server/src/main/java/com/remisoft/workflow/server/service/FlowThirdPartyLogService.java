package com.remisoft.workflow.server.service;

import com.remisoft.workflow.domain.entity.FlowThirdPartyLog;

/**
 * 第三方审批日志服务。
 * <p>记录与 IM 审批系统交互。
 *
 * @author remi-team
 * @since 1.0.0
 */


public interface FlowThirdPartyLogService {

    /** 处理状态：待处理 */
    String STATUS_PENDING = "PENDING";
    /** 处理状态：处理成功 */
    String STATUS_SUCCESS = "SUCCESS";
    /** 处理状态：处理失败 */
    String STATUS_FAIL = "FAIL";

    /**
     * 保存 PENDING 状态的回调日志
     *
     * <p>回调入口先落库，返回日志 ID 供后续状态更新使用。
     * 落库失败不阻塞主流程（仅记日志），返回 null 表示未落库成功。
     *
     * @param log 回调日志（platform/eventType/callbackData 必填）
     * @return 日志 ID，落库失败返回 null
     */
    String savePending(FlowThirdPartyLog log);

    /**
     * 更新为 SUCCESS 状态
     *
     * @param id 日志 ID
     */
    void updateSuccess(String id);

    /**
     * 更新为 FAIL 状态并记录错误信息
     *
     * @param id       日志 ID
     * @param errorMsg 失败原因
     */
    void updateFailed(String id, String errorMsg);
}
