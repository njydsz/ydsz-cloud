package com.njydsz.pmis.file.controller;

import com.njydsz.pmis.common.annotation.RateLimit;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.file.service.FileEnhanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 文件增强 Controller。
 *
 * <p>提供分片上传、病毒扫描、在线预览接口。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "文件增强")
@RestController
@RequestMapping("/api/v1/file/enhance")
@RequiredArgsConstructor
@Validated
public class FileEnhanceController {

    private final FileEnhanceService fileEnhanceService;

    /**
     * 病毒扫描。
     *
     * @param file 待扫描文件
     * @return 扫描结果，包含 safe 和 filename
     */
    @Operation(summary = "病毒扫描")
    @RateLimit(key = "file-upload", qps = 10, windowSeconds = 60,
            message = "{validation.file.msg_f4ed69d1}")
    @PostMapping("/scan")
    public Result<Map<String, Object>> scanVirus(
            @RequestParam("file") @NotNull(message = "{validation.file.msg_3f00c223}") MultipartFile file) {
        boolean safe = fileEnhanceService.scanVirus(file);
        Map<String, Object> result = new HashMap<>();
        result.put("safe", safe);
        result.put("filename", file.getOriginalFilename());
        return Result.ok(result);
    }

    /**
     * 初始化分片上传。
     *
     * @param filename    文件名
     * @param totalSize   文件总大小（字节）
     * @param totalChunks 分片总数
     * @return uploadId
     */
    @Operation(summary = "初始化分片上传")
    @RateLimit(key = "file-upload", qps = 10, windowSeconds = 60,
            message = "{validation.file.msg_f4ed69d1}")
    @PostMapping("/multipart/init")
    public Result<Map<String, Object>> initMultipartUpload(
            @RequestParam @NotBlank(message = "{validation.file.msg_f185973c}") String filename,
            @RequestParam @Min(value = 1, message = "{validation.file.msg_a32c726a}") long totalSize,
            @RequestParam @Min(value = 1, message = "{validation.file.msg_0dddf2c0}") int totalChunks) {
        String uploadId = fileEnhanceService.initMultipartUpload(filename, totalSize, totalChunks);
        Map<String, Object> result = new HashMap<>();
        result.put("uploadId", uploadId);
        return Result.ok(result);
    }

    /**
     * 上传分片。
     *
     * @param uploadId   分片上传 ID
     * @param chunkIndex 分片序号
     * @param chunk      分片数据
     * @return 上传结果
     * @throws Exception 读取分片数据时发生异常
     */
    @Operation(summary = "上传分片")
    @PostMapping("/multipart/chunk")
    public Result<Map<String, Object>> uploadChunk(
            @RequestParam @NotBlank(message = "{validation.file.msg_5866b696}") String uploadId,
            @RequestParam @Min(value = 0, message = "{validation.file.msg_4b78b69b}") int chunkIndex,
            @RequestParam("chunk") @NotNull(message = "{validation.file.msg_041e6b98}") MultipartFile chunk) throws Exception {
        boolean success = fileEnhanceService.uploadChunk(uploadId, chunkIndex, chunk.getBytes());
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("chunkIndex", chunkIndex);
        return Result.ok(result);
    }

    /**
     * 合并所有分片完成上传。
     *
     * @param uploadId 分片上传 ID
     * @return 合并结果，包含 fileKey 和 success
     */
    @Operation(summary = "完成分片上传")
    @PostMapping("/multipart/complete")
    public Result<Map<String, Object>> completeMultipartUpload(
            @RequestParam @NotBlank(message = "{validation.file.msg_5866b696}") String uploadId) {
        String fileKey = fileEnhanceService.completeMultipartUpload(uploadId);
        Map<String, Object> result = new HashMap<>();
        result.put("fileKey", fileKey != null ? fileKey : "");
        result.put("success", fileKey != null);
        return Result.ok(result);
    }

    /**
     * 取消分片上传。
     *
     * @param uploadId 分片上传 ID
     * @return 操作结果
     */
    @Operation(summary = "取消分片上传")
    @DeleteMapping("/multipart/abort")
    public Result<Map<String, Object>> abortMultipartUpload(
            @RequestParam @NotBlank(message = "{validation.file.msg_5866b696}") String uploadId) {
        fileEnhanceService.abortMultipartUpload(uploadId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return Result.ok(result);
    }

    /**
     * 生成在线预览 URL。
     *
     * @param fileKey 文件 key
     * @return 预览 URL
     */
    @Operation(summary = "生成预览URL")
    @GetMapping("/preview")
    public Result<Map<String, Object>> generatePreviewUrl(
            @RequestParam @NotBlank(message = "{validation.file.msg_db802ce3}") String fileKey) {
        String url = fileEnhanceService.generatePreviewUrl(fileKey);
        Map<String, Object> result = new HashMap<>();
        result.put("previewUrl", url);
        return Result.ok(result);
    }
}
