package com.njydsz.pmis.finance.server.notify;

import com.njydsz.pmis.common.notify.core.AsyncNotifyService;
import com.njydsz.pmis.common.notify.enums.NotifyChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 财务模块通知助手
 *
 * <p>基于 common-notify 的 {@link AsyncNotifyService} 实现财务相关通知发送，
 * 支持多渠道（站内信/邮件/企业微信/钉钉/飞书）异步投递，自带重试和指数退避。
 *
 * <p><b>通知场景：</b>
 * <ul>
 *   <li>发票创建提醒 — 通知财务人员有新发票需要处理</li>
 *   <li>付款确认通知 — 通知相关方付款已完成</li>
 *   <li>对账完成通知 — 通知对账结果给财务负责人</li>
 *   <li>信用额度预警 — 客户信用额度接近上限时预警</li>
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
public class FinanceNotifyHelper {

    private final AsyncNotifyService asyncNotifyService;

    /**
     * 发票创建通知
     *
     * @param receiverIds 接收人 ID 列表
     * @param invoiceNo   发票编号
     * @param amount      发票金额
     * @param projectCode 项目编号
     */
    public void notifyInvoiceCreated(List<String> receiverIds, String invoiceNo,
                                      String amount, String projectCode) {
        if (receiverIds == null || receiverIds.isEmpty()) {
            return;
        }
        String title = "新发票待处理";
        String content = String.format("发票编号: %s\n项目: %s\n金额: %s\n请尽快处理",
                invoiceNo, projectCode, amount);
        sendToReceivers(receiverIds, title, content);
    }

    /**
     * 付款确认通知
     *
     * @param receiverIds 接收人 ID 列表
     * @param paymentNo   付款单号
     * @param amount      付款金额
     * @param payee       收款方
     */
    public void notifyPaymentConfirmed(List<String> receiverIds, String paymentNo,
                                        String amount, String payee) {
        if (receiverIds == null || receiverIds.isEmpty()) {
            return;
        }
        String title = "付款已完成";
        String content = String.format("付款单号: %s\n金额: %s\n收款方: %s",
                paymentNo, amount, payee);
        sendToReceivers(receiverIds, title, content);
    }

    /**
     * 对账完成通知
     *
     * @param receiverIds  接收人 ID 列表
     * @param reconcileDate 对账日期
     * @param result        对账结果（SUCCESS/PARTIAL/FAILED）
     * @param summary       对账摘要
     */
    public void notifyReconcileCompleted(List<String> receiverIds, String reconcileDate,
                                          String result, String summary) {
        if (receiverIds == null || receiverIds.isEmpty()) {
            return;
        }
        String title = "对账完成: " + reconcileDate;
        String content = String.format("对账日期: %s\n结果: %s\n摘要: %s",
                reconcileDate, result, summary);
        sendToReceivers(receiverIds, title, content);
    }

    /**
     * 客户信用额度预警
     *
     * @param receiverIds  接收人 ID 列表
     * @param customerName 客户名称
     * @param usedRatio    已用比例
     * @param remaining    剩余额度
     */
    public void notifyCreditAlert(List<String> receiverIds, String customerName,
                                   String usedRatio, String remaining) {
        if (receiverIds == null || receiverIds.isEmpty()) {
            return;
        }
        String title = "客户信用额度预警";
        String content = String.format("客户: %s\n已用比例: %s\n剩余额度: %s\n请关注信用风险",
                customerName, usedRatio, remaining);
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
                                log.warn("[FinanceNotify] 通知发送失败: receiver={} err={}",
                                        receiverId, ex.getMessage());
                            } else if (!result.isSuccess()) {
                                log.warn("[FinanceNotify] 通知发送失败: receiver={} error={}",
                                        receiverId, result.getErrorMessage());
                            }
                        });
            } catch (Exception e) {
                log.warn("[FinanceNotify] 通知发送异常: receiver={} err={}",
                        receiverId, e.getMessage());
            }
        }
    }
}
