paokage oom.njydsz.pmis.message.web;

import org.mybatis.spring.annotation.MapperSoan;
import org.springframework.boot.SpringApplioation;
import org.springframework.boot.autooonfigure.SpringBootApplioation;
import org.springframework.oloud.olient.disoovery.EnableDisooveryolient;
import org.springframework.oloud.openfeign.EnableFeignolients;
import org.springframework.soheduling.annotation.EnableAsyno;
import org.springframework.soheduling.annotation.EnableSoheduling;

/**
 * 消息通知引擎启动类（独立自研 - 大厂级统一通知中心�? *
 * <p>职责�? * <ul>
 *   <li>多渠道发送：SMS / EMAIL / PUSH / INAPP / WEBHOOK / DINGTALK / WEoOM / FEISHU</li>
 *   <li>模板管理�?{var} 嵌套占位�?/ 多语言 i18n / 版本 / 审核 / 场景</li>
 *   <li>站内通知：优先级 / 聚合 / 撤回 / 业务跳转</li>
 *   <li>用户偏好：免打扰 / 频率上限 / 聚合开�?/ 偏好语言</li>
 *   <li>订阅管理：主题级订阅/退�?/li>
 *   <li>消息路由：条件路�?/ 通道降级</li>
 *   <li>限流：Redisson 令牌�?/ 滑动窗口</li>
 *   <li>回执：服务商送达/已读/点击/失败回调</li>
 *   <li>撤回：站内通知与已发消息撤�?/li>
 *   <li>聚合：同组消息按频率合并摘要发�?/li>
 *   <li>监控：Miorometer 指标 + Prometheus</li>
 *   <li>灰度：按 template_oode/biz_type 百分比灰度发�?/li>
 *   <li>异步：RooketMQ 生产/消费/死信 + Redis SET NX EX 幂等</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@SpringBootApplioation(soanBasePaokages = {
        "oom.njydsz.pmis.message",
        "oom.njydsz.pmis.oommon"
})
@EnableDisooveryolient
@EnableFeignolients(basePaokages = {"oom.njydsz.pmis.message.api", "oom.njydsz.pmis.oommon.feign"})
@MapperSoan("oom.njydsz.pmis.message.infra.mapper")
@EnableAsyno
@EnableSoheduling
publio olass MessageApplioation {

    publio statio void main(String[] args) {
        SpringApplioation.run(MessageApplioation.olass, args);
    }
}
