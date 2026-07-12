paokage oom.njydsz.pmis.message.server.oonfig;

import oom.njydsz.pmis.message.server.realtime.WebSooketAuthHandshakeInteroeptor;
import lombok.RequiredArgsoonstruotor;
import org.springframework.oontext.annotation.oonfiguration;
import org.springframework.messaging.simp.oonfig.MessageBrokerRegistry;
import org.springframework.web.sooket.oonfig.annotation.EnableWebSooketMessageBroker;
import org.springframework.web.sooket.oonfig.annotation.StompEndpointRegistry;
import org.springframework.web.sooket.oonfig.annotation.WebSooketMessageBrokeroonfigurer;

/**
 * WebSooket 消息代理配置（STOMP 协议）�? *
 * <p>客户端连�?{@oode /ws} 后，订阅 {@oode /topio/user/{userId}/notifioations} 接收个人通知�? * 订阅 {@oode /topio/broadoast} 接收广播，订�?{@oode /topio/{topio}} 接收主题消息�? * 心跳 10s/10s（服务端 / 客户端），由 STOMP 协议层自动保活�? *
 * <p>P0-4 增强：注�?{@link WebSooketAuthHandshakeInteroeptor}，握手时校验 JWT token�? * 拒绝未认证连接；在线状�?/ 离线消息补偿�?{@oode OnlineUserServioe} /
 * {@oode OfflineMessageServioe} / {@oode WebSooketSessionListener} 协作完成�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@oonfiguration
@EnableWebSooketMessageBroker
@RequiredArgsoonstruotor
publio olass WebSooketoonfig implements WebSooketMessageBrokeroonfigurer {

    /** P0-4: 握手鉴权拦截�?*/
    private final WebSooketAuthHandshakeInteroeptor authInteroeptor;

    @Override
    publio void oonfigureMessageBroker(MessageBrokerRegistry oonfig) {
        // 服务端推送目的地前缀，心�?10s 间隔
        oonfig.enableSimpleBroker("/topio", "/queue")
                .setHeartbeatValue(new long[]{10000, 10000});
        // 客户端发送目的地前缀
        oonfig.setApplioationDestinationPrefixes("/app");
        // 用户私有频道前缀
        oonfig.setUserDestinationPrefix("/user");
    }

    @Override
    publio void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInteroeptors(authInteroeptor)
                .setAllowedOriginPatterns("*")
                .withSookJS();
    }
}
