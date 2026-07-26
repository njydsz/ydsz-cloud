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
 * 文件锁定 REST API（P1-6）
 * <p>
 * 支持 Check-out / Check-in 机制，防止多用户并发编辑冲突。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/files")
@RequiredArgsConstructor
@Tag(name = "文件锁定", description = "Check-out/Check-in 防并发编辑")
public class FileLockController {

    private final FileNodeRepository fileNodeRepository;

    @PostMapping("/{nodeId}/lock")
    @Operation(summary = "锁定文件（Check-out）")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_UPLOAD)
    public BaseResponse<Void> lock(
            @PathVariable String nodeId,
            @RequestHeader("X-User-Id") String userId) {

        FileNode node = fileNodeRepository.findById(nodeId);
        if (node == null || !node.isFile()) {
            throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId);
        }

        if (node.getShareStatus() != null && node.getShareStatus().equals("locked")
                && !userId.equals(node.getUpdatedBy())) {
            throw new BusinessException(NextwikiExceptionCode.FILE_LOCKED);
        }

        node.setShareStatus("locked");
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

        FileNode node = fileNodeRepository.findById(nodeId);
        if (node == null) {
            throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId);
        }

        node.setShareStatus("private");
        node.setUpdatedBy(userId);
        node.setUpdatedAt(LocalDateTime.now());
        fileNodeRepository.update(node);

        log.info("[FileLockController] 解锁文件: nodeId={}, userId={}", nodeId, userId);
        return BaseResponse.success();
    }
}
