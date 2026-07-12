package com.njydsz.pmis.common.auth.util;

import com.njydsz.pmis.common.util.string.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * 列权限 HMAC-SHA256 签名工具类。
 *
 * <p>用于对列权限 Header（X-Visible-Columns / X-Editable-Columns）进行签名生成与校验，
 * 防止攻击者伪造或篡改列权限数据。
 *
 * <p><b>签名算法：</b>
 * <pre>
 * HMAC-SHA256(visibleColumns + "|" + editableColumns, appSecret)
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
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class AuthColPermissionSigner {

    private static final Logger log = LoggerFactory.getLogger(AuthColPermissionSigner.class);

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * 签名密钥（AppSecret）。
     */
    private final String secretKey;

    /**
     * 构造签名器。
     *
     * @param secretKey 签名密钥，为空时签名校验将被跳过
     */
    public AuthColPermissionSigner(String secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * 生成列权限数据的 HMAC-SHA256 签名。
     *
     * <p>签名数据源：{@code visibleColumns + "|" + editableColumns}
     *
     * @param visibleColumns 可见列规则（如 "table1:col1,col2;table2:col3"）
     * @param editableColumns 可编辑列规则（格式同 visibleColumns）
     * @return 十六进制编码的签名值；若密钥为空则返回空字符串
     */
    public String generateSign(String visibleColumns, String editableColumns) {
        if (StringUtils.isBlank(secretKey)) {
            log.debug("列权限签名密钥未配置，跳过签名生成");
            return "";
        }
        String data = buildSignData(visibleColumns, editableColumns);
        return hmacSha256(data, secretKey);
    }

    /**
     * 校验列权限数据的签名是否合法。
     *
     * <p>校验逻辑：
     * <ol>
     *   <li>若密钥未配置，跳过校验（返回 true）</li>
     *   <li>若传入签名为空，视为校验失败</li>
     *   <li>使用相同密钥重新计算签名，与传入签名做恒定时间比较</li>
     * </ol>
     *
     * @param visibleColumns 可见列规则
     * @param editableColumns 可编辑列规则
     * @param receivedSign 客户端传入的签名值
     * @return 校验是否通过
     * @throws SecurityException 签名不匹配时抛出
     */
    public boolean verifySign(String visibleColumns, String editableColumns, String receivedSign) {
        if (StringUtils.isBlank(secretKey)) {
            log.debug("列权限签名密钥未配置，跳过签名校验");
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
     * 判断当前签名器是否处于激活状态（即密钥已配置）。
     *
     * @return 密钥非空时返回 true
     */
    public boolean isEnabled() {
        return StringUtils.isNotBlank(secretKey);
    }

    /**
     * 构建签名原始数据。
     *
     * @param visibleColumns 可见列规则
     * @param editableColumns 可编辑列规则
     * @return 拼接后的签名数据源
     */
    private String buildSignData(String visibleColumns, String editableColumns) {
        String visible = visibleColumns != null ? visibleColumns : "";
        String editable = editableColumns != null ? editableColumns : "";
        return visible + "|" + editable;
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
}
