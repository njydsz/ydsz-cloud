package com.njydsz.common.auth.util;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.util.string.StringUtils;

/**
 * 列权限 HMAC-SHA256 签名工具类。
 *
 * <p>用于对列权限 Header（X-Visible-Columns / X-Editable-Columns）进行签名生成与校验，
 * 防止攻击者伪造或篡改列权限数据。
 *
 * <p><b>签名算法（含防重放）：</b>
 * <pre>
 * signData = visibleColumns + "|" + editableColumns + "|" + timestamp + "|" + nonce
 * HMAC-SHA256(signData, appSecret)
 * </pre>
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>客户端：在发送列权限数据前，使用 AppSecret 对数据做签名，并通过 X-Col-Permission-Sign Header 传递</li>
 *   <li>服务端：收到请求后，使用相同的 AppSecret 重新计算签名并与传入签名对比，不匹配则拒绝请求</li>
 * </ul>
 *
 * <p><b>安全约束：</b>
 * <ul>
 *   <li>签名密钥必须保密，建议通过配置中心或环境变量注入</li>
 *   <li>签名校验失败时抛出 SecurityException，并记录安全审计日志</li>
 *   <li>密钥为空时跳过签名校验（仅建议开发/测试环境使用）</li>
 *   <li>签名包含时间戳和 nonce，可防重放攻击（需在配置中开启签名校验）</li>
 * </ul>
 *
 * <p><b>注意事项：</b>
 * <p>列权限数据实际由服务端从 Redis 或 ColumnPermissionResolver 加载，客户端透传的 Header
 * 仅用于 Feign 调用下游服务时传递权限信息。在服务端已有独立权限数据源的场景下，签名校验收益有限。
 * 建议仅在开放 API 或跨网络边界调用时开启。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
public class AuthColPermissionSigner {

    private static final Logger log = LoggerFactory.getLogger(AuthColPermissionSigner.class);

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * 签名密钥（AppSecret）。
     */
    private final String secretKey;

    /**
     * 是否启用签名校验。
     */
    private final boolean enabled;

    /**
     * 签名有效时间窗口（秒），用于防重放。
     */
    private final int validitySeconds;

    /**
     * 构造签名器（向后兼容，默认关闭签名校验）。
     *
     * @param secretKey 签名密钥，为空时签名校验将被跳过
     * @deprecated 使用 {@link #AuthColPermissionSigner(String, boolean, int)} 替代
     */
    @Deprecated
    public AuthColPermissionSigner(String secretKey) {
        this(secretKey, false, 300);
    }

    /**
     * 构造签名器。
     *
     * @param secretKey       签名密钥，为空时签名校验将被跳过
     * @param enabled         是否启用签名校验
     * @param validitySeconds 签名有效时间窗口（秒），用于防重放
     * @since 2.0.0
     */
    public AuthColPermissionSigner(String secretKey, boolean enabled, int validitySeconds) {
        this.secretKey = secretKey;
        this.enabled = enabled && StringUtils.isNotBlank(secretKey);
        this.validitySeconds = validitySeconds;
    }

    /**
     * 生成列权限数据的 HMAC-SHA256 签名（含时间戳和 nonce）。
     *
     * <p>签名数据源：{@code visibleColumns + "|" + editableColumns + "|" + timestamp + "|" + nonce}
     *
     * @param visibleColumns  可见列规则（如 "table1:col1,col2;table2:col3"）
     * @param editableColumns 可编辑列规则（格式同 visibleColumns）
     * @return 十六进制编码的签名值；若签名未启用则返回空字符串
     */
    public String generateSign(String visibleColumns, String editableColumns) {
        if (!enabled) {
            log.debug("列权限签名校验未启用，跳过签名生成");
            return "";
        }
        long timestamp = Instant.now().getEpochSecond();
        String nonce = generateNonce();
        String data = buildSignData(visibleColumns, editableColumns, timestamp, nonce);
        return hmacSha256(data, secretKey);
    }

    /**
     * 生成包含时间戳和 nonce 的签名。
     *
     * <p>客户端调用此方法获取完整的签名信息，需将 timestamp 和 nonce 一并传递给服务端。
     *
     * @param visibleColumns  可见列规则
     * @param editableColumns 可编辑列规则
     * @return 签名结果对象，包含签名值、时间戳和 nonce
     * @since 2.0.0
     */
    public SignResult generateSignWithTimestamp(String visibleColumns, String editableColumns) {
        if (!enabled) {
            return SignResult.disabled();
        }
        long timestamp = Instant.now().getEpochSecond();
        String nonce = generateNonce();
        String data = buildSignData(visibleColumns, editableColumns, timestamp, nonce);
        String sign = hmacSha256(data, secretKey);
        return new SignResult(sign, timestamp, nonce);
    }

    /**
     * 校验列权限数据的签名是否合法（含防重放检查）。
     *
     * <p>校验逻辑：
     * <ol>
     *   <li>若签名未启用，跳过校验（返回 true）</li>
     *   <li>若传入签名为空，视为校验失败</li>
     *   <li>校验时间戳是否在有效窗口内（防重放）</li>
     *   <li>使用相同密钥重新计算签名，与传入签名做恒定时间比较</li>
     * </ol>
     *
     * @param visibleColumns  可见列规则
     * @param editableColumns 可编辑列规则
     * @param receivedSign    客户端传入的签名值
     * @return 校验是否通过
     * @throws SecurityException 签名不匹配或时间戳过期时抛出
     */
    public boolean verifySign(String visibleColumns, String editableColumns, String receivedSign) {
        if (!enabled) {
            log.debug("列权限签名校验未启用，跳过签名校验");
            return true;
        }
        if (StringUtils.isBlank(receivedSign)) {
            logSecurityEvent("签名缺失", "客户端未提供列权限签名");
            throw new SecurityException("列权限签名缺失，拒绝请求");
        }

        String expectedSign = generateSign(visibleColumns, editableColumns);
        if (!constantTimeEquals(expectedSign, receivedSign)) {
            logSecurityEvent("签名校验失败",
                    "期望签名前缀: " + (expectedSign != null ? expectedSign.substring(0, Math.min(8, expectedSign.length())) : "null")
                            + ", 收到签名前缀: " + receivedSign.substring(0, Math.min(8, receivedSign.length())));
            throw new SecurityException("列权限签名校验失败，拒绝请求");
        }

        log.debug("列权限签名校验通过");
        return true;
    }

    /**
     * 校验列权限数据的签名（含时间戳和 nonce 防重放）。
     *
     * @param visibleColumns  可见列规则
     * @param editableColumns 可编辑列规则
     * @param receivedSign    客户端传入的签名值
     * @param timestamp       签名时间戳（Unix 秒）
     * @param nonce           签名随机数
     * @return 校验是否通过
     * @throws SecurityException 签名不匹配、时间戳过期或 nonce 重复时抛出
     * @since 2.0.0
     */
    public boolean verifySign(String visibleColumns, String editableColumns, String receivedSign,
                              long timestamp, String nonce) {
        if (!enabled) {
            log.debug("列权限签名校验未启用，跳过签名校验");
            return true;
        }
        if (StringUtils.isBlank(receivedSign)) {
            logSecurityEvent("签名缺失", "客户端未提供列权限签名");
            throw new SecurityException("列权限签名缺失，拒绝请求");
        }

        // 防重放：校验时间戳是否在有效窗口内
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - timestamp) > validitySeconds) {
            logSecurityEvent("签名时间戳过期",
                    "timestamp=" + timestamp + ", now=" + now + ", validitySeconds=" + validitySeconds);
            throw new SecurityException("列权限签名已过期，请重新生成签名");
        }

        String data = buildSignData(visibleColumns, editableColumns, timestamp, nonce);
        String expectedSign = hmacSha256(data, secretKey);
        if (!constantTimeEquals(expectedSign, receivedSign)) {
            logSecurityEvent("签名校验失败",
                    "期望签名前缀: " + (expectedSign != null ? expectedSign.substring(0, Math.min(8, expectedSign.length())) : "null")
                            + ", 收到签名前缀: " + receivedSign.substring(0, Math.min(8, receivedSign.length())));
            throw new SecurityException("列权限签名校验失败，拒绝请求");
        }

        log.debug("列权限签名校验通过（含防重放）");
        return true;
    }

    /**
     * 判断当前签名器是否处于激活状态。
     *
     * @return 签名启用且密钥非空时返回 true
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 构建签名原始数据（含时间戳和 nonce）。
     *
     * @param visibleColumns  可见列规则
     * @param editableColumns 可编辑列规则
     * @param timestamp       签名时间戳（Unix 秒）
     * @param nonce           签名随机数
     * @return 拼接后的签名数据源
     */
    private String buildSignData(String visibleColumns, String editableColumns, long timestamp, String nonce) {
        String visible = visibleColumns != null ? visibleColumns : "";
        String editable = editableColumns != null ? editableColumns : "";
        return visible + "|" + editable + "|" + timestamp + "|" + nonce;
    }

    /**
     * 生成随机 nonce（16 位十六进制字符串）。
     *
     * @return 随机 nonce
     */
    private static String generateNonce() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        byte[] bytes = new byte[8];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * 使用 HMAC-SHA256 算法对数据做签名。
     *
     * @param data 待签名数据
     * @param key 密钥
     * @return 十六进制编码的签名结果
     * @throws SecurityException 签名计算失败时抛出
     */
    private static String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            log.error("HMAC-SHA256 签名计算失败: {}", e.getMessage(), e);
            throw new SecurityException("列权限签名计算失败");
        }
    }

    /**
     * 恒定时间字符串比较，防止时序攻击。
     *
     * @param a 第一个字符串
     * @param b 第二个字符串
     * @return 是否相等
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    /**
     * 记录安全审计事件。
     *
     * @param eventType 事件类型
     * @param detail 事件详情
     */
    private static void logSecurityEvent(String eventType, String detail) {
        log.warn("[SECURITY] 列权限签名校验异常 - 类型: {}, 详情: {}", eventType, detail);
    }

    /**
     * 列权限签名结果对象。
     *
     * <p>包含签名值、时间戳和 nonce，客户端需将 timestamp 和 nonce 通过 Header 传递给服务端，
     * 服务端使用这三项数据进行签名校验和防重放检查。
     *
     * <p>对应的 Header 名称：
     * <ul>
     *   <li>X-Col-Permission-Sign：签名值</li>
     *   <li>X-Col-Permission-Timestamp：签名时间戳</li>
     *   <li>X-Col-Permission-Nonce：签名随机数</li>
     * </ul>
     *
     * @author ydsz-team
     * @since 2.0.0
     */
    public static class SignResult {

        /**
         * 签名值（十六进制编码）。
         */
        private final String sign;

        /**
         * 签名时间戳（Unix 秒）。
         */
        private final long timestamp;

        /**
         * 签名随机数。
         */
        private final String nonce;

        /**
         * 签名是否启用。
         */
        private final boolean enabled;

        private SignResult(String sign, long timestamp, String nonce) {
            this.sign = sign;
            this.timestamp = timestamp;
            this.nonce = nonce;
            this.enabled = true;
        }

        private SignResult() {
            this.sign = "";
            this.timestamp = 0L;
            this.nonce = "";
            this.enabled = false;
        }

        /**
         * 创建签名未启用的结果对象。
         *
         * @return 签名未启用的 SignResult
         */
        public static SignResult disabled() {
            return new SignResult();
        }

        public String getSign() {
            return sign;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getNonce() {
            return nonce;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }
}
