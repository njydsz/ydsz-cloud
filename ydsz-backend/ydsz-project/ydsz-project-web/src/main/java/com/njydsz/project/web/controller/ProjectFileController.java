package com.njydsz.project.web.controller;

import java.util.UUID;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.file.domain.FileStorage;
import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.file.storage.IFileStorageProvider;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.FileStorageVO;

/**
 * 项目附件文件管理 Controller。
 *
 * <p>使用 common-file 的 IFileStorageProvider 实现文件上传、下载、删除，
 * 支持项目合同、立项报告、验收文档等附件的统一存储管理。
 *
 * <p>存储路径规范：{@code project/{projectId}/{category}/{uuid}.{suffix}}
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/project/files")
@Tag(name = "项目附件", description = "项目文件上传下载管理")
public class ProjectFileController {

    private static final String DEFAULT_BUCKET = "ydsz-project";
    private static final String PATH_PREFIX = "project/";

    @Autowired(required = false)
    private IFileStorageProvider fileStorageProvider;

    /**
     * 上传项目附件。
     *
     * @param projectId 项目ID
     * @param category  文件分类（contract/report/acceptance/other）
     * @param file      文件
     * @return 文件存储信息
     */
    @Operation(summary = "上传项目附件")
    @PostMapping("/upload")
    public BaseResponse<FileStorageVO> upload(
            @RequestParam String projectId,
            @RequestParam(defaultValue = "other") String category,
            @RequestParam("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return BaseResponse.error("文件不能为空");
        }

        IFileStorage storage = getStorage();
        if (storage == null) {
            return BaseResponse.error("文件存储服务未配置");
        }

        String originalFilename = file.getOriginalFilename();
        String suffix = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String objectName = PATH_PREFIX + projectId + "/" + category + "/"
                + UUID.randomUUID().toString().replace("-", "") + suffix;

        FileStorage result = storage.upload(DEFAULT_BUCKET, objectName, file);
        log.info("[ProjectFileController] 文件上传成功: projectId={}, category={}, objectName={}, size={}",
                projectId, category, objectName, file.getSize());

        return BaseResponse.success(result);
    }

    /**
     * 下载项目附件。
     *
     * @param objectName 对象存储路径
     * @param response   HTTP 响应
     */
    @Operation(summary = "下载项目附件")
    @GetMapping("/download")
    public void download(@RequestParam String objectName, HttpServletResponse response) {
        IFileStorage storage = getStorage();
        if (storage == null) {
            throw new IllegalStateException("文件存储服务未配置");
        }
        storage.download(DEFAULT_BUCKET, objectName, response);
    }

    /**
     * 删除项目附件。
     *
     * @param objectName 对象存储路径
     * @return 操作结果
     */
    @Operation(summary = "删除项目附件")
    @DeleteMapping("/{objectName}")
    public BaseResponse<String> delete(@PathVariable String objectName) {
        IFileStorage storage = getStorage();
        if (storage == null) {
            return BaseResponse.error("文件存储服务未配置");
        }
        storage.delete(DEFAULT_BUCKET, objectName);
        log.info("[ProjectFileController] 文件删除成功: objectName={}", objectName);
        return BaseResponse.success("删除成功");
    }

    /**
     * 获取文件存储实例。
     *
     * @return 文件存储实例，未配置时返回 null
     */
    private IFileStorage getStorage() {
        if (fileStorageProvider == null) {
            return null;
        }
        return fileStorageProvider.getStorage();
    }
}
