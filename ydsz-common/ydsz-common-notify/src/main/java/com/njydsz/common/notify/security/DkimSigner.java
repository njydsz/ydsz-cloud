package com.njydsz.common.notify.security;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import jakarta.mail.internet.InternetHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import com.njydsz.common.notify.config.NotifyProperties;

/**
 * DKIM 邮件签名支持（P2-9）
 *
 * <p>DKIM（DomainKeys Identified Mail）通过 RSA 私钥对邮件头+正文进行签名， 收件方通过 DNS 中的公钥记录验证签名，防止邮件被篡改或伪造。
 *
 * <p><b>签名流程：</b>
 *
 * <ol>
 *   <li>选择需要签名的邮件头字段（From、To、Subject、Date 等）
 *   <li>构建 DKIM-Signature 头的规范数据（domain、selector、算法等）
 *   <li>对邮件头规范数据和正文规范数据进行 RSA-SHA256 签名
 *   <li>将签名结果以 Base64 编码写入 DKIM-Signature 头
 * </ol>
 *
 * <p><b>配置示例：</b>
 *
 * <pre>{@code
 * ydsz:
 *   notify:
 *     email:
 *       dkim:
 *         enabled: true
 *         domain: ydsz.com
 *         selector: default
 *         private-key: "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQ..."
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class DkimSigner {

  private static final Logger LOG = LoggerFactory.getLogger(DkimSigner.class);

  /** DKIM-Signature 头名称 */
  public static final String DKIM_HEADER = "DKIM-Signature";

  private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
  private static final String CANONICALIZATION = "relaxed/relaxed";

  /** 需要签名的邮件头字段 */
  private static final String[] SIGNED_HEADERS = {
    "From", "To", "Subject", "Date", "Message-ID", "MIME-Version"
  };

  private final NotifyProperties properties;
  private volatile PrivateKey privateKey;
  private volatile boolean initialized = false;

  public DkimSigner(NotifyProperties properties) {
    this.properties = properties;
  }

  /**
   * 判断 DKIM 签名是否启用
   *
   * @return true 表示 DKIM 已启用且配置完整
   */
  public boolean isDkimEnabled() {
    NotifyProperties.EmailConfig email = properties.getEmail();
    if (email == null || email.getDkim() == null) {
      return false;
    }
    NotifyProperties.DkimConfig dkim = email.getDkim();
    return dkim.isEnabled()
        && StringUtils.hasText(dkim.getDomain())
        && StringUtils.hasText(dkim.getSelector())
        && StringUtils.hasText(dkim.getPrivateKey());
  }

  /**
   * 为邮件头生成 DKIM 签名
   *
   * @param headers 邮件头
   * @param body 邮件正文
   * @return DKIM-Signature 头值，未启用时返回 null
   */
  public String generateDkimSignature(InternetHeaders headers, byte[] body) {
    if (!isDkimEnabled()) {
      return null;
    }
    try {
      PrivateKey key = getPrivateKey();
      if (key == null) {
        return null;
      }

      NotifyProperties.DkimConfig dkim = properties.getEmail().getDkim();

      // 构建签名数据
      StringBuilder signingData = new StringBuilder();
      for (String headerName : SIGNED_HEADERS) {
        String[] values = headers.getHeader(headerName);
        if (values != null && values.length > 0) {
          signingData
              .append(headerName.toLowerCase())
              .append(":")
              .append(values[0].trim())
              .append("\r\n");
        }
      }

      // 添加 DKIM-Signature 头本身（部分字段）
      String dkimHeaderPartial =
          "v=1; a=rsa-sha256; c="
              + CANONICALIZATION
              + "; d="
              + dkim.getDomain()
              + "; s="
              + dkim.getSelector()
              + "; h="
              + String.join(":", SIGNED_HEADERS)
              + ";";
      signingData.append(dkimHeaderPartial).append(" b=");

      // 签名数据 = 头部规范数据 + 正文的 SHA-256 摘要
      byte[] signingBytes = signingData.toString().getBytes(StandardCharsets.UTF_8);

      // 对正文做 relaxed 规范化（简化版：去除尾部空行）
      String bodyStr = new String(body, StandardCharsets.UTF_8);
      bodyStr = bodyStr.replaceAll("\\s+$", "");
      byte[] bodyCanonical = bodyStr.getBytes(StandardCharsets.UTF_8);

      // 合并签名数据
      byte[] combined = new byte[signingBytes.length + bodyCanonical.length];
      System.arraycopy(signingBytes, 0, combined, 0, signingBytes.length);
      System.arraycopy(bodyCanonical, 0, combined, signingBytes.length, bodyCanonical.length);

      // RSA-SHA256 签名
      Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
      sig.initSign(key);
      sig.update(combined);
      byte[] signature = sig.sign();

      String signatureBase64 = Base64.getEncoder().encodeToString(signature);

      String fullDkimHeader = dkimHeaderPartial + " " + signatureBase64;
      LOG.debug(
          "[DkimSigner] DKIM 签名生成成功: domain={}, selector={}", dkim.getDomain(), dkim.getSelector());
      return fullDkimHeader;

    } catch (Exception e) {
      LOG.error("[DkimSigner] DKIM 签名生成失败: {}", e.getMessage(), e);
      return null;
    }
  }

  /** 懒加载 RSA 私钥 */
  private PrivateKey getPrivateKey() {
    if (initialized) {
      return privateKey;
    }
    synchronized (this) {
      if (initialized) {
        return privateKey;
      }
      try {
        NotifyProperties.DkimConfig dkim = properties.getEmail().getDkim();
        String keyStr = dkim.getPrivateKey();
        // 移除 PEM 头尾标记
        keyStr =
            keyStr
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(keyStr);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        privateKey = keyFactory.generatePrivate(keySpec);
        LOG.info("[DkimSigner] RSA 私钥加载成功");
      } catch (Exception e) {
        LOG.error("[DkimSigner] RSA 私钥加载失败: {}", e.getMessage());
        privateKey = null;
      }
      initialized = true;
      return privateKey;
    }
  }
}
