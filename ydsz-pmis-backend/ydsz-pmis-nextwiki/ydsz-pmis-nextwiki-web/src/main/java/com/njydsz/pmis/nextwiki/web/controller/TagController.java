package com.njydsz.pmis.nextwiki.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.njydsz.pmis.common.core.response.Result;
import com.njydsz.pmis.nextwiki.api.dto.NextwikiDTOs;
import com.njydsz.pmis.nextwiki.domain.entity.Tag;
import com.njydsz.pmis.nextwiki.domain.service.TagDomainService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "标签管理", description = "标签创建、绑定、推荐")
public class TagController {

    private final TagDomainService tagDomainService;

    @PostMapping
    @Operation(summary = "创建标签")
    public Result<Tag> createTag(
            @RequestBody NextwikiDTOs.CreateTagRequest request,
            @RequestHeader("X-User-Id") String userId) {
        Tag tag = tagDomainService.createTag(request.getName(), request.getColor(), userId);
        return Result.ok(tag);
    }

    @GetMapping
    @Operation(summary = "查询所有标签")
    public Result<List<Tag>> listTags() {
        return Result.ok(tagDomainService.getAllTags());
    }

    @PostMapping("/bind")
    @Operation(summary = "为文件绑定标签")
    public Result<Void> bindTag(
            @RequestBody NextwikiDTOs.BindTagRequest request,
            @RequestHeader("X-User-Id") String userId) {
        tagDomainService.batchBindTags(request.getFileNodeId(), request.getTagIds(), userId);
        return Result.ok();
    }

    @GetMapping("/file/{fileNodeId}")
    @Operation(summary = "查询文件的标签")
    public Result<List<Tag>> getFileTags(@PathVariable String fileNodeId) {
        return Result.ok(tagDomainService.getFileTags(fileNodeId));
    }

    @GetMapping("/recommend/{fileNodeId}")
    @Operation(summary = "推荐标签")
    public Result<List<Tag>> recommendTags(@PathVariable String fileNodeId) {
        return Result.ok(tagDomainService.recommendTags(fileNodeId));
    }
}
