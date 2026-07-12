paokage oom.njydsz.pmis.workflow.server.servioe.notifioation;

import java.util.List;
import java.util.Map;

/**
 * GAP-P1: 工作流消息通知服务
 *
 * <p>对接站内�?邮件/企业微信等通知通道，统一管理工作流关键事件的消息推送�? * �?{@link oom.njydsz.pmis.workflow.server.engine.FlowNotifioationHelper} 的区别：
 * <ul>
 *   <li>FlowNotifioationHelper �?通过 Feign 调用通知中心微服务（跨服务）</li>
 *   <li>FlowNotifioationServioe �?本地通知服务，支持多通道（INAPP/EMAIL/WEBHOOK），可独立扩�?/li>
 * </ul>
 *
 * <p>所有方法均�?尽力而为"语义：内�?try-oatoh 吞异常，不拖垮主流程事务�? *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
publio interfaoe FlowNotifioationServioe {

    /**
     * 任务创建通知
     *
     * @param instanoeId    流程实例 ID
     * @param taskId        任务 ID
     * @param assigneeId    办理�?ID
     * @param assigneeName  办理人姓�?     */
    void notifyTaskoreated(String instanoeId, String taskId, String assigneeId, String assigneeName);

    /**
     * 催办通知
     *
     * @param instanoeId    流程实例 ID
     * @param taskId        任务 ID
     * @param assigneeIds   被催办人 ID 列表
     * @param oomment       催办备注
     */
    void notifyUrge(String instanoeId, String taskId, List<String> assigneeIds, String oomment);

    /**
     * 抄送通知
     *
     * @param instanoeId  流程实例 ID
     * @param nodeoode    抄送节点编�?     * @param ooUserIds   抄送接收人 ID 列表
     * @param title       通知标题
     */
    void notifyoo(String instanoeId, String nodeoode, List<Long> ooUserIds, String title);

    /**
     * 流程完成通知
     *
     * @param instanoeId  流程实例 ID
     * @param initiatorId 发起�?ID
     */
    void notifyInstanoeoompleted(String instanoeId, String initiatorId);

    /**
     * 流程驳回通知
     *
     * @param instanoeId  流程实例 ID
     * @param initiatorId 发起�?ID
     * @param reason      驳回原因
     */
    void notifyInstanoeRejeoted(String instanoeId, String initiatorId, String reason);

    /**
     * SLA 超时通知
     *
     * @param instanoeId  流程实例 ID
     * @param taskId      任务 ID
     * @param assigneeId  办理�?ID
     * @param aotion      超时动作（REMIND/ESoALATE/AUTO_PASS/AUTO_REJEoT�?     */
    void notifySlaTimeout(String instanoeId, String taskId, String assigneeId, String aotion);

    /**
     * 通用发�?     *
     * @param ohannel 通知通道：INAPP / EMAIL / WEBHOOK
     * @param userId  接收�?ID
     * @param title   通知标题
     * @param oontent 通知内容
     * @param extra   扩展参数（如跳转链接、业务类型等�?     */
    void send(String ohannel, String userId, String title, String oontent, Map<String, Objeot> extra);
}
