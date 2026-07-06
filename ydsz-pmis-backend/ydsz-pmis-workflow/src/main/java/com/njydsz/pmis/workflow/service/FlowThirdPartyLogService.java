package com.njydsz.pmis.workflow.service;

import com.njydsz.pmis.workflow.entity.FlowThirdPartyLogDO;

/**
 * 三方审批回调日志服务
 *
 * <p>P0-2: 三方审批回调日志落库与状态流转。
 * <p>回调入口先以 PENDING 状态写入原始数据，处理完成后更新为 SUCCESS/FAIL，
 * 由独立重试任务保证最终一致（重试任务暂未实现）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
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
    Long savePending(FlowThirdPartyLogDO log);

    /**
     * 更新为 SUCCESS 状态
     *
     * @param id 日志 ID
     */
    void updateSuccess(Long id);

    /**
     * 更新为 FAIL 状态并记录错误信息
     *
     * @param id       日志 ID
     * @param errorMsg 失败原因
     */
    void updateFailed(Long id, String errorMsg);
}
