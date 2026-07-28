package com.njydsz.project.web.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.excel.core.ExcelFacade;
import com.njydsz.common.excel.core.listener.ReadListener;
import com.njydsz.common.excel.core.metadata.AnalysisContext;
import com.njydsz.common.excel.spring.web.ExcelWebSupport;
import com.njydsz.project.domain.dto.ProjectInitiationExcelDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 项目立项 Excel 导入导出 Controller。
 *
 * <p>使用 common-excel 的 ExcelFacade 实现高性能 Excel 读写，
 * 支持 .xlsx 格式的批量导入和导出。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/project/excel")
@RequiredArgsConstructor
@Tag(name = "项目Excel", description = "项目立项 Excel 导入导出")
public class ProjectExcelController {

    /**
     * 导出项目立项 Excel 模板。
     *
     * <p>下载空白模板，用户填写后通过 /import 端点导入。
     *
     * @param response HTTP 响应
     */
    @Operation(summary = "下载导入模板")
    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) {
        ExcelWebSupport.download(response, "项目立项导入模板",
                ProjectInitiationExcelDTO.class, new ArrayList<>(), "项目立项");
    }

    /**
     * 导出项目立项数据为 Excel。
     *
     * <p>如果指定了 projectIds，则导出对应项目；否则导出全部（受分页限制）。
     *
     * @param response  HTTP 响应
     * @param projectIds 项目ID列表（可选，逗号分隔）
     */
    @Operation(summary = "导出项目立项数据")
    @GetMapping("/export")
    public void export(HttpServletResponse response,
                       @RequestParam(required = false) String projectIds) {
        // TODO: 从 ProjectInitiationService 查询数据并转换为 ExcelDTO
        // 当前返回空列表作为骨架，后续接入 Service 查询
        List<ProjectInitiationExcelDTO> data = new ArrayList<>();
        ExcelWebSupport.download(response, "项目立项数据",
                ProjectInitiationExcelDTO.class, data, "项目立项");
    }

    /**
     * 导入项目立项 Excel 数据。
     *
     * <p>解析上传的 Excel 文件，逐行读取并校验数据。
     *
     * @param file Excel 文件（.xlsx）
     * @return 导入结果
     */
    @Operation(summary = "导入项目立项数据")
    @Idempotent(key = "ydsz:project:ProjectExcelController:onData:lock", ttlSeconds = 5)
    @PostMapping("/import")
    public BaseResponse<String> importExcel(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return BaseResponse.error("文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.endsWith(".xlsx")) {
            return BaseResponse.error("仅支持 .xlsx 格式文件");
        }

        List<ProjectInitiationExcelDTO> successList = new ArrayList<>();
        List<String> errorList = new ArrayList<>();

        try (InputStream is = file.getInputStream()) {
            ExcelFacade.read(is, ProjectInitiationExcelDTO.class)
                    .sheet("项目立项")
                    .doRead(new ReadListener<ProjectInitiationExcelDTO>() {
                        @Override
                        public void onData(AnalysisContext context, ProjectInitiationExcelDTO data) {
                            if (data.getProjectCode() == null || data.getProjectCode().isBlank()) {
                                errorList.add("第" + (context.getCurrentRowIndex() + 1) + "行：项目编号不能为空");
                                return;
                            }
                            successList.add(data);
                        }

                        @Override
                        public void onException(AnalysisContext context, Exception exception) {
                            log.warn("Excel 导入解析异常: row={}", context.getCurrentRowIndex(), exception);
                            errorList.add("第" + (context.getCurrentRowIndex() + 1) + "行：" + exception.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.error("Excel 导入文件读取失败", e);
            return BaseResponse.error("文件读取失败: " + e.getMessage());
        }

        // TODO: 批量保存到数据库（通过 ProjectInitiationService.batchSave）
        log.info("Excel 导入完成: 成功{}条, 失败{}条", successList.size(), errorList.size());

        StringBuilder result = new StringBuilder();
        result.append("导入完成：成功").append(successList.size()).append("条");
        if (!errorList.isEmpty()) {
            result.append("，失败").append(errorList.size()).append("条");
            result.append("\n失败详情：");
            for (String error : errorList) {
                result.append("\n").append(error);
            }
        }

        return BaseResponse.success(result.toString());
    }
}
