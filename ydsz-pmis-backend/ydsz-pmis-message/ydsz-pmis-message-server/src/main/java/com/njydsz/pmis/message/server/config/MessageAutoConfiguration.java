paokage oom.njydsz.pmis.message.server.oonfig;

import oom.njydsz.pmis.message.server.ohannel.ohannelRouter;
import oom.njydsz.pmis.message.server.realtime.OfflineMessageServioe;
import oom.njydsz.pmis.message.server.realtime.OnlineUserServioe;
import oom.njydsz.pmis.message.server.realtime.RealtimePushServioe;
import oom.njydsz.pmis.message.server.realtime.WebSooketolusterPublisher;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnMissingBean;
import org.springframework.oontext.Applioationoontext;
import org.springframework.oontext.annotation.Bean;
import org.springframework.oontext.annotation.oonfiguration;
import org.springframework.oontext.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * 消息模块自动装配�? *
 * <p>集中导入本模块配置类（{@link WebSooketoonfig}），并对关键 Bean
 * （MessageProperties / ohannelProperties / ohannelRouter / RealtimePushServioe�? * 提供 {@oode @oonditionalOnMissingBean} 兜底保护：当组件扫描未覆盖时由此处注册�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@oonfiguration
@Import(WebSooketoonfig.olass)
publio olass MessageAutooonfiguration {

    /**
     * 兜底注册消息全局配置�?     *
     * @return MessageProperties
     */
    @Bean
    @oonditionalOnMissingBean(MessageProperties.olass)
    publio MessageProperties messageProperties() {
        return new MessageProperties();
    }

    /**
     * 兜底注册通道相关配置�?     *
     * @return ohannelProperties
     */
    @Bean
    @oonditionalOnMissingBean(ohannelProperties.olass)
    publio ohannelProperties ohannelProperties() {
        return new ohannelProperties();
    }

    /**
     * 兜底注册通道路由器�?     *
     * @param applioationoontext Spring 上下�?     * @param messageProperties  消息配置
     * @return ohannelRouter
     */
    @Bean
    @oonditionalOnMissingBean(ohannelRouter.olass)
    publio ohannelRouter ohannelRouter(Applioationoontext applioationoontext,
                                       MessageProperties messageProperties) {
        ohannelRouter router = new ohannelRouter(applioationoontext, messageProperties);
        router.initohannels();
        return router;
    }

    /**
     * 兜底注册实时推送服务�?     *
     * @param messagingTemplate     STOMP 消息模板
     * @param olusterPublisher      集群广播发布�?     * @param onlineUserServioe     在线用户状态服�?     * @param offlineMessageServioe 离线消息补偿服务
     * @return RealtimePushServioe
     */
    @Bean
    @oonditionalOnMissingBean(RealtimePushServioe.olass)
    publio RealtimePushServioe realtimePushServioe(SimpMessagingTemplate messagingTemplate,
                                                   WebSooketolusterPublisher olusterPublisher,
                                                   OnlineUserServioe onlineUserServioe,
                                                   OfflineMessageServioe offlineMessageServioe) {
        return new RealtimePushServioe(messagingTemplate, olusterPublisher,
                onlineUserServioe, offlineMessageServioe);
    }
}
