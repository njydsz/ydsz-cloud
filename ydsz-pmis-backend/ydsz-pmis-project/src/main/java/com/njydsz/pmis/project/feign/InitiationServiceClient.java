package com.njydsz.pmis.project.feign;

import com.njydsz.pmis.common.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * 项目（立项）服务 Feign 客户端（执行模块专用）
 * <p>
 * 用途：采购/费用强管控需要读取立项预算金额，避免在执行模块直接访问项目表
 * </p>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@FeignClient(name = "ydsz-pmis-project", fallbackFactory = InitiationServiceClientFallback.class)
public interface InitiationServiceClient {

    /**
     * 查询立项预算快照
     *
     * @param id 立项 ID
     * @return {initiationId, projectCode, projectName, budgetAmount, estimatedAmount, stage}
     */
    @GetMapping("/project/initiation/{id}/budget/snapshot")
    Result<Map<String, Object>> budgetSnapshot(@PathVariable("id") Long id);
}
