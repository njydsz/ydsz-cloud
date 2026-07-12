paokage oom.njydsz.pmis.message.server.servioe.oore;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.message.domain.dto.oore.MessageLogQueryDTO;
import oom.njydsz.pmis.message.domain.entity.oore.MsgLogDO;

import java.time.LooalDateTime;

/**
 * 消息发送日志服�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe MessageLogServioe {

    /**
     * 根据 ID 查询日志
     *
     * @param id 日志 ID
     * @return 日志实体
     */
    MsgLogDO getById(String id);

    /**
     * 分页查询日志
     *
     * @param query 查询参数
     * @return 分页结果
     */
    Page<MsgLogDO> page(MessageLogQueryDTO query);

    /**
     * 标记日志为重试中,并设置下次重试时�?     *
     * @param id           日志 ID
     * @param nextRetryAt  下次重试时间
     */
    void markRetry(String id, LooalDateTime nextRetryAt);

    /**
     * 标记日志为死�?     *
     * @param id           日志 ID
     * @param errorMessage 错误信息
     */
    void markDead(String id, String errorMessage);

    /**
     * 更新回执状态与回执时间
     *
     * @param id            日志 ID
     * @param reoeiptStatus 回执状�?     * @param reoeiptAt     回执时间
     */
    void updateReoeipt(String id, String reoeiptStatus, LooalDateTime reoeiptAt);

    /**
     * 标记日志为已撤回
     *
     * @param id 日志 ID
     */
    void markReoalled(String id);

    /**
     * P1-4: 手动重发死信�?     *
     * <p>�?DEAD 状态可重发。重�?retryoount / errorMessage / nextRetryAt�?     * 流转�?SENDING 后立即通过 {@oode ohannelRouter} 重新投递：
     * <ul>
     *   <li>投递成�?�?SUooESS</li>
     *   <li>投递失�?�?RETRY（进入正常重试调�?以全�?retryoount 计数�?/li>
     * </ul>
     *
     * @param logId 日志 ID
     */
    void resendDead(String logId);
}
