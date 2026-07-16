package com.njydsz.pmis.common.notify.provider;

import java.util.List;
import java.util.Map;

/**
 * 短信服务提供商接口（P2-1）
 *
 * <p>抽象短信发送能力，支持阿里云、腾讯云、华为云等多厂商快速接入。
 * 各厂商通过实现此接口并配合 {@code @ConditionalOnProperty} 条件注册。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
public interface SmsProvider {

    /**
     * 获取提供商名称
     *
     * @return 提供商名称（如 aliyun、tencent、huawei）
     */
    String getProviderName();

    /**
     * 发送短信
     *
     * @param phoneNumber   手机号
     * @param signName      短信签名
     * @param templateCode  模板编码
     * @param templateParams 模板参数
     * @return 发送结果
     */
    SmsSendResult send(String phoneNumber, String signName, String templateCode, Map<String, Object> templateParams);

    /**
     * 批量发送短信
     *
     * @param phoneNumbers  手机号列表
     * @param signName      短信签名
     * @param templateCode  模板编码
     * @param templateParams 模板参数
     * @return 批量发送结果
     */
    SmsSendResult batchSend(List<String> phoneNumbers, String signName, String templateCode,
                            Map<String, Object> templateParams);

    /**
     * 查询短信余额
     *
     * @return 余额信息
     */
    SmsBalance queryBalance();

    /**
     * 短信发送结果
     */
    class SmsSendResult {

        private final boolean success;
        private final String messageId;
        private final String errorCode;
        private final String errorMessage;

        /**
         * 构造发送结果
         *
         * @param success     是否成功
         * @param messageId   消息ID
         * @param errorCode   错误码
         * @param errorMessage 错误信息
         */
        public SmsSendResult(boolean success, String messageId, String errorCode, String errorMessage) {
            this.success = success;
            this.messageId = messageId;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        public static SmsSendResult success(String messageId) {
            return new SmsSendResult(true, messageId, null, null);
        }

        public static SmsSendResult failure(String errorCode, String errorMessage) {
            return new SmsSendResult(false, null, errorCode, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public String getMessageId() { return messageId; }
        public String getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * 短信余额信息
     */
    class SmsBalance {

        private final long remainingCount;
        private final String currency;
        private final double remainingAmount;

        /**
         * 构造余额信息
         *
         * @param remainingCount  剩余条数
         * @param currency        币种
         * @param remainingAmount 剩余金额
         */
        public SmsBalance(long remainingCount, String currency, double remainingAmount) {
            this.remainingCount = remainingCount;
            this.currency = currency;
            this.remainingAmount = remainingAmount;
        }

        public long getRemainingCount() { return remainingCount; }
        public String getCurrency() { return currency; }
        public double getRemainingAmount() { return remainingAmount; }
    }
}
