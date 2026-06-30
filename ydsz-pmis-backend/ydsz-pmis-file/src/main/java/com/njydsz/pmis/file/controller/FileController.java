package com.njydsz.pmis.file.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.file.dto.FileUploadDTO;
import com.njydsz.pmis.file.entity.FileDO;
import com.njydsz.pmis.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文件存储 Controller
 */
@Tag(name = "文件存储")
@RestController
@RequestMapping("/api/v1/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @Operation(summary = "上传文件")
    @PrePermission(PermissionCodes.FILE_STORAGE_UPLOAD)
    @OperationLog(module = "文件存储", action = "上传文件", bizType = "FILE")
    @PostMapping("/upload")
    public R<FileDO> upload(@RequestPart("file") MultipartFile file,
                            FileUploadDTO dto) throws Exception {
        if (dto == null) {
            dto = new FileUploadDTO();
        }
        if (dto.getUploaderId() == null) {
            dto.setUploaderId(SecurityContext.getUserId());
        }
        if (dto.getUploaderName() == null) {
            dto.setUploaderName(SecurityContext.getUsername());
        }
        return R.ok(fileService.upload(file, dto));
    }

    @Operation(summary = "删除文件")
    @PrePermission(PermissionCodes.FILE_STORAGE_DELETE)
    @OperationLog(module = "文件存储", action = "删除文件", bizType = "FILE")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) throws Exception {
        fileService.delete(id);
        return R.ok();
    }

    @Operation(summary = "批量删除")
    @PrePermission(PermissionCodes.FILE_STORAGE_DELETE)
    @DeleteMapping("/batch")
    public R<Void> deleteBatch(@RequestBody List<Long> ids) throws Exception {
        fileService.deleteBatch(ids);
        return R.ok();
    }

    @Operation(summary = "文件详情")
    @GetMapping("/{id}")
    public R<FileDO> getById(@PathVariable Long id) {
        return R.ok(fileService.getById(id));
    }

    @Operation(summary = "获取预签名下载 URL")
    @GetMapping("/{id}/presigned-url")
    public R<String> presignedUrl(@PathVariable Long id,
                                  @RequestParam(required = false) Integer expireSeconds) {
        return R.ok(fileService.getPresignedUrl(id, expireSeconds));
    }

    @Operation(summary = "下载文件")
    @GetMapping("/{id}/download")
    public void download(@PathVariable Long id, HttpServletResponse response) throws Exception {
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

    @Operation(summary = "按业务查询")
    @GetMapping("/by-biz")
    public R<List<FileDO>> listByBiz(@RequestParam String bizType,
                                     @RequestParam String bizId) {
        return R.ok(fileService.listByBiz(bizType, bizId));
    }

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<Page<FileDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String bizId,
            @RequestParam(required = false) String keyword) {
        return R.ok(fileService.page(page, size, bizType, bizId, keyword));
    }
}
