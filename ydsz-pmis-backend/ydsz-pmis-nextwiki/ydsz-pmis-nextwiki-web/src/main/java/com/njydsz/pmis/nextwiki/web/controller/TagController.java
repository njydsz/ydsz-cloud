package com.njydsz.pmis.nextwiki.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.nextwiki.api.dto.NextwikiDTOs;
import com.njydsz.pmis.nextwiki.domain.entity.Tag;
import com.njydsz.pmis.nextwiki.domain.service.TagDomainService;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 标签管理 REST API
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@RestController
@RequestMapping("/nextwiki/tags")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "标签管理", description = "标签创建、绑定、推荐")
// FQN-OK: name conflict with Tag entity
public class TagController {

    private final TagDomainService tagDomainService;

    @PostMapping
    @Operation(summary = "创建标签")
    public BaseResponse<Tag> createTag(
            @RequestBody NextwikiDTOs.CreateTagRequest request,
            @RequestHeader("X-User-Id") String userId) {
        Tag tag = tagDomainService.createTag(request.getName(), request.getColor(), userId);
        return BaseResponse.ok(tag);
    }

    @GetMapping
    @Operation(summary = "查询所有标签")
    public BaseResponse<List<Tag>> listTags() {
        return BaseResponse.ok(tagDomainService.getAllTags());
    }

    @PostMapping("/bind")
    @Operation(summary = "为文件绑定标签")
    public BaseResponse<Void> bindTag(
            @RequestBody NextwikiDTOs.BindTagRequest request,
            @RequestHeader("X-User-Id") String userId) {
        tagDomainService.batchBindTags(request.getFileNodeId(), request.getTagIds(), userId);
        return BaseResponse.ok();
    }

    @GetMapping("/file/{fileNodeId}")
    @Operation(summary = "查询文件的标签")
    public BaseResponse<List<Tag>> getFileTags(@PathVariable String fileNodeId) {
        return BaseResponse.ok(tagDomainService.getFileTags(fileNodeId));
    }

    @GetMapping("/recommend/{fileNodeId}")
    @Operation(summary = "推荐标签")
    public BaseResponse<List<Tag>> recommendTags(@PathVariable String fileNodeId) {
        return BaseResponse.ok(tagDomainService.recommendTags(fileNodeId));
    }
}
