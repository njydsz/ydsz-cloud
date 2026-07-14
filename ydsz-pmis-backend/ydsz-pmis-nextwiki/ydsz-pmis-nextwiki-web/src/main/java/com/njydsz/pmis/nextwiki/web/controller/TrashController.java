package com.njydsz.pmis.nextwiki.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.nextwiki.domain.entity.TrashItem;
import com.njydsz.pmis.nextwiki.server.service.TrashApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 回收站 REST API
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/trash")
@RequiredArgsConstructor
@Tag(name = "回收站管理", description = "回收站列表、恢复、永久删除、清空")
public class TrashController {

    private final TrashApplicationService trashApplicationService;

    @GetMapping("/list")
    @Operation(summary = "查询回收站列表")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_TRASH_LIST)
    public BaseResponse<List<TrashItem>> list(@RequestHeader("X-User-Id") String userId) {
        return BaseResponse.ok(trashApplicationService.listTrash(userId));
    }

    @PostMapping("/{trashItemId}/restore")
    @Operation(summary = "从回收站恢复")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_TRASH_RESTORE)
    public BaseResponse<Void> restore(
            @PathVariable String trashItemId,
            @RequestHeader("X-User-Id") String userId) {
        trashApplicationService.restore(trashItemId, userId);
        return BaseResponse.ok();
    }

    @PostMapping("/batch-restore")
    @Operation(summary = "批量恢复")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_TRASH_RESTORE)
    public BaseResponse<Void> batchRestore(
            @RequestBody List<String> trashItemIds,
            @RequestHeader("X-User-Id") String userId) {
        trashApplicationService.batchRestore(trashItemIds, userId);
        return BaseResponse.ok();
    }

    @DeleteMapping("/{trashItemId}")
    @Operation(summary = "永久删除")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_TRASH_PURGE)
    public BaseResponse<Void> purge(
            @PathVariable String trashItemId,
            @RequestHeader("X-User-Id") String userId) {
        trashApplicationService.purge(trashItemId, userId);
        return BaseResponse.ok();
    }

    @DeleteMapping("/empty")
    @Operation(summary = "清空回收站")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_TRASH_EMPTY)
    public BaseResponse<Void> emptyTrash(@RequestHeader("X-User-Id") String userId) {
        trashApplicationService.emptyTrash(userId);
        return BaseResponse.ok();
    }
}
