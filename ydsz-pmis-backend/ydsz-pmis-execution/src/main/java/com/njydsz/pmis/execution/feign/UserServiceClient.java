package com.njydsz.pmis.execution.feign;

import com.njydsz.pmis.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 用户服务 Feign 客户端（执行模块专用）
 *
 * <p>用于 NameAssembler 解析员工姓名、内部成本费率查询等跨模块场景；
 * user 服务不可用时由 {@link UserServiceClientFallback} 返回降级值。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@FeignClient(name = "ydsz-pmis-user", fallbackFactory = UserServiceClientFallback.class)
public interface UserServiceClient {

    /**
     * 按员工 ID 查询员工基本信息
     *
     * @param id 员工 ID
     * @return 员工信息（含姓名 / 部门 / 职级等）
     */
    @GetMapping("/api/v1/user/employee/{id}")
    R<Map<String, Object>> getEmployee(@PathVariable("id") Long id);

    /**
     * 按职级编码查询内部成本费率
     *
     * @param levelCode 职级编码（如 L4、L5）
     * @return 内部日费率；服务降级时返回 0
     */
    @GetMapping("/api/v1/user/employee/level-rate")
    R<BigDecimal> getLevelRate(@RequestParam("levelCode") String levelCode);
}
