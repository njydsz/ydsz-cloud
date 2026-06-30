package com.njydsz.pmis.common.feign;

import com.njydsz.pmis.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 系统配置中心 Feign 客户端
 *
 * <p>供业务模块在不直接依赖 ydsz-pmis-config 模块的前提下读取
 * pmis_config 表中的告警阈值、计算费率等运行时配置。
 *
 * @author ydsz-pmis-team
 */
@FeignClient(name = "ydsz-pmis-config", fallbackFactory = ConfigClientFallback.class)
public interface ConfigClient {

    /** 拉取整组配置（key→value 形式） */
    @GetMapping("/api/v1/configs/group/{group}")
    R<Map<String, String>> getGroup(@PathVariable("group") String group);

    /** 按 group+key 取单条配置 */
    @GetMapping("/api/v1/configs/by-key")
    R<String> getValue(@RequestParam("group") String group, @RequestParam("key") String key);

    /** 公开配置（前端可见） */
    @GetMapping("/api/v1/configs/public")
    R<List<Map<String, Object>>> listPublic();
}
