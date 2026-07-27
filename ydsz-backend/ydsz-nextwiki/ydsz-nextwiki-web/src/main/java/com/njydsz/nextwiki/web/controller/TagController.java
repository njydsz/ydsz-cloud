package com.njydsz.nextwiki.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.nextwiki.api.dto.NextwikiDTOs;
import com.njydsz.nextwiki.domain.entity.Tag;
import com.njydsz.nextwiki.server.service.TagApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 标签管理 REST API
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/tags")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "标签管理", description = "标签创建、绑定、推荐")
// FQN-OK: name conflict with Tag entity
public class TagController {

    private final TagApplicationService tagApplicationService;

    @Audit(module = "标签管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'createTag'")
    @Idempotent(key = "ydsz:nextwiki:TagController:createTag:lock", ttlSeconds = 5)
    @PostMapping
    @Operation(summary = "创建标签")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_TAG_CREATE)
    public BaseResponse<Tag> createTag(
            @RequestBody NextwikiDTOs.CreateTagRequest request,
            @RequestHeader("X-User-Id") String userId) {
        Tag tag = tagApplicationService.createTag(request.getName(), request.getColor(), userId);
        return BaseResponse.success(tag);
    }

    @GetMapping
    @Operation(summary = "查询所有标签")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_TAG_LIST)
    public BaseResponse<List<Tag>> listTags() {
        return BaseResponse.success(tagApplicationService.getAllTags());
    }

    @Audit(module = "标签管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'bindTag'")
    @Idempotent(key = "ydsz:nextwiki:TagController:bindTag:lock", ttlSeconds = 5)
    @PostMapping("/bind")
    @Operation(summary = "为文件绑定标签")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_TAG_BIND)
    public BaseResponse<Void> bindTag(
            @RequestBody NextwikiDTOs.BindTagRequest request,
            @RequestHeader("X-User-Id") String userId) {
        tagApplicationService.batchBindTags(request.getFileNodeId(), request.getTagIds(), userId);
        return BaseResponse.success();
    }

    @GetMapping("/file/{fileNodeId}")
    @Operation(summary = "查询文件的标签")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_TAG_LIST)
    public BaseResponse<List<Tag>> getFileTags(@PathVariable String fileNodeId) {
        return BaseResponse.success(tagApplicationService.getFileTags(fileNodeId));
    }

    @GetMapping("/recommend/{fileNodeId}")
    @Operation(summary = "推荐标签")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_TAG_LIST)
    public BaseResponse<List<Tag>> recommendTags(@PathVariable String fileNodeId) {
        return BaseResponse.success(tagApplicationService.recommendTags(fileNodeId));
    }
}
