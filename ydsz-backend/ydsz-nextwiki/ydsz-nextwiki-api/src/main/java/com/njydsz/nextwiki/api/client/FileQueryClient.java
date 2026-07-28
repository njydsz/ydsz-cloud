package com.njydsz.nextwiki.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.nextwiki.api.fallback.FileQueryClientFallback;

/**
 * 文件查询 Feign 客户端（供跨服务调用）。
 *
 * <p>提供文件信息和下载 URL 的远程查询能力。
 * 典型场景：Agent 服务检索知识库文件、工作流模块查询流程附件等。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FeignClient(name = FeignClientConstants.NEXTWIKI, contextId = "fileQueryClient",
        fallbackFactory = FileQueryClientFallback.class)

/**
 * FileQueryClient Feign 客户端接口，声明跨服务远程调用。
 *
 * <p>所属包：{@code com.njydsz.nextwiki.api.client}
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FileQueryClient {

    /**
     * 按文件 ID 查询文件名称。
     *
     * @param fileId 文件 ID
     * @return 文件名称；不存在时返回 null
     */
    @GetMapping(FeignClientConstants.NEXTWIKI_PATH_FILE_GET)
    BaseResponse<String> getFileName(@RequestParam String fileId);

    /**
     * 获取文件下载 URL（预签名 URL）。
     *
     * @param fileId 文件 ID
     * @return 预签名下载 URL；不存在时返回 null
     */
    @GetMapping(FeignClientConstants.NEXTWIKI_PATH_FILE_URL)
    BaseResponse<String> getFileUrl(@RequestParam String fileId);
}
