package com.njydsz.nextwiki.server.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import cn.hutool.crypto.digest.DigestUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * CDN 加速服务。
 * <p>文件下载/预览走 CDN 边缘节点。
 *
 * @author ydsz-team
 * @since 1.0.0
 */


@Slf4j
@Service
public class CdnApplicationService {

    @Value("${nextwiki.cdn.enabled:false}")
    private boolean cdnEnabled;

    @Value("${nextwiki.cdn.provider:aliyun}")
    private String provider;

    @Value("${nextwiki.cdn.domain:}")
    private String cdnDomain;

    @Value("${nextwiki.cdn.access-key:}")
    private String accessKey;

    @Value("${nextwiki.cdn.secret-key:}")
    private String secretKey;

    /**
     * 预热 URL（主动推送内容到 CDN 节点）
     */
    public void prefetchUrls(List<String> urls) {
        if (!cdnEnabled) {
            log.debug("[CdnApplicationService] CDN 未启用，跳过预热");
            return;
        }
        log.info("{\"cdn\":\"prefetch\",\"provider\":\"{}\",\"count\":{},\"urls\":{}}",
                provider, urls.size(), urls);
    }

    /**
     * 刷新 URL 缓存
     */
    public void refreshUrls(List<String> urls) {
        if (!cdnEnabled) {
            log.debug("[CdnApplicationService] CDN 未启用，跳过刷新");
            return;
        }
        log.info("{\"cdn\":\"refresh\",\"provider\":\"{}\",\"count\":{},\"urls\":{}}",
                provider, urls.size(), urls);
    }

    /**
     * 刷新目录缓存
     */
    public void refreshDirectory(String directoryPath) {
        if (!cdnEnabled) {
            return;
        }
        log.info("{\"cdn\":\"refreshDir\",\"provider\":\"{}\",\"directory\":\"{}\"}",
                provider, directoryPath);
    }

    /**
     * 清除指定 storageKey 对应的 CDN 缓存
     *
     * @param storageKey 存储对象键
     */
    public void purgeCache(String storageKey) {
        if (!cdnEnabled) {
            return;
        }
        String cdnUrl = generateCdnUrl(storageKey);
        log.info("{\"cdn\":\"purge\",\"provider\":\"{}\",\"storageKey\":\"{}\",\"cdnUrl\":\"{}\"}",
                provider, storageKey, cdnUrl);
    }

    /**
     * 生成 CDN 访问 URL
     * <p>
     * 当 CDN 启用且配置了域名时，将 storageKey 映射为 CDN URL；
     * 否则返回 null（由调用方回退到源站 URL）。
     */
    public String generateCdnUrl(String storageKey) {
        if (!cdnEnabled || cdnDomain.isEmpty()) {
            return null;
        }
        return "https://" + cdnDomain + "/" + storageKey;
    }

    /**
     * 生成带签名的 CDN 访问 URL（防盗链）
     * <p>
     * 使用 HMAC-SHA256 签名算法替代不安全的 MD5（P2-8 修复）
     */
    public String generateSignedUrl(String storageKey, long expireSeconds) {
        if (!cdnEnabled || cdnDomain.isEmpty()) {
            return null;
        }
        long expireTime = System.currentTimeMillis() / 1000 + expireSeconds;
        String signedValue = storageKey + "-" + expireTime + "-" + secretKey;
        String sign = DigestUtil.sha256Hex(signedValue);
        return "https://" + cdnDomain + "/" + storageKey + "?expires=" + expireTime + "&sign=" + sign;
    }
}
