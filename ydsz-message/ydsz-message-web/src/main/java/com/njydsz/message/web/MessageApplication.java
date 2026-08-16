package com.njydsz.message.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.njydsz.common.audit.annotation.EnableYdszAudit;
import com.njydsz.common.auth.annotation.EnableYdszAuth;
import com.njydsz.common.feign.annotation.EnableYdszFeign;
import com.njydsz.common.safe.annotation.EnableYdszSafe;

/**
 * 消息通知引擎启动类（独立自研 - 大厂级统一通知中心）
 *
 * <p>职责：
 *
 * <ul>
 *   <li>多渠道发送：SMS / EMAIL / PUSH / INAPP / WEBHOOK / DINGTALK / WECOM / FEISHU
 *   <li>模板管理：${var} 嵌套占位符 / 多语言 i18n / 版本 / 审核 / 场景
 *   <li>站内通知：优先级 / 聚合 / 撤回 / 业务跳转
 *   <li>用户偏好：免打扰 / 频率上限 / 聚合开关 / 偏好语言
 *   <li>订阅管理：主题级订阅/退订
 *   <li>消息路由：条件路由 / 通道降级
 *   <li>限流：Redisson 令牌桶 / 滑动窗口
 *   <li>回执：服务商送达/已读/点击/失败回调
 *   <li>撤回：站内通知与已发消息撤回
 *   <li>聚合：同组消息按频率合并摘要发送
 *   <li>监控：Micrometer 指标 + Prometheus
 *   <li>灰度：按 template_code/biz_type 百分比灰度发布
 *   <li>异步：RocketMQ 生产/消费/死信 + Redis SET NX EX 幂等
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = {"com.njydsz.message", "com.njydsz.common"})
@EnableDiscoveryClient
@EnableYdszAuth
@EnableYdszSafe
@EnableYdszAudit
@EnableYdszFeign(
    basePackages = {"com.njydsz.message.api", "com.njydsz.common.feign", "com.njydsz.agent.api"})
@MapperScan("com.njydsz.message.infra.mapper")
@EnableAsync
@EnableScheduling
public class MessageApplication {


  public static void main(String[] args) {
    SpringApplication.run(MessageApplication.class, args);
  }
}
