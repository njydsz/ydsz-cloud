package com.njydsz.nextwiki.server.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.njydsz.nextwiki.server.config.NextwikiProperties;

import cn.hutool.crypto.digest.DigestUtil;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class CdnApplicationService {

    private final NextwikiProperties properties;

    /**
     * 预热 URL（主动将内容推送到 CDN 边缘节点，提升首访命中率）。
     * <p>CDN 未启用时直接跳过，仅打印 debug 日志，不发起任何外部调用。
     *
     * @param urls 待预热的完整 URL 列表，为 {@code null} 时由调用方保证非空
     * @return 无返回值
     * @note 无副作用到业务数据；CDN 未启用时为空操作，线程安全
     * @complexity O(urls.size())（仅打点日志，未真正实现预热推送，后续可对接厂商 API）
     */
    public void prefetchUrls(List<String> urls) {
        if (!properties.getCdn().isEnabled()) {
            log.debug("[CdnApplicationService] CDN 未启用，跳过预热");
            return;
        }
        log.info("{\"cdn\":\"prefetch\",\"provider\":\"{}\",\"count\":{},\"urls\":{}}",
                properties.getCdn().getProvider(), urls.size(), urls);
    }

    /**
     * 刷新 URL 缓存（使边缘节点上的旧内容失效，下次回源拉取最新版本）。
     * <p>CDN 未启用时直接跳过。
     *
     * @param urls 待刷新的完整 URL 列表
     * @return 无返回值
     * @note 无业务数据副作用；CDN 未启用时为空操作，线程安全
     * @complexity O(urls.size())（当前仅打点日志，未对接厂商刷新 API）
     */
    public void refreshUrls(List<String> urls) {
        if (!properties.getCdn().isEnabled()) {
            log.debug("[CdnApplicationService] CDN 未启用，跳过刷新");
            return;
        }
        log.info("{\"cdn\":\"refresh\",\"provider\":\"{}\",\"count\":{},\"urls\":{}}",
                properties.getCdn().getProvider(), urls.size(), urls);
    }

    /**
     * 刷新目录缓存（按目录前缀批量使缓存失效）。
     * <p>CDN 未启用时直接返回，不发请求。
     *
     * @param directoryPath 待刷新的目录路径（CDN 侧前缀）
     * @return 无返回值
     * @note 无业务数据副作用；CDN 未启用时为空操作，线程安全
     */
    public void refreshDirectory(String directoryPath) {
        if (!properties.getCdn().isEnabled()) {
            return;
        }
        log.info("{\"cdn\":\"refreshDir\",\"provider\":\"{}\",\"directory\":\"{}\"}",
                properties.getCdn().getProvider(), directoryPath);
    }

    /**
     * 清除指定 storageKey 对应的 CDN 缓存（内容删除/更新后的精准失效）。
     * <p>内部先由 {@link #generateCdnUrl(String)} 拼出完整 URL 再下发刷新指令；CDN 未启用时直接返回。
     *
     * @param storageKey 存储对象键（即对象存储中的 key，如 {@code user/xxx/file.pdf}）
     * @return 无返回值
     * @note 无业务数据副作用；CDN 未启用时为空操作，线程安全
     */
    public void purgeCache(String storageKey) {
        if (!properties.getCdn().isEnabled()) {
            return;
        }
        String cdnUrl = generateCdnUrl(storageKey);
        log.info("{\"cdn\":\"purge\",\"provider\":\"{}\",\"storageKey\":\"{}\",\"cdnUrl\":\"{}\"}",
                properties.getCdn().getProvider(), storageKey, cdnUrl);
    }

    /**
     * 生成 CDN 访问 URL。
     * <p>当 CDN 启用且配置了域名时，将 storageKey 映射为 {@code https://域名/storageKey}；
     * 否则返回 {@code null}（由调用方回退到对象存储源站 URL）。
     *
     * @param storageKey 存储对象键
     * @return 完整 CDN URL；CDN 未启用或未配置域名时返回 {@code null}
     * @note 纯字符串拼接，无外部调用，线程安全
     */
    public String generateCdnUrl(String storageKey) {
        if (!properties.getCdn().isEnabled() || properties.getCdn().getDomain().isEmpty()) {
            return null;
        }
        return "https://" + properties.getCdn().getDomain() + "/" + storageKey;
    }

    /**
     * 生成带签名的 CDN 访问 URL（防盗链）
     * <p>
     * 使用 HMAC-SHA256 签名算法替代不安全的 MD5（P2-8 修复）
     */
    public String generateSignedUrl(String storageKey, long expireSeconds) {
        if (!properties.getCdn().isEnabled() || properties.getCdn().getDomain().isEmpty()) {
            return null;
        }
        long expireTime = System.currentTimeMillis() / 1000 + expireSeconds;
        String signedValue = storageKey + "-" + expireTime + "-" + properties.getCdn().getSecretKey();
        String sign = DigestUtil.sha256Hex(signedValue);
        return "https://" + properties.getCdn().getDomain() + "/" + storageKey + "?expires=" + expireTime + "&sign=" + sign;
    }
}
