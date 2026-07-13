package com.njydsz.pmis.nextwiki.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.pmis.common.core.response.Result;
import com.njydsz.pmis.nextwiki.api.dto.NextwikiDTOs;
import com.njydsz.pmis.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.pmis.nextwiki.server.service.FileApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件管理 REST API
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@RestController
@RequestMapping("/nextwiki/files")
@RequiredArgsConstructor
@Tag(name = "网盘文件管理", description = "文件上传、下载、移动、重命名、删除等操作")
public class FileController {

    private final FileApplicationService fileApplicationService;

    @PostMapping("/upload")
    @Operation(summary = "上传文件", description = "支持单文件上传，自动创建版本记录")
    public Result<FileNodeVO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "parentId", required = false) String parentId,
            @RequestParam(value = "rename", required = false) String rename,
            @RequestParam(value = "versionRemark", required = false) String versionRemark,
            @RequestHeader("X-User-Id") String userId) {

        FileNodeVO result = fileApplicationService.upload(file, parentId, rename, versionRemark, userId);
        return Result.ok(result);
    }

    @PostMapping("/folders")
    @Operation(summary = "创建目录")
    public Result<FileNodeVO> createFolder(
            @RequestBody NextwikiDTOs.CreateFolderRequest request,
            @RequestHeader("X-User-Id") String userId) {

        FileNodeVO result = fileApplicationService.createFolder(request.getParentId(), request.getName(), userId);
        return Result.ok(result);
    }

    @GetMapping("/list")
    @Operation(summary = "列出目录内容")
    public Result<List<FileNodeVO>> listFiles(
            @RequestParam(value = "parentId", required = false) String parentId,
            @RequestHeader("X-User-Id") String userId) {

        List<FileNodeVO> result = fileApplicationService.listFiles(parentId, userId);
        return Result.ok(result);
    }

    @PutMapping("/{nodeId}/move")
    @Operation(summary = "移动文件/文件夹")
    public Result<FileNodeVO> move(
            @PathVariable String nodeId,
            @RequestBody NextwikiDTOs.MoveRequest request,
            @RequestHeader("X-User-Id") String userId) {

        FileNodeVO result = fileApplicationService.move(nodeId, request.getTargetParentId(), userId);
        return Result.ok(result);
    }

    @PutMapping("/{nodeId}/rename")
    @Operation(summary = "重命名文件/文件夹")
    public Result<FileNodeVO> rename(
            @PathVariable String nodeId,
            @RequestBody NextwikiDTOs.RenameRequest request,
            @RequestHeader("X-User-Id") String userId) {

        FileNodeVO result = fileApplicationService.rename(nodeId, request.getNewName(), userId);
        return Result.ok(result);
    }

    @DeleteMapping("/{nodeId}")
    @Operation(summary = "删除文件/文件夹（移入回收站）")
    public Result<Void> delete(
            @PathVariable String nodeId,
            @RequestHeader("X-User-Id") String userId) {

        fileApplicationService.delete(nodeId, userId);
        return Result.ok();
    }

    @GetMapping("/{nodeId}/versions")
    @Operation(summary = "获取版本历史")
    public Result<List<Object>> getVersionHistory(@PathVariable String nodeId) {
        return Result.ok(fileApplicationService.getVersionHistory(nodeId).stream()
                .map(v -> (Object) v)
                .toList());
    }

    @PostMapping("/{nodeId}/versions/{version}/rollback")
    @Operation(summary = "回滚到指定版本")
    public Result<FileNodeVO> rollbackVersion(
            @PathVariable String nodeId,
            @PathVariable Integer version,
            @RequestHeader("X-User-Id") String userId) {

        FileNodeVO result = fileApplicationService.rollbackVersion(nodeId, version, userId);
        return Result.ok(result);
    }

    @PutMapping("/{nodeId}/star")
    @Operation(summary = "切换星标状态")
    public Result<Void> toggleStar(
            @PathVariable String nodeId,
            @RequestHeader("X-User-Id") String userId) {

        fileApplicationService.toggleStar(nodeId, userId);
        return Result.ok();
    }
}
