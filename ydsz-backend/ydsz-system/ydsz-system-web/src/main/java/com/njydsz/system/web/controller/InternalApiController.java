package com.njydsz.system.web.controller;

import java.util.Map;

import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.SentinelRateLimit;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.system.domain.vo.DictItemVO;
import com.njydsz.system.server.service.AppInfoService;
import com.njydsz.system.server.service.ConfigService;
import com.njydsz.system.server.service.DictItemService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Internal API controller for cross-service Feign calls.
 *
 * <p>These endpoints are intended for service-to-service communication only.
 * Gateway should restrict access to {@code /api/internal/**} paths.
 *
 * <p>安全要求：
 * <ul>
 *   <li>appSecret 通过 POST body 传输，不暴露在 URL 中</li>
 *   <li>Gateway 应限制 /api/internal/** 仅允许内部服务调用</li>
 * </ul>
 *
 * @author ydsz-team
 */
@Slf4j
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalApiController {

    private final ConfigService configService;
    private final DictItemService dictItemService;
    private final AppInfoService appInfoService;

    /**
     * 按配置键查询配置值（走缓存）。
     *
     * @param request 包含 key 的请求体
     * @return 配置值，不存在返回 null
     */
    @SentinelRateLimit(resource = "system.internalapi.getConfig", threshold = 50)
    @Idempotent(key = 'system:internalapi:getConfig', ttlSeconds = 5, message = "请勿重复提交")
    @Idempotent(key = "ydsz:system:InternalApiController:getConfig:lock", ttlSeconds = 5)
    @PostMapping("/config/get")
    public String getConfig(@RequestBody Map<String, String> request) {
        return configService.getConfigValue(request.get("key"));
    }

    /**
     * 按类型编码和字典项编码查询字典项（走缓存）。
     *
     * @param request 包含 typeCode 和 itemCode 的请求体
     * @return 字典项 VO
     */
    @SentinelRateLimit(resource = "system.internalapi.getDictItem", threshold = 50)
    @Idempotent(key = 'system:internalapi:getDictItem', ttlSeconds = 5, message = "请勿重复提交")
    @Idempotent(key = "ydsz:system:InternalApiController:getDictItem:lock", ttlSeconds = 5)
    @PostMapping("/dict/item")
    public DictItemVO getDictItem(@RequestBody Map<String, String> request) {
        return dictItemService.getByTypeAndCode(request.get("typeCode"), request.get("itemCode"));
    }

    /**
     * 校验应用密钥（BCrypt）。密钥通过 POST body 传输，不暴露在 URL 中。
     *
     * @param request 包含 appKey 和 appSecret 的请求体
     * @return 校验通过返回 true
     */
    @SentinelRateLimit(resource = "system.internalapi.validateClient", threshold = 50)
    @Idempotent(key = 'system:internalapi:validateClient', ttlSeconds = 5, message = "请勿重复提交")
    @Idempotent(key = "ydsz:system:InternalApiController:validateClient:lock", ttlSeconds = 5)
    @PostMapping("/app/validate")
    public boolean validateClient(@RequestBody Map<String, String> request) {
        return appInfoService.validateClient(request.get("appKey"), request.get("appSecret"));
    }
}
