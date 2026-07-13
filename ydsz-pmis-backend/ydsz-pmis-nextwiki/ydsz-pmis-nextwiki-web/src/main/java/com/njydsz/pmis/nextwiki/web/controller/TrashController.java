package com.njydsz.pmis.nextwiki.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.njydsz.pmis.common.core.response.Result;
import com.njydsz.pmis.nextwiki.domain.entity.TrashItem;
import com.njydsz.pmis.nextwiki.domain.service.TrashDomainService;

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
@RequestMapping("/nextwiki/trash")
@RequiredArgsConstructor
@Tag(name = "回收站", description = "回收站列表、恢复、永久删除、清空")
public class TrashController {

    private final TrashDomainService trashDomainService;

    @GetMapping("/list")
    @Operation(summary = "查询回收站列表")
    public Result<List<TrashItem>> list(@RequestHeader("X-User-Id") String userId) {
        return Result.ok(trashDomainService.listTrash(userId));
    }

    @PostMapping("/{trashItemId}/restore")
    @Operation(summary = "从回收站恢复")
    public Result<Void> restore(
            @PathVariable String trashItemId,
            @RequestHeader("X-User-Id") String userId) {
        trashDomainService.restore(trashItemId, userId);
        return Result.ok();
    }

    @PostMapping("/batch-restore")
    @Operation(summary = "批量恢复")
    public Result<Void> batchRestore(
            @RequestBody List<String> trashItemIds,
            @RequestHeader("X-User-Id") String userId) {
        trashDomainService.batchRestore(trashItemIds, userId);
        return Result.ok();
    }

    @DeleteMapping("/{trashItemId}")
    @Operation(summary = "永久删除")
    public Result<Void> purge(
            @PathVariable String trashItemId,
            @RequestHeader("X-User-Id") String userId) {
        trashDomainService.purge(trashItemId, userId);
        return Result.ok();
    }

    @DeleteMapping("/empty")
    @Operation(summary = "清空回收站")
    public Result<Void> emptyTrash(@RequestHeader("X-User-Id") String userId) {
        trashDomainService.emptyTrash(userId);
        return Result.ok();
    }
}
