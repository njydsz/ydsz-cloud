package com.njydsz.system.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
     * @param key 配置键
     * @return 配置值，不存在返回 null
     */
    @GetMapping("/config/get")
    public String getConfig(@RequestParam String key) {
        return configService.getConfigValue(key);
    }

    /**
     * 按类型编码和字典项编码查询字典项（走缓存）。
     *
     * @param typeCode 字典类型编码
     * @param itemCode 字典项编码
     * @return 字典项 VO
     */
    @GetMapping("/dict/item")
    public DictItemVO getDictItem(@RequestParam String typeCode, @RequestParam String itemCode) {
        return dictItemService.getByTypeAndCode(typeCode, itemCode);
    }

    /**
     * 校验应用密钥（BCrypt）。
     *
     * @param appKey    应用 Key
     * @param appSecret 应用密钥明文
     * @return 校验通过返回 true
     */
    @GetMapping("/app/validate")
    public boolean validateClient(@RequestParam String appKey, @RequestParam String appSecret) {
        return appInfoService.validateClient(appKey, appSecret);
    }
}
