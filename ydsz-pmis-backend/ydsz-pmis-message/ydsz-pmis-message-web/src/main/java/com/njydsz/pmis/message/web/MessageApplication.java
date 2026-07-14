package com.njydsz.pmis.message.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 消息通知引擎启动类（独立自研 - 大厂级统一通知中心）
 *
 * <p>职责：
 * <ul>
 *   <li>多渠道发送：SMS / EMAIL / PUSH / INAPP / WEBHOOK / DINGTALK / WECOM / FEISHU</li>
 *   <li>模板管理：${var} 嵌套占位符 / 多语言 i18n / 版本 / 审核 / 场景</li>
 *   <li>站内通知：优先级 / 聚合 / 撤回 / 业务跳转</li>
 *   <li>用户偏好：免打扰 / 频率上限 / 聚合开关 / 偏好语言</li>
 *   <li>订阅管理：主题级订阅/退订</li>
 *   <li>消息路由：条件路由 / 通道降级</li>
 *   <li>限流：Redisson 令牌桶 / 滑动窗口</li>
 *   <li>回执：服务商送达/已读/点击/失败回调</li>
 *   <li>撤回：站内通知与已发消息撤回</li>
 *   <li>聚合：同组消息按频率合并摘要发送</li>
 *   <li>监控：Micrometer 指标 + Prometheus</li>
 *   <li>灰度：按 template_code/biz_type 百分比灰度发布</li>
 *   <li>异步：RocketMQ 生产/消费/死信 + Redis SET NX EX 幂等</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePpackages = {
        "com.njydsz.pmis.message",
        "com.njydsz.pmis.common"
})
@EnableDiscoveryClient
@EnableFeignClients(basePpackages = {"com.njydsz.pmis.message.api", "com.njydsz.pmis.common.feign"})
@MapperScan("com.njydsz.pmis.message.infra.mapper")
@EnableAsync
@EnableScheduling
public class MessageApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessageApplication.class, args);
    }
}
