package com.njydsz.pmis.sales.server.notify;

import java.util.List;

import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.notify.core.AsyncNotifyService;
import com.njydsz.pmis.common.notify.enums.NotifyChannel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 销售模块通知助手
 *
 * <p>基于 common-notify 的 {@link AsyncNotifyService} 实现销售相关通知发送，
 * 支持多渠道（站内信/邮件/企业微信/钉钉/飞书）异步投递，自带重试和指数退避。
 *
 * <p><b>通知场景：</b>
 * <ul>
 *   <li>商机阶段变更 — 通知销售经理商机进入新阶段</li>
 *   <li>合同签订提醒 — 通知相关方合同已签订</li>
 *   <li>合同风险预警 — 合同风险评估触发预警时通知负责人</li>
 *   <li>商机跟进提醒 — 定时提醒销售人员进行商机跟进</li>
 * </ul>
 *
 * <p><b>设计说明：</b>
 * <ul>
 *   <li>使用 {@link AsyncNotifyService#sendAsync} 异步发送，不阻塞业务主流程</li>
 *   <li>发送失败自动重试 3 次（由 AsyncNotifyService 内部实现）</li>
 *   <li>重试失败的消息进入持久化重试队列，由定时任务补偿</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalesNotifyHelper {

    private final AsyncNotifyService asyncNotifyService;

    /**
     * 商机阶段变更通知
     *
     * @param receiverIds   接收人 ID 列表
     * @param opportunityName 商机名称
     * @param fromStage     原阶段
     * @param toStage       新阶段
     */
    public void notifyOpportunityStageChanged(List<String> receiverIds, String opportunityName,
                                               String fromStage, String toStage) {
        if (receiverIds == null || receiverIds.isEmpty()) {
            return;
        }
        String title = "商机阶段变更";
        String content = String.format("商机: %s\n阶段变更: %s → %s\n请关注进展",
                opportunityName, fromStage, toStage);
        sendToReceivers(receiverIds, title, content);
    }

    /**
     * 合同签订通知
     *
     * @param receiverIds  接收人 ID 列表
     * @param contractNo   合同编号
     * @param contractName 合同名称
     * @param counterparty 合同对方
     */
    public void notifyContractSigned(List<String> receiverIds, String contractNo,
                                      String contractName, String counterparty) {
        if (receiverIds == null || receiverIds.isEmpty()) {
            return;
        }
        String title = "合同已签订";
        String content = String.format("合同编号: %s\n名称: %s\n对方: %s",
                contractNo, contractName, counterparty);
        sendToReceivers(receiverIds, title, content);
    }

    /**
     * 合同风险预警
     *
     * @param receiverIds  接收人 ID 列表
     * @param contractNo   合同编号
     * @param riskLevel    风险等级
     * @param riskDesc     风险描述
     */
    public void notifyContractRiskAlert(List<String> receiverIds, String contractNo,
                                         String riskLevel, String riskDesc) {
        if (receiverIds == null || receiverIds.isEmpty()) {
            return;
        }
        String title = "合同风险预警: " + riskLevel;
        String content = String.format("合同编号: %s\n风险等级: %s\n风险描述: %s\n请及时评估处理",
                contractNo, riskLevel, riskDesc);
        sendToReceivers(receiverIds, title, content);
    }

    /**
     * 商机跟进提醒
     *
     * @param receiverIds   接收人 ID 列表
     * @param opportunityName 商机名称
     * @param lastFollowDays 距上次跟进天数
     */
    public void notifyOpportunityFollowUp(List<String> receiverIds, String opportunityName,
                                           int lastFollowDays) {
        if (receiverIds == null || receiverIds.isEmpty()) {
            return;
        }
        String title = "商机跟进提醒";
        String content = String.format("商机: %s\n已 %d 天未跟进\n请及时更新商机状态",
                opportunityName, lastFollowDays);
        sendToReceivers(receiverIds, title, content);
    }

    /**
     * 批量发送通知到多个接收人
     *
     * <p>默认使用站内信渠道，发送失败仅记录日志不影响业务。
     *
     * @param receiverIds 接收人 ID 列表
     * @param title       通知标题
     * @param content     通知内容
     */
    private void sendToReceivers(List<String> receiverIds, String title, String content) {
        for (String receiverId : receiverIds) {
            try {
                asyncNotifyService.sendAsync(NotifyChannel.INSITE, receiverId, title, content)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.warn("[SalesNotify] 通知发送失败: receiver={} err={}",
                                        receiverId, ex.getMessage());
                            } else if (!result.isSuccess()) {
                                log.warn("[SalesNotify] 通知发送失败: receiver={} error={}",
                                        receiverId, result.getErrorMessage());
                            }
                        });
            } catch (Exception e) {
                log.warn("[SalesNotify] 通知发送异常: receiver={} err={}",
                        receiverId, e.getMessage());
            }
        }
    }
}
