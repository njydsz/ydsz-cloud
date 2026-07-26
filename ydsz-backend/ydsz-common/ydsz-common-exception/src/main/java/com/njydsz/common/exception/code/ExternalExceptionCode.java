package com.njydsz.common.exception.code;

import java.util.HashMap;
import java.util.Map;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionCodeRegistry;

import lombok.Getter;

/**
 * 外部/三方服务类错误码枚举 (E)
 *
 * <p>涵盖外部服务（支付、短信、邮件、API网关、第三方SaaS、Feign 调用等）
 * 调用失败的错误码，配合 ydsz-common-feign 框架使用。
 *
 * <p><b>编码规范：</b>
 * <pre>
 *     E + 模块(2位) + 序号(3位)
 * </pre>
 *
 * <p><b>模块定义：</b>
 * <ul>
 *   <li>E01xxx - 通用外部服务（超时、连接失败、不可达）</li>
 *   <li>E02xxx - Feign / OpenFeign 调用</li>
 *   <li>E03xxx - 网关 / API Gateway</li>
 *   <li>E04xxx - 支付服务</li>
 *   <li>E05xxx - 短信 / 邮件 / 推送</li>
 *   <li>E06xxx - 存储 / OSS / CDN</li>
 *   <li>E07xxx - 消息队列 / Kafka / RocketMQ</li>
 *   <li>E08xxx - 搜索引擎 / ES / OpenSearch</li>
 *   <li>E09xxx - 第三方 OAuth / 登录</li>
 * </ul>
 *
 * <p><b>HTTP 状态码：</b>
 * <ul>
 *   <li>502 - 网关错误（上游服务不可达）</li>
 *   <li>503 - 服务不可用（上游暂时不可用）</li>
 *   <li>504 - 网关超时（上游响应超时）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ExceptionCode
 */
@Getter
public enum ExternalExceptionCode implements ExceptionCode {

    // ==================== E01 通用外部服务 ====================

    /** 外部服务调用失败 */
    EXTERNAL_SERVICE_FAILED("E01001", "external.service.failed", 502),
    /** 外部服务超时 */
    EXTERNAL_SERVICE_TIMEOUT("E01002", "external.service.timeout", 504),
    /** 外部服务连接失败 */
    EXTERNAL_CONNECTION_FAILED("E01003", "external.connection.failed", 502),
    /** 外部服务拒绝（4xx） */
    EXTERNAL_SERVICE_REJECTED("E01004", "external.service.rejected", 502),
    /** 外部服务错误（5xx） */
    EXTERNAL_SERVICE_ERROR("E01005", "external.service.error", 502),
    /** 外部服务返回数据格式错误 */
    EXTERNAL_INVALID_RESPONSE("E01006", "external.invalid.response", 502),
    /** 外部服务返回业务错误码 */
    EXTERNAL_BUSINESS_ERROR("E01007", "external.business.error", 502),
    /** 外部服务返回数据签名校验失败 */
    EXTERNAL_SIGNATURE_INVALID("E01008", "external.signature.invalid", 502),
    /** 外部服务返回数据加密/解密失败 */
    EXTERNAL_DECRYPT_FAILED("E01009", "external.decrypt.failed", 502),
    /** 外部服务限流 */
    EXTERNAL_RATE_LIMITED("E01010", "external.rate.limited", 429),
    /** 外部服务熔断 */
    EXTERNAL_CIRCUIT_BROKEN("E01011", "external.circuit.broken", 503),
    /** 外部服务未注册 */
    EXTERNAL_NOT_REGISTERED("E01012", "external.not.registered", 502),
    /** 外部服务降级 */
    EXTERNAL_DEGRADED("E01013", "external.degraded", 503),

    // ==================== E02 Feign / OpenFeign ====================

    /** Feign 调用失败 */
    FEIGN_FAILED("E02001", "external.feign.failed", 502),
    /** Feign 调用超时 */
    FEIGN_TIMEOUT("E02002", "external.feign.timeout", 504),
    /** Feign Fallback 触发 */
    FEIGN_FALLBACK("E02003", "external.feign.fallback", 503),
    /** Feign 解码错误 */
    FEIGN_DECODE_ERROR("E02004", "external.feign.decode.error", 502),
    /** Feign 编码错误 */
    FEIGN_ENCODE_ERROR("E02005", "external.feign.encode.error", 502),
    /** Feign 重试耗尽 */
    FEIGN_RETRY_EXHAUSTED("E02006", "external.feign.retry.exhausted", 502),
    /** Feign 4xx 客户端错误 */
    FEIGN_CLIENT_ERROR("E02007", "external.feign.client.error", 502),
    /** Feign 5xx 服务端错误 */
    FEIGN_SERVER_ERROR("E02008", "external.feign.server.error", 502),

    // ==================== E03 网关 / API Gateway ====================

    /** 网关错误 */
    GATEWAY_ERROR("E03001", "external.gateway.error", 502),
    /** 网关超时 */
    GATEWAY_TIMEOUT("E03002", "external.gateway.timeout", 504),
    /** 网关路由失败 */
    GATEWAY_ROUTE_FAILED("E03003", "external.gateway.route.failed", 502),
    /** 网关限流 */
    GATEWAY_RATE_LIMITED("E03004", "external.gateway.rate.limited", 429),
    /** 网关未授权 */
    GATEWAY_UNAUTHORIZED("E03005", "external.gateway.unauthorized", 401),
    /** 网关熔断 */
    GATEWAY_CIRCUIT_BROKEN("E03006", "external.gateway.circuit.broken", 503),
    /** 上游服务不可达 */
    UPSTREAM_UNREACHABLE("E03007", "external.upstream.unreachable", 502),

    // ==================== E04 支付服务 ====================

    /** 支付服务失败 */
    PAYMENT_FAILED("E04001", "external.payment.failed", 502),
    /** 支付超时 */
    PAYMENT_TIMEOUT("E04002", "external.payment.timeout", 504),
    /** 支付订单创建失败 */
    PAYMENT_ORDER_CREATE_FAILED("E04003", "external.payment.order.create.failed", 502),
    /** 支付订单查询失败 */
    PAYMENT_ORDER_QUERY_FAILED("E04004", "external.payment.order.query.failed", 502),
    /** 支付退款失败 */
    PAYMENT_REFUND_FAILED("E04005", "external.payment.refund.failed", 502),
    /** 支付回调处理失败 */
    PAYMENT_CALLBACK_FAILED("E04006", "external.payment.callback.failed", 502),
    /** 支付签名验证失败 */
    PAYMENT_SIGNATURE_INVALID("E04007", "external.payment.signature.invalid", 502),
    /** 支付渠道不可用 */
    PAYMENT_CHANNEL_UNAVAILABLE("E04008", "external.payment.channel.unavailable", 502),
    /** 支付金额错误 */
    PAYMENT_AMOUNT_INVALID("E04009", "external.payment.amount.invalid", 400),
    /** 支付凭证无效 */
    PAYMENT_CREDENTIAL_INVALID("E04010", "external.payment.credential.invalid", 502),

    // ==================== E05 短信 / 邮件 / 推送 ====================

    /** 短信发送失败 */
    SMS_SEND_FAILED("E05001", "external.sms.send.failed", 502),
    /** 短信发送超时 */
    SMS_SEND_TIMEOUT("E05002", "external.sms.send.timeout", 504),
    /** 短信通道不可用 */
    SMS_CHANNEL_UNAVAILABLE("E05003", "external.sms.channel.unavailable", 502),
    /** 短信模板不存在 */
    SMS_TEMPLATE_NOT_FOUND("E05004", "external.sms.template.not.found", 502),
    /** 短信内容非法 */
    SMS_CONTENT_INVALID("E05005", "external.sms.content.invalid", 502),
    /** 短信签名错误 */
    SMS_SIGNATURE_INVALID("E05006", "external.sms.signature.invalid", 502),
    /** 短信手机号非法 */
    SMS_PHONE_INVALID("E05007", "external.sms.phone.invalid", 400),
    /** 邮件发送失败 */
    EMAIL_SEND_FAILED("E05008", "external.email.send.failed", 502),
    /** 邮件服务器不可用 */
    EMAIL_SERVER_UNAVAILABLE("E05009", "external.email.server.unavailable", 502),
    /** 邮件地址非法 */
    EMAIL_ADDRESS_INVALID("E05010", "external.email.address.invalid", 400),
    /** 推送失败 */
    PUSH_SEND_FAILED("E05011", "external.push.send.failed", 502),
    /** 推送通道不可用 */
    PUSH_CHANNEL_UNAVAILABLE("E05012", "external.push.channel.unavailable", 502),
    /** 推送 Token 无效 */
    PUSH_TOKEN_INVALID("E05013", "external.push.token.invalid", 502),

    // ==================== E06 存储 / OSS / CDN ====================

    /** OSS 上传失败 */
    OSS_UPLOAD_FAILED("E06001", "external.oss.upload.failed", 502),
    /** OSS 下载失败 */
    OSS_DOWNLOAD_FAILED("E06002", "external.oss.download.failed", 502),
    /** OSS 删除失败 */
    OSS_DELETE_FAILED("E06003", "external.oss.delete.failed", 502),
    /** OSS 复制失败 */
    OSS_COPY_FAILED("E06004", "external.oss.copy.failed", 502),
    /** OSS 签名 URL 生成失败 */
    OSS_SIGNED_URL_FAILED("E06005", "external.oss.signed.url.failed", 502),
    /** OSS 存储桶不存在 */
    OSS_BUCKET_NOT_FOUND("E06006", "external.oss.bucket.not.found", 502),
    /** OSS 访问密钥无效 */
    OSS_ACCESS_KEY_INVALID("E06007", "external.oss.access.key.invalid", 502),
    /** CDN 刷新失败 */
    CDN_REFRESH_FAILED("E06008", "external.cdn.refresh.failed", 502),
    /** CDN 预热失败 */
    CDN_PRELOAD_FAILED("E06009", "external.cdn.preload.failed", 502),

    // ==================== E07 消息队列 / Kafka / RocketMQ ====================

    /** 消息发送失败 */
    MQ_SEND_FAILED("E07001", "external.mq.send.failed", 502),
    /** 消息消费失败 */
    MQ_CONSUME_FAILED("E07002", "external.mq.consume.failed", 502),
    /** 消息订阅失败 */
    MQ_SUBSCRIBE_FAILED("E07003", "external.mq.subscribe.failed", 502),
    /** 消息 ACK 失败 */
    MQ_ACK_FAILED("E07004", "external.mq.ack.failed", 502),
    /** 消息死信队列 */
    MQ_DLQ("E07005", "external.mq.dlq", 502),
    /** 消息顺序错乱 */
    MQ_ORDER_BROKEN("E07006", "external.mq.order.broken", 502),
    /** 消息重复消费 */
    MQ_DUPLICATE("E07007", "external.mq.duplicate", 502),
    /** Broker 不可用 */
    MQ_BROKER_UNAVAILABLE("E07008", "external.mq.broker.unavailable", 503),

    // ==================== E08 搜索引擎 / ES / OpenSearch ====================

    /** ES 索引失败 */
    ES_INDEX_FAILED("E08001", "external.es.index.failed", 502),
    /** ES 查询失败 */
    ES_QUERY_FAILED("E08002", "external.es.query.failed", 502),
    /** ES 删除失败 */
    ES_DELETE_FAILED("E08003", "external.es.delete.failed", 502),
    /** ES 更新失败 */
    ES_UPDATE_FAILED("E08004", "external.es.update.failed", 502),
    /** ES 索引不存在 */
    ES_INDEX_NOT_FOUND("E08005", "external.es.index.not.found", 502),
    /** ES 集群不可用 */
    ES_CLUSTER_UNAVAILABLE("E08006", "external.es.cluster.unavailable", 503),
    /** ES 搜索超时 */
    ES_SEARCH_TIMEOUT("E08007", "external.es.search.timeout", 504),
    /** ES mapping 冲突 */
    ES_MAPPING_CONFLICT("E08008", "external.es.mapping.conflict", 502),

    // ==================== E09 第三方 OAuth / 登录 ====================

    /** OAuth 授权失败 */
    OAUTH_AUTHORIZE_FAILED("E09001", "external.oauth.authorize.failed", 502),
    /** OAuth Token 获取失败 */
    OAUTH_TOKEN_FAILED("E09002", "external.oauth.token.failed", 502),
    /** OAuth Token 刷新失败 */
    OAUTH_REFRESH_FAILED("E09003", "external.oauth.refresh.failed", 502),
    /** OAuth 用户信息获取失败 */
    OAUTH_USERINFO_FAILED("E09004", "external.oauth.userinfo.failed", 502),
    /** OAuth 授权码无效 */
    OAUTH_CODE_INVALID("E09005", "external.oauth.code.invalid", 400),
    /** OAuth State 校验失败 */
    OAUTH_STATE_INVALID("E09006", "external.oauth.state.invalid", 400),
    /** OAuth 回调地址不匹配 */
    OAUTH_REDIRECT_URI_MISMATCH("E09007", "external.oauth.redirect.mismatch", 400),
    /** 第三方登录用户不存在 */
    THIRD_PARTY_USER_NOT_FOUND("E09008", "external.thirdparty.user.not.found", 404),
    /** 第三方登录用户已绑定 */
    THIRD_PARTY_USER_BOUND("E09009", "external.thirdparty.user.bound", 409);

    /** 异常错误码 */
    private final String code;
    /** 国际化消息键 */
    private final String key;
    /** HTTP 状态码 */
    private final int httpStatus;

    ExternalExceptionCode(String code, String key, int httpStatus) {
        this.code = code;
        this.key = key;
        this.httpStatus = httpStatus;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }

    private static final Map<String, ExternalExceptionCode> CODE_MAP = new HashMap<>();

    static {
        Map<String, ExceptionCode> registryMap = new HashMap<>();
        for (ExternalExceptionCode code : values()) {
            registryMap.put(code.getCode(), code);
            CODE_MAP.put(code.getCode(), code);
        }
        ExceptionCodeRegistry.register(registryMap);
    }

    /**
     * 按 code 字符串查找外部服务异常码
     *
     * @param code 异常码字符串
     * @return 对应的外部服务异常码枚举实例；未找到返回 null
     */
    public static ExternalExceptionCode resolve(String code) {
        if (code == null) {
            return null;
        }
        return CODE_MAP.get(code);
    }
}
