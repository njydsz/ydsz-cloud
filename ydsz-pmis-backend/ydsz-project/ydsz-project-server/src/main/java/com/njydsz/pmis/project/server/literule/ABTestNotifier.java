package com.njydsz.project.server.literule;

/**
 * AB Test 通知器接口（P1-10）
 *
 * <p>用于 AB Test 自动回滚/通知场景，由 ydsz-system 模块实现（依赖 system 模块的 NotificationService）。
 * 项目模块仅定义接口，避免反向依赖。
 *
 * <p>实现方负责将通知转化为 INAPP / EMAIL / SMS / WEBHOOK 等渠道的具体投递动作。
 *
 * @author ydsz-team
 * @since 1.5.0
 */
public interface ABTestNotifier {

    /**
     * 发送通知
     *
     * @param recipient 接收人（工号/用户名/邮箱）
     * @param subject   标题
     * @param content   内容
     * @param channels  渠道（逗号分隔：INAPP/EMAIL/SMS/WEBHOOK）
     */
    void notify(String recipient, String subject, String content, String channels);
}
