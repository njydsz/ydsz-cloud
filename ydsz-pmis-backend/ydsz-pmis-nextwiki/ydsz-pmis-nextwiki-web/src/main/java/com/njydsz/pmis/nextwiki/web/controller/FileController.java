package com.njydsz.pmis.nextwiki.web.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.nextwiki.api.dto.NextwikiDTOs;
import com.njydsz.pmis.nextwiki.domain.entity.FileVersion;
import com.njydsz.pmis.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.pmis.nextwiki.server.service.FileApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
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
@RequestMapping("/api/v1/nextwiki/files")
@RequiredArgsConstructor
@Validated
@Tag(name = "网盘文件管理", description = "文件上传、下载、移动、重命名、删除等操作")
public class FileController {

    private final FileApplicationService fileApplicationService;

    @PostMapping("/upload")
    @Operation(summary = "上传文件", description = "支持单文件上传，自动创建版本记录")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_UPLOAD)
    public BaseResponse<FileNodeVO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "parentId", required = false) String parentId,
            @RequestParam(value = "rename", required = false) String rename,
            @RequestParam(value = "versionRemark", required = false) String versionRemark,
            @RequestHeader("X-User-Id") String userId) {

        FileNodeVO result = fileApplicationService.upload(file, parentId, rename, versionRemark, userId);
        return BaseResponse.ok(result);
    }

    @PostMapping("/folders")
    @Operation(summary = "创建目录")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FOLDER_CREATE)
    public BaseResponse<FileNodeVO> createFolder(
            @Valid @RequestBody NextwikiDTOs.CreateFolderRequest request,
            @RequestHeader("X-User-Id") String userId) {

        FileNodeVO result = fileApplicationService.createFolder(
                request.getParentId(), request.getName(), userId);
        return BaseResponse.ok(result);
    }

    @GetMapping("/list")
    @Operation(summary = "列出目录内容", description = "支持排序、过滤、分页")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_LIST)
    public BaseResponse<List<FileNodeVO>> listFiles(
            @RequestParam(value = "parentId", required = false) String parentId,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "sortDir", required = false) String sortDir,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "50") int pageSize,
            @RequestHeader("X-User-Id") String userId) {

        List<FileNodeVO> result = fileApplicationService.listFiles(
                parentId, userId, sortBy, sortDir, type, page, pageSize);
        return BaseResponse.ok(result);
    }

    @PutMapping("/{nodeId}/move")
    @Operation(summary = "移动文件/文件夹")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_MOVE)
    public BaseResponse<FileNodeVO> move(
            @PathVariable String nodeId,
            @Valid @RequestBody NextwikiDTOs.MoveRequest request,
            @RequestHeader("X-User-Id") String userId) {

        FileNodeVO result = fileApplicationService.move(nodeId, request.getTargetParentId(), userId);
        return BaseResponse.ok(result);
    }

    @PutMapping("/{nodeId}/rename")
    @Operation(summary = "重命名文件/文件夹")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_RENAME)
    public BaseResponse<FileNodeVO> rename(
            @PathVariable String nodeId,
            @Valid @RequestBody NextwikiDTOs.RenameRequest request,
            @RequestHeader("X-User-Id") String userId) {

        FileNodeVO result = fileApplicationService.rename(nodeId, request.getNewName(), userId);
        return BaseResponse.ok(result);
    }

    @DeleteMapping("/{nodeId}")
    @Operation(summary = "删除文件/文件夹（移入回收站）")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_DELETE)
    public BaseResponse<Void> delete(
            @PathVariable String nodeId,
            @RequestHeader("X-User-Id") String userId) {

        fileApplicationService.delete(nodeId, userId);
        return BaseResponse.ok();
    }

    @PostMapping("/batch/delete")
    @Operation(summary = "批量删除")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_DELETE)
    public BaseResponse<FileApplicationService.BatchResult> batchDelete(
            @RequestBody List<String> nodeIds,
            @RequestHeader("X-User-Id") String userId) {

        FileApplicationService.BatchResult result = fileApplicationService.batchDelete(nodeIds, userId);
        return BaseResponse.ok(result);
    }

    @PostMapping("/batch/move")
    @Operation(summary = "批量移动")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_MOVE)
    public BaseResponse<FileApplicationService.BatchResult> batchMove(
            @RequestBody BatchMoveRequest request,
            @RequestHeader("X-User-Id") String userId) {

        FileApplicationService.BatchResult result = fileApplicationService.batchMove(
                request.getNodeIds(), request.getTargetParentId(), userId);
        return BaseResponse.ok(result);
    }

    @PostMapping("/{nodeId}/copy")
    @Operation(summary = "复制文件")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_COPY)
    public BaseResponse<FileNodeVO> copy(
            @PathVariable String nodeId,
            @RequestParam("targetParentId") String targetParentId,
            @RequestHeader("X-User-Id") String userId) {

        FileNodeVO result = fileApplicationService.copy(nodeId, targetParentId, userId);
        return BaseResponse.ok(result);
    }

    @GetMapping("/{nodeId}/versions")
    @Operation(summary = "获取版本历史")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_VERSION_VIEW)
    public BaseResponse<List<FileVersion>> getVersionHistory(@PathVariable String nodeId) {
        return BaseResponse.ok(fileApplicationService.getVersionHistory(nodeId));
    }

    @PostMapping("/{nodeId}/versions/{version}/rollback")
    @Operation(summary = "回滚到指定版本")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_VERSION_ROLLBACK)
    public BaseResponse<FileNodeVO> rollbackVersion(
            @PathVariable String nodeId,
            @PathVariable Integer version,
            @RequestHeader("X-User-Id") String userId) {

        FileNodeVO result = fileApplicationService.rollbackVersion(nodeId, version, userId);
        return BaseResponse.ok(result);
    }

    @PutMapping("/{nodeId}/star")
    @Operation(summary = "切换星标状态")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_STAR)
    public BaseResponse<Void> toggleStar(
            @PathVariable String nodeId,
            @RequestHeader("X-User-Id") String userId) {

        fileApplicationService.toggleStar(nodeId, userId);
        return BaseResponse.ok();
    }

    /**
     * 批量移动请求
     */
    @Data
    @Schema(description = "批量移动请求")
    public static class BatchMoveRequest {
        @Schema(description = "待移动节点ID列表")
        private List<String> nodeIds;
        @Schema(description = "目标父目录ID")
        private String targetParentId;

        public List<String> getNodeIds() {
            return nodeIds;
        }

        public void setNodeIds(List<String> nodeIds) {
            this.nodeIds = nodeIds;
        }

        public String getTargetParentId() {
            return targetParentId;
        }

        public void setTargetParentId(String targetParentId) {
            this.targetParentId = targetParentId;
        }
    }
}
