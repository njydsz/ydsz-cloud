package com.njydsz.pmis.project.api.client;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.feign.FeignClientConstants;
import com.njydsz.pmis.project.api.fallback.SalesDataClientFallback;

/**
 * 商务数据查询 Feign 客户端（供 PM/Finance 模块跨域查询商务数据）
 *
 * <p>PM 模块的报表服务通过此客户端查询合同金额等商务聚合数据，
 * 替代原有的直接注入跨域 Mapper 的方式，实现模块间解耦。
 *
 * <p>所有方法均配 {@link SalesDataClientFallback} 降级，销售服务不可用时返回零值。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@FeignClient(
        name = FeignClientConstants.SALES,
        contextId = "salesDataClient",
        fallbackFactory = SalesDataClientFallback.class
)
public interface SalesDataClient {

    /**
     * 合同总金额
     *
     * @return 合同总金额
     */
    @GetMapping("/sales/data/contract/sumAmount")
    BaseResponse<BigDecimal> sumContractAmount();

    /**
     * 按项目查询合同金额
     *
     * @param initiationId 立项 ID
     * @return 合同金额
     */
    @GetMapping("/sales/data/contract/sumByInitiation")
    BaseResponse<BigDecimal> sumContractAmountByInitiation(@RequestParam("initiationId") String initiationId);

    /**
     * 按客户统计合同金额
     *
     * @return 客户维度合同金额列表
     */
    @GetMapping("/sales/data/contract/sumByCustomer")
    BaseResponse<List<Map<String, Object>>> sumContractByCustomer();

    /**
     * 按年度统计合同金额
     *
     * @return 年度维度合同金额列表
     */
    @GetMapping("/sales/data/contract/sumByYear")
    BaseResponse<List<Map<String, Object>>> sumContractByYear();

    /**
     * 按最近月份统计合同金额
     *
     * @param limit 月份数量
     * @return 月度维度合同金额列表
     */
    @GetMapping("/sales/data/contract/sumByRecentMonth")
    BaseResponse<List<Map<String, Object>>> sumContractByRecentMonth(@RequestParam("limit") Integer limit);

    /**
     * 商机总数
     *
     * @return 商机总数
     */
    @GetMapping("/sales/data/opportunity/count")
    BaseResponse<Integer> countOpportunities();

    /**
     * 按项目类型统计合同金额
     *
     * @return 项目类型维度合同金额列表
     */
    @GetMapping("/sales/data/contract/sumByProjectType")
    BaseResponse<List<Map<String, Object>>> sumContractByProjectType();
}
