package com.njydsz.pmis.common.feign;

import com.njydsz.pmis.common.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 用户服务统一 Feign 客户端（P1 架构优化：合并 project + system 两个版本）。
 *
 * <p>用于 NameAssembler 解析员工/客户名称、内部成本费率查询、通知模块获取接收人邮箱等跨模块场景；
 * userinfo 服务不可用时由 {@link UserServiceClientFallback} 返回降级值。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@FeignClient(
        name = FeignClientConstants.USERINFO,
        contextId = "commonUserServiceClient",
        fallbackFactory = UserServiceClientFallback.class
)
public interface UserServiceClient {

    /**
     * 按员工 ID 查询员工基本信息（含 email/phone/姓名/部门/职级等）
     *
     * @param id 员工 ID
     * @return 员工信息
     */
    @GetMapping("/user/employee/{id}")
    Result<Map<String, Object>> getEmployee(@PathVariable("id") String id);

    /**
     * 根据客户 ID 查询客户名称
     *
     * @param customerId 客户 ID
     * @return 客户名称；服务降级时返回空字符串
     */
    @GetMapping("/user/customers/name")
    Result<String> getCustomerName(@RequestParam("customerId") String customerId);

    /**
     * 批量查询员工姓名
     *
     * @param ids 员工 ID 列表
     * @return 员工 ID 到姓名的映射；服务降级时返回空 Map
     */
    @GetMapping("/user/employees/batch")
    Result<Map<String, String>> batchEmployeeName(@RequestParam("ids") List<String> ids);

    /**
     * 批量查询客户名称
     *
     * @param customerIds 客户 ID 列表
     * @return 客户 ID 到名称的映射
     */
    @GetMapping("/user/customers/batchName")
    Result<Map<String, String>> batchCustomerName(@RequestParam("ids") List<String> customerIds);

    /**
     * 按职级编码查询内部成本费率
     *
     * @param levelCode 职级编码（如 L4、L5）
     * @return 内部日费率；服务降级时返回 0
     */
    @GetMapping("/user/employee/levelRate")
    Result<BigDecimal> getLevelRate(@RequestParam("levelCode") String levelCode);
}