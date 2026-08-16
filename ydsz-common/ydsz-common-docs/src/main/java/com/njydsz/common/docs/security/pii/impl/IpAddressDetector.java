package com.njydsz.common.docs.security.pii.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.domain.PiiFinding;
import com.njydsz.common.docs.enums.PiiType;
import com.njydsz.common.docs.security.pii.PiiDetector;

/**
 * IP 地址检测器（IPv4）
 * <p>
 * 检测 IPv4 地址（如 {@code 192.168.1.1}），不检测 IPv6。
 *
 * <p><b>使用场景：</b>服务器日志、错误堆栈中常包含内网 IP，
 * 外发文档时需识别并脱敏，避免泄露网络拓扑。对 RFC1918 私网段
 * （10.x、172.16-31.x、192.168.x）给予较高置信度 0.9，
 * 其他公网 IP 视为低频敏感，给 0.5。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
public class IpAddressDetector implements PiiDetector {

    /** IPv4 地址正则（0-255 限制） */
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "\\b(?:(?:25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\b");

    /**
     * 扫描全文中的 IPv4 地址，按是否为私网段分配置信度。
     *
     * <p>公网 IP 虽也属敏感，但误报率较高（如版本号 1.0.0.1 也会命中），
     * 因此仅对 RFC1918 私网段给予 0.9 高置信度，公网 IP 降为 0.5。
     * {@code 0.0.0.0}、{@code 255.255.255.255} 等特殊地址<b>不会</b>命中。
     *
     * <p>返回的下标基于预处理后的文本，脱敏时须使用同一份文本。
     *
     * @param content 文档内容；为 {@code null} 或其 text 为 {@code null} 时返回空列表，不抛异常
     * @return PII 发现列表；无命中时返回空列表而非 {@code null}
     */
    @Override
    public List<PiiFinding> detect(DocumentContent content) {
        if (content == null || content.getText() == null) {
            return List.of();
        }

        String text = content.getText();
        List<PiiFinding> findings = new ArrayList<>();
        Matcher matcher = IPV4_PATTERN.matcher(text);

        while (matcher.find()) {
            String matched = matcher.group();
            double confidence = isPrivateIp(matched) ? 0.9 : 0.5;
            findings.add(PiiFinding.builder()
                    .type(PiiType.IP_ADDRESS)
                    .maskedValue(mask(matched))
                    .startIndex(matcher.start())
                    .endIndex(matcher.end())
                    .confidence(confidence)
                    .build());
        }

        return findings;
    }

    /**
     * 声明本检测器负责的 PII 类别。
     *
     * @return 恒为 {@link PiiType#IP_ADDRESS}
     */
    @Override
    public PiiType getSupportedType() {
        return PiiType.IP_ADDRESS;
    }

    /**
     * 对 IPv4 地址做保留首尾段脱敏。
     *
     * <p>例如 {@code 192.168.1.100} 脱敏为 {@code 192.***.***.100}，
     * 使运维仍可辨识大致网段，同时隐藏主机位。
     *
     * @param matchedText 命中的 IPv4 地址；格式非法时不校验直接返回 {@code "***"}
     * @return 脱敏后的 IP 串；输入过短时返回 {@code "***"}
     */
    @Override
    public String mask(String matchedText) {
        if (matchedText == null || matchedText.length() < 7) {
            return "***";
        }
        String[] parts = matchedText.split("\\.");
        if (parts.length != 4) {
            return "***";
        }
        return parts[0] + ".***.***." + parts[3];
    }

    /**
     * 判断是否为 RFC1918 定义的私有地址空间。
     *
     * <p>覆盖 {@code 10.0.0.0/8}、{@code 172.16.0.0/12}、{@code 192.168.0.0/16} 三个区间。
     * {@code 127.0.0.0/8} 回环地址不视为私网（非敏感）。
     *
     * @param ip 待判定的 IPv4 地址
     * @return 私网地址返回 {@code true}；公网或 {@code null} 返回 {@code false}
     */
    private boolean isPrivateIp(String ip) {
        if (ip == null) {
            return false;
        }
        if (ip.startsWith("10.")) {
            return true;
        }
        if (ip.startsWith("192.168.")) {
            return true;
        }
        if (ip.startsWith("172.")) {
            String[] parts = ip.split("\\.");
            if (parts.length >= 2) {
                try {
                    int secondOctet = Integer.parseInt(parts[1]);
                    return secondOctet >= 16 && secondOctet <= 31;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        }
        return false;
    }
}
