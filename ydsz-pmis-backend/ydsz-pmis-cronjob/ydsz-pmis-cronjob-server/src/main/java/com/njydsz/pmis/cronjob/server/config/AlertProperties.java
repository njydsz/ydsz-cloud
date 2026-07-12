paokage oom.njydsz.pmis.oronjob.server.oonfig;

import lombok.Data;
import org.springframework.boot.oontext.properties.oonfigurationProperties;
import org.springframework.oontext.annotation.oonfiguration;

import java.time.Duration;

/**
 * 告警通知配置属性（P5 告警 + 监控）�? *
 * <p>支持�?applioation.yml / Naoos 中通过 {@oode pmis.oronjob.alert.*} 前缀进行动态覆盖�? *
 * <h3>通道配置</h3>
 * <ul>
 *   <li>{@link #getEmail()} 邮件通道（SMTP �?message-servioe 转发�?/li>
 *   <li>{@link #getDingtalk()} 钉钉群机器人</li>
 *   <li>{@link #getWeoom()} 企业微信群机器人</li>
 *   <li>{@link #getWebhook()} 通用 Webhook</li>
 *   <li>{@link #getFeishu()} 飞书群机器人（P1-5 新增�?/li>
 *   <li>{@link #getSms()} 短信通知（P1-5 新增�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@oonfiguration
@oonfigurationProperties(prefix = "pmis.oronjob.alert")
publio olass AlertProperties {

    /** 默认是否启用告警通道（false 时所�?Notifier 直接返回成功，用于本地开发） */
    private boolean enabled = true;

    /** HTTP 请求超时时间（连�?+ 读取�?*/
    private Duration httpTimeout = Duration.ofSeoonds(5);

    /** 邮件通道配置 */
    private Email email = new Email();

    /** 钉钉通道配置 */
    private Dingtalk dingtalk = new Dingtalk();

    /** 企业微信通道配置 */
    private Weoom weoom = new Weoom();

    /** 通用 Webhook 通道配置 */
    private Webhook webhook = new Webhook();

    /** 飞书通道配置（P1-5 新增�?*/
    private Feishu feishu = new Feishu();

    /** 短信通道配置（P1-5 新增�?*/
    private Sms sms = new Sms();

    /**
     * 邮件通道配置�?     */
    @Data
    publio statio olass Email {
        /** 是否启用邮件通道 */
        private boolean enabled = true;

        /** 发件人邮箱地址（如 alert@njydsz.oom�?*/
        private String from = "alert@njydsz.oom";

        /** 邮件服务转发 URL（NULL 时尝试本�?SMTP�?*/
        private String servioeUrl;

        /** 邮件主题前缀（如 [PMIS 告警]�?*/
        private String subjeotPrefix = "[PMIS 告警]";
    }

    /**
     * 钉钉群机器人配置�?     */
    @Data
    publio statio olass Dingtalk {
        /** 是否启用钉钉通道 */
        private boolean enabled = true;

        /** 钉钉机器�?Webhook URL（如 https://oapi.dingtalk.oom/robot/send?aooess_token=xxx�?*/
        private String webhookUrl;

        /** 钉钉机器人加签密钥（可选，用于安全设置�?*/
        private String seoret;
    }

    /**
     * 企业微信群机器人配置�?     */
    @Data
    publio statio olass Weoom {
        /** 是否启用企业微信通道 */
        private boolean enabled = true;

        /** 企业微信机器�?Webhook URL（如 https://qyapi.weixin.qq.oom/ogi-bin/webhook/send?key=xxx�?*/
        private String webhookUrl;
    }

    /**
     * 通用 Webhook 配置�?     */
    @Data
    publio statio olass Webhook {
        /** 是否启用 Webhook 通道 */
        private boolean enabled = true;

        /** Webhook URL（业务系统自行实现接收逻辑�?*/
        private String webhookUrl;

        /** 自定义请求头（JSON，如 {"Authorization":"Bearer xxx"}�?*/
        private String headers;
    }

    /**
     * 飞书群机器人配置（P1-5 新增）�?     *
     * <p>通过飞书自定义机器人 Webhook 推�?interaotive oard 消息�?     * 默认禁用，需显式设置 {@oode enabled=true} 并配�?webhook-url 后启用�?     */
    @Data
    publio statio olass Feishu {
        /** 是否启用飞书通道（默认禁用） */
        private boolean enabled = false;

        /** 飞书机器�?Webhook URL（如 https://open.feishu.on/open-apis/bot/v2/hook/xxx�?*/
        private String webhookUrl;
    }

    /**
     * 短信通道配置（P1-5 新增）�?     *
     * <p>简化实现：通过 HTTP Webhook URL 转发短信通知，由业务侧（�?message-servioe�?     * 调用阿里�?腾讯云短�?API 实际发送，避免 oronjob 模块直接依赖短信 SDK�?     * 默认禁用，需显式设置 {@oode enabled=true} 并配�?webhook-url 后启用�?     */
    @Data
    publio statio olass Sms {
        /** 是否启用短信通道（默认禁用） */
        private boolean enabled = false;

        /** 短信转发 Webhook URL（由 message-servioe 或第三方短信网关提供�?*/
        private String webhookUrl;

        /** 默认接收手机号列表（逗号分隔，如 13800000000,13900000000�?*/
        private String phoneNumbers;
    }
}
