package com.njydsz.pmis.system.web.controller.file;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.common.auth.context.AuthContext;
import com.njydsz.pmis.system.domain.dto.file.FileUploadDTO;
import com.njydsz.pmis.system.domain.entity.file.FileDO;
import com.njydsz.pmis.system.server.service.file.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文件存储 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "文件存储", description = "文件上传、下载、删除及查询相关接口")
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
@Validated
public class FileController {

    /** 文件服务 */
    private final FileService fileService;

    /**
     * 上传文件
     *
     * @param file multipart 文件
     * @param dto  上传附加参数（可选，自动填充当前登录人信息）
     * @return 统一响应结果，包含文件元信息
     * @throws Exception 上传过程中发生异常
     */
    @Operation(summary = "上传文件")
    @PrePermission(PermissionCodes.FILE_STORAGE_UPLOAD)
    @OperationLog(module = "文件存储", action = "上传文件", bizType = "FILE")
    @Idempotent(key = "file:upload", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/upload")
    public BaseResponse<FileDO> upload(
            @Parameter(description = "multipart 文件") @RequestPart("file") MultipartFile file,
            FileUploadDTO dto) throws Exception {
        if (dto == null) {
            dto = new FileUploadDTO();
        }
        if (dto.getUploaderId() == null) {
            dto.setUploaderId(AuthContext.getUserId());
        }
        if (dto.getUploaderName() == null) {
            dto.setUploaderName(AuthContext.getUsername());
        }
        return BaseResponse.ok(fileService.upload(file, dto));
    }

    /**
     * 删除文件
     *
     * @param id 文件 ID
     * @return 统一响应结果
     * @throws Exception 删除过程中发生异常
     */
    @Operation(summary = "删除文件")
    @PrePermission(PermissionCodes.FILE_STORAGE_DELETE)
    @OperationLog(module = "文件存储", action = "删除文件", bizType = "FILE")
    @Idempotent(key = "file:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(
            @Parameter(description = "文件ID") @PathVariable String id) throws Exception {
        fileService.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 批量删除文件
     *
     * @param ids 文件 ID 列表
     * @return 统一响应结果
     * @throws Exception 删除过程中发生异常
     */
    @Operation(summary = "批量删除")
    @PrePermission(PermissionCodes.FILE_STORAGE_DELETE)
    @OperationLog(module = "文件存储", action = "批量删除文件", bizType = "FILE")
    @Idempotent(key = "file:deleteBatch", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/batch")
    public BaseResponse<Void> deleteBatch(@Valid @RequestBody List<String> ids) throws Exception {
        fileService.deleteBatch(ids);
        return BaseResponse.ok();
    }

    /**
     * 查询文件详情
     *
     * @param id 文件 ID
     * @return 统一响应结果，包含文件元信息
     */
    @Operation(summary = "文件详情")
    @GetMapping("/{id}")
    public BaseResponse<FileDO> getById(
            @Parameter(description = "文件ID") @PathVariable String id) {
        return BaseResponse.ok(fileService.getById(id));
    }

    /**
     * 获取预签名下载 URL
     *
     * @param id            文件 ID
     * @param expireSeconds URL 有效期（秒），可选
     * @return 统一响应结果，包含预签名 URL
     */
    @Operation(summary = "获取预签名下载 URL")
    @GetMapping("/{id}/presignedUrl")
    public BaseResponse<String> presignedUrl(
            @Parameter(description = "文件ID") @PathVariable String id,
            @Parameter(description = "URL有效期（秒）") @RequestParam(required = false) @Min(1) Integer expireSeconds) {
        return BaseResponse.ok(fileService.getPresignedUrl(id, expireSeconds));
    }

    /**
     * 下载文件
     *
     * @param id       文件 ID
     * @param response HTTP 响应对象
     * @throws Exception 下载过程中发生异常
     */
    @Operation(summary = "下载文件")
    @GetMapping("/{id}/download")
    public void download(
            @Parameter(description = "文件ID") @PathVariable String id,
            HttpServletResponse response) throws Exception {
        FileDO f = fileService.getById(id);
        try (InputStream in = fileService.download(id);
             OutputStream out = response.getOutputStream()) {
            response.setContentType(f.getContentType() == null ? "application/octet-stream" : f.getContentType());
            String encoded = URLEncoder.encode(f.getOriginalName(), StandardCharsets.UTF_8)
                    .replace("+", "%20");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
            response.setHeader("Content-Length", String.valueOf(f.getFileSize()));
            in.transferTo(out);
            out.flush();
        }
    }

    /**
     * 按业务类型与业务 ID 查询文件列表
     *
     * @param bizType 业务类型
     * @param bizId   业务单据 ID
     * @return 统一响应结果，包含文件元信息列表
     */
    @Operation(summary = "按业务查询")
    @GetMapping("/byBiz")
    public BaseResponse<List<FileDO>> listByBiz(
            @Parameter(description = "业务类型") @RequestParam @NotBlank String bizType,
            @Parameter(description = "业务单据ID") @RequestParam @NotBlank String bizId) {
        return BaseResponse.ok(fileService.listByBiz(bizType, bizId));
    }

    /**
     * 分页查询文件
     *
     * @param page    页码
     * @param size    每页大小
     * @param bizType 业务类型（可选）
     * @param bizId   业务单据 ID（可选）
     * @param keyword 关键词（可选）
     * @return 统一响应结果，包含分页数据
     */
    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public BaseResponse<Page<FileDO>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "业务类型") @RequestParam(required = false) String bizType,
            @Parameter(description = "业务单据ID") @RequestParam(required = false) String bizId,
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword) {
        return BaseResponse.ok(fileService.page(page, size, bizType, bizId, keyword));
    }
}
