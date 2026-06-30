package com.njydsz.pmis.execution.feign;

import com.njydsz.pmis.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 用户服务 Feign 客户端（执行模块专用）
 */
@FeignClient(name = "ydsz-pmis-user", fallbackFactory = UserServiceClientFallback.class)
public interface UserServiceClient {

    @GetMapping("/api/v1/user/employee/{id}")
    R<Map<String, Object>> getEmployee(@PathVariable("id") Long id);

    @GetMapping("/api/v1/user/employee/level-rate")
    R<java.math.BigDecimal> getLevelRate(@RequestParam("levelCode") String levelCode);
}
