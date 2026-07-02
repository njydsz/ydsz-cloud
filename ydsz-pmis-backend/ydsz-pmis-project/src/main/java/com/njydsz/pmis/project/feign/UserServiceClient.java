package com.njydsz.pmis.project.feign;

import com.njydsz.pmis.common.api.Result;
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

    /**
     * 根据员工 ID 查询员工信息。
     *
     * @param id 员工 ID
     * @return 员工信息（包含 name/realName 等字段）；服务降级时返回 null
     */
    @GetMapping("/api/v1/user/employee/{id}")
    Result<Map<String, Object>> getEmployee(@PathVariable("id") Long id);

    /**
     * 根据客户 ID 查询客户名称。
     *
     * @param customerId 客户 ID
     * @return 客户名称；服务降级时返回空字符串
     */
    @GetMapping("/api/v1/user/customers/name")
    Result<String> getCustomerName(@RequestParam("customerId") Long customerId);

    /**
     * 批量查询员工姓名。
     *
     * @param ids 员工 ID 列表
     * @return 员工 ID 到姓名的映射；服务降级时返回空 Map
     */
    @GetMapping("/api/v1/user/employees/batch")
    Result<Map<Long, String>> batchEmployeeName(@RequestParam("ids") List<Long> ids);
}
