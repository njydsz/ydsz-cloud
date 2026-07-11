package com.njydsz.pmis.message.server.config;

import com.njydsz.pmis.message.server.channel.ChannelRouter;
import com.njydsz.pmis.message.server.realtime.OfflineMessageService;
import com.njydsz.pmis.message.server.realtime.OnlineUserService;
import com.njydsz.pmis.message.server.realtime.RealtimePushService;
import com.njydsz.pmis.message.server.realtime.WebSocketClusterPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * 消息模块自动装配。
 *
 * <p>集中导入本模块配置类（{@link WebSocketConfig}），并对关键 Bean
 * （MessageProperties / ChannelProperties / ChannelRouter / RealtimePushService）
 * 提供 {@code @ConditionalOnMissingBean} 兜底保护：当组件扫描未覆盖时由此处注册。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Configuration
@Import(WebSocketConfig.class)
public class MessageAutoConfiguration {

    /**
     * 兜底注册消息全局配置。
     *
     * @return MessageProperties
     */
    @Bean
    @ConditionalOnMissingBean(MessageProperties.class)
    public MessageProperties messageProperties() {
        return new MessageProperties();
    }

    /**
     * 兜底注册通道相关配置。
     *
     * @return ChannelProperties
     */
    @Bean
    @ConditionalOnMissingBean(ChannelProperties.class)
    public ChannelProperties channelProperties() {
        return new ChannelProperties();
    }

    /**
     * 兜底注册通道路由器。
     *
     * @param applicationContext Spring 上下文
     * @param messageProperties  消息配置
     * @return ChannelRouter
     */
    @Bean
    @ConditionalOnMissingBean(ChannelRouter.class)
    public ChannelRouter channelRouter(ApplicationContext applicationContext,
                                       MessageProperties messageProperties) {
        ChannelRouter router = new ChannelRouter(applicationContext, messageProperties);
        router.initChannels();
        return router;
    }

    /**
     * 兜底注册实时推送服务。
     *
     * @param messagingTemplate     STOMP 消息模板
     * @param clusterPublisher      集群广播发布者
     * @param onlineUserService     在线用户状态服务
     * @param offlineMessageService 离线消息补偿服务
     * @return RealtimePushService
     */
    @Bean
    @ConditionalOnMissingBean(RealtimePushService.class)
    public RealtimePushService realtimePushService(SimpMessagingTemplate messagingTemplate,
                                                   WebSocketClusterPublisher clusterPublisher,
                                                   OnlineUserService onlineUserService,
                                                   OfflineMessageService offlineMessageService) {
        return new RealtimePushService(messagingTemplate, clusterPublisher,
                onlineUserService, offlineMessageService);
    }
}
