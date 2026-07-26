package com.njydsz.nextwiki.web.controller;

import java.time.LocalDateTime;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件锁定 REST API（P1-6 + P0-R3 + P2-R2）
 * <p>
 * 支持 Check-out / Check-in 机制，防止多用户并发编辑冲突。
 * P0-R3 修复：不再滥用 shareStatus 字段，改用 status 字段记录锁定状态。
 * P2-R2 修复：增加权限检查。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/files")
@RequiredArgsConstructor
@Tag(name = "文件锁定", description = "Check-out/Check-in 防并发编辑")
public class FileLockController {

    private final FileNodeRepository fileNodeRepository;
    private final com.njydsz.nextwiki.domain.service.FilePermissionService permissionService;

    @PostMapping("/{nodeId}/lock")
    @Operation(summary = "锁定文件（Check-out）")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_UPLOAD)
    public BaseResponse<Void> lock(
            @PathVariable String nodeId,
            @RequestHeader("X-User-Id") String userId) {

        // P2-R2: 权限检查
        permissionService.checkWrite(nodeId, userId);

        FileNode node = fileNodeRepository.findById(nodeId);
        if (node == null || !node.isFile()) {
            throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId);
        }

        // P0-R3: 使用 status 字段记录锁定状态，不再覆盖 shareStatus
        if ("locked".equals(node.getStatus()) && !userId.equals(node.getUpdatedBy())) {
            throw new BusinessException(NextwikiExceptionCode.FILE_LOCKED);
        }

        node.setStatus("locked");
        node.setUpdatedBy(userId);
        node.setUpdatedAt(LocalDateTime.now());
        fileNodeRepository.update(node);

        log.info("[FileLockController] 锁定文件: nodeId={}, userId={}", nodeId, userId);
        return BaseResponse.success();
    }

    @PostMapping("/{nodeId}/unlock")
    @Operation(summary = "解锁文件（Check-in）")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_UPLOAD)
    public BaseResponse<Void> unlock(
            @PathVariable String nodeId,
            @RequestHeader("X-User-Id") String userId) {

        // P2-R2: 权限检查
        permissionService.checkWrite(nodeId, userId);

        FileNode node = fileNodeRepository.findById(nodeId);
        if (node == null) {
            throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId);
        }

        // P0-R3: 恢复 status 为 active，不影响 shareStatus
        node.setStatus("active");
        node.setUpdatedBy(userId);
        node.setUpdatedAt(LocalDateTime.now());
        fileNodeRepository.update(node);

        log.info("[FileLockController] 解锁文件: nodeId={}, userId={}", nodeId, userId);
        return BaseResponse.success();
    }
}
