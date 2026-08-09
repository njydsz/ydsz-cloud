package com.njydsz.system.api.client;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.system.api.fallback.DictClientFallback;

/**
 * 字典查询 Feign 客户端（供跨服务调用）。
 *
 * <p>提供字典项的远程查询能力，走 Redis 缓存。
 * 典型场景：工作流模块解析「审批状态」「审批类型」等字典项，
 * 项目模块查询「项目类型」「合同类型」等字典项。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FeignClient(name = FeignClientConstants.SYSTEM, contextId = "dictClient",
        fallbackFactory = DictClientFallback.class)

public interface DictClient {

    /**
     * 按类型编码和字典项编码查询字典项（走缓存）。
     *
     * @param request 请求体（必须包含 {@code typeCode} 和 {@code itemCode} 字段）
     * @return 字典项值（itemValue 字段）；不存在时返回 null
     */
    @PostMapping(FeignClientConstants.SYSTEM_PATH_DICT_ITEM)
    BaseResponse<String> getDictItem(@RequestBody Map<String, String> request);

    /**
     * 按字典类型编码查询全部字典项列表（走缓存）。
     *
     * @param typeCode 字典类型编码
     * @return 字典项值列表
     */
    @PostMapping(FeignClientConstants.SYSTEM_PATH_DICT_LIST)
    BaseResponse<List<String>> listDictItems(@RequestParam("typeCode") String typeCode);
}
