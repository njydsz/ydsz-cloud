package com.njydsz.pmis.system.web.controller.file;

import com.njydsz.pmis.common.lock.annotation.Idempotent;

import com.njydsz.pmis.common.annotation.RateLimit;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.system.server.service.file.FileEnhanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@Tag(name = "文件增强", description = "分片上传、病毒扫描、在线预览接口")
@RestController
@RequestMapping("/file/enhance")
@RequiredArgsConstructor
@Validated
public class FileEnhanceController {

    /** 文件增强服务（分片上传、病毒扫描、在线预览） */
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
    @Idempotent(key = "fileEnhance:scanVirus", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/scan")
    public BaseResponse<Map<String, Object>> scanVirus(
            @Parameter(description = "待扫描文件") @RequestParam("file") @NotNull(message = "{validation.file.msg_3f00c223}") MultipartFile file) {
        boolean safe = fileEnhanceService.scanVirus(file);
        Map<String, Object> result = new HashMap<>();
        BaseResponse.put("safe", safe);
        BaseResponse.put("filename", file.getOriginalFilename());
        return BaseResponse.ok(result);
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
    @Idempotent(key = "fileEnhance:initMultipartUpload", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/multipart/init")
    public BaseResponse<Map<String, Object>> initMultipartUpload(
            @Parameter(description = "文件名") @RequestParam @NotBlank(message = "{validation.file.msg_f185973c}") String filename,
            @Parameter(description = "文件总大小（字节）") @RequestParam @Min(value = 1, message = "{validation.file.msg_a32c726a}") long totalSize,
            @Parameter(description = "分片总数") @RequestParam @Min(value = 1, message = "{validation.file.msg_0dddf2c0}") int totalChunks) {
        String uploadId = fileEnhanceService.initMultipartUpload(filename, totalSize, totalChunks);
        Map<String, Object> result = new HashMap<>();
        BaseResponse.put("uploadId", uploadId);
        return BaseResponse.ok(result);
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
    @Idempotent(key = "fileEnhance:uploadChunk", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/multipart/chunk")
    public BaseResponse<Map<String, Object>> uploadChunk(
            @Parameter(description = "分片上传ID") @RequestParam @NotBlank(message = "{validation.file.msg_5866b696}") String uploadId,
            @Parameter(description = "分片序号") @RequestParam @Min(value = 0, message = "{validation.file.msg_4b78b69b}") int chunkIndex,
            @RequestParam("chunk") @NotNull(message = "{validation.file.msg_041e6b98}") MultipartFile chunk) throws Exception {
        boolean success = fileEnhanceService.uploadChunk(uploadId, chunkIndex, chunk.getBytes());
        Map<String, Object> result = new HashMap<>();
        BaseResponse.put("success", success);
        BaseResponse.put("chunkIndex", chunkIndex);
        return BaseResponse.ok(result);
    }

    /**
     * 合并所有分片完成上传。
     *
     * @param uploadId 分片上传 ID
     * @return 合并结果，包含 fileKey 和 success
     */
    @Operation(summary = "完成分片上传")
    @Idempotent(key = "fileEnhance:completeMultipartUpload", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/multipart/complete")
    public BaseResponse<Map<String, Object>> completeMultipartUpload(
            @Parameter(description = "分片上传ID") @RequestParam @NotBlank(message = "{validation.file.msg_5866b696}") String uploadId) {
        String fileKey = fileEnhanceService.completeMultipartUpload(uploadId);
        Map<String, Object> result = new HashMap<>();
        BaseResponse.put("fileKey", fileKey != null ? fileKey : "");
        BaseResponse.put("success", fileKey != null);
        return BaseResponse.ok(result);
    }

    /**
     * 取消分片上传。
     *
     * @param uploadId 分片上传 ID
     * @return 操作结果
     */
    @Operation(summary = "取消分片上传")
    @Idempotent(key = "fileEnhance:abortMultipartUpload", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/multipart/abort")
    public BaseResponse<Map<String, Object>> abortMultipartUpload(
            @Parameter(description = "分片上传ID") @RequestParam @NotBlank(message = "{validation.file.msg_5866b696}") String uploadId) {
        fileEnhanceService.abortMultipartUpload(uploadId);
        Map<String, Object> result = new HashMap<>();
        BaseResponse.put("success", true);
        return BaseResponse.ok(result);
    }

    /**
     * 生成在线预览 URL。
     *
     * @param fileKey 文件 key
     * @return 预览 URL
     */
    @Operation(summary = "生成预览URL")
    @GetMapping("/preview")
    public BaseResponse<Map<String, Object>> generatePreviewUrl(
            @Parameter(description = "文件key") @RequestParam @NotBlank(message = "{validation.file.msg_db802ce3}") String fileKey) {
        String url = fileEnhanceService.generatePreviewUrl(fileKey);
        Map<String, Object> result = new HashMap<>();
        BaseResponse.put("previewUrl", url);
        return BaseResponse.ok(result);
    }
}
