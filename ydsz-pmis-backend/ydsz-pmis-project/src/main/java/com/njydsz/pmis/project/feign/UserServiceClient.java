package com.njydsz.pmis.project.feign;

import com.njydsz.pmis.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 用户服务 Feign 客户端
 *
 * <p>用于装配用户名/客户名称等冗余字段，调用失败时降级返回空。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@FeignClient(name = "ydsz-pmis-user", fallbackFactory = UserServiceClientFallback.class)
public interface UserServiceClient {

    @GetMapping("/api/v1/user/employee/{id}")
    R<Map<String, Object>> getEmployee(@PathVariable("id") Long id);

    @GetMapping("/api/v1/user/customers/name")
    R<String> getCustomerName(@RequestParam("customerId") Long customerId);

    @GetMapping("/api/v1/user/employees/batch")
    R<Map<Long, String>> batchEmployeeName(@RequestParam("ids") List<Long> ids);
}
