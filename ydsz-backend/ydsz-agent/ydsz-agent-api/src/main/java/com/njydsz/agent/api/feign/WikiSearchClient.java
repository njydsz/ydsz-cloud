package com.njydsz.agent.api.feign;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.njydsz.common.core.response.BaseResponse;

/**
 * Agent → Nextwiki Feign 客户端。
 *
 * <p>Agent 模块通过此客户端调用 nextwiki 服务，检索知识库文档内容，
 * 用于 RAG（Retrieval-Augmented Generation）知识增强。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FeignClient(name = "ydsz-nextwiki", contextId = "wikiSearchClient")
public interface WikiSearchClient {

    /**
     * 搜索知识库文档。
     *
     * @param query    搜索关键词
     * @param topK     返回结果数上限
     * @return 搜索结果列表（每项包含 fileId, title, snippet 字段）
     */
    @GetMapping("/api/v1/nextwiki/search")
    BaseResponse<List<Map<String, Object>>> search(
            @RequestParam("query") String query,
            @RequestParam(value = "topK", defaultValue = "5") int topK);

    /**
     * 根据文件 ID 获取文件元数据。
     *
     * @param fileId 文件 ID
     * @return 文件元数据（含 fileName, fileSize, contentType 等）
     */
    @GetMapping("/api/v1/nextwiki/files/{fileId}")
    BaseResponse<Map<String, Object>> getFileMeta(@org.springframework.web.bind.annotation.PathVariable("fileId") String fileId);
}
