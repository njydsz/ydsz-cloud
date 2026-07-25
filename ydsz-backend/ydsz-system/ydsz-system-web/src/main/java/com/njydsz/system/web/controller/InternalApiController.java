package com.njydsz.system.web.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.system.domain.entity.ConfigDO;
import com.njydsz.system.domain.entity.DictItemDO;
import com.njydsz.system.domain.entity.AppInfoDO;
import com.njydsz.system.server.service.ConfigService;
import com.njydsz.system.server.service.DictItemService;
import com.njydsz.system.server.service.AppInfoService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Internal API controller for cross-service Feign calls.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalApiController {

    private final ConfigService configService;
    private final DictItemService dictItemService;
    private final AppInfoService appInfoService;

    @GetMapping("/config/get")
    public String getConfig(@RequestParam String key) {
        for (ConfigDO config : configService.list()) {
            if (key.equals(config.getConfigKey())) {
                return config.getConfigValue();
            }
        }
        return null;
    }

    @GetMapping("/dict/item")
    public Map<String, Object> getDictItem(@RequestParam String typeCode, @RequestParam String itemCode) {
        for (DictItemDO item : dictItemService.list()) {
            if (typeCode.equals(item.getTypeCode()) && itemCode.equals(item.getItemCode())) {
                Map<String, Object> result = new HashMap<>();
                result.put("itemCode", item.getItemCode());
                result.put("itemName", item.getItemName());
                result.put("itemValue", item.getItemValue());
                return result;
            }
        }
        return new HashMap<>();
    }

    @GetMapping("/app/validate")
    public boolean validateClient(@RequestParam String appKey, @RequestParam String appSecret) {
        for (AppInfoDO app : appInfoService.list()) {
            if (appKey.equals(app.getAppKey()) && app.getStatus() != null
                    && "ENABLED".equals(app.getStatus())) {
                return true;
            }
        }
        return false;
    }
}
