package com.njydsz.pmis.project.web.controller.common;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.server.service.ImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 批量导入 Controller
 *
 * <p>职责：模板下载（GET /template/{bizType}）+ 批量导入（POST /import/{bizType}）。
 * <p>支持业务类型：rate-card（职级费率）、rate-internal（内部费率）、time-entry（工时），
 * 新增业务类型只需扩展 {@link ImportService} 的 dispatch 路由表。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "批量导入")
@RestController
@RequestMapping("/importExport")
@RequiredArgsConstructor
@Validated
public class ImportExportController {

    /** bizType 白名单（防御性编程：阻止路径穿越与非法业务类型） */
    private static final Set<String> ALLOWED_BIZ_TYPES = Set.of("rate-card", "rate-internal", "time-entry");

    /** 数据导入服务 */
    private final ImportService importService;

    /**
     * 下载空白模板（带样例数据）
     *
     * @param bizType  业务类型
     * @param response HTTP 响应对象
     * @throws IOException 写入响应流时发生 I/O 异常
     */
    @Operation(summary = "下载空白模板（带样例数据）")
    @GetMapping("/template/{bizType}")
    public void downloadTemplate(@PathVariable String bizType, HttpServletResponse response) throws IOException {
        // 白名单校验：防止非法 bizType 导致路径穿越或未预期的分派
        if (!ALLOWED_BIZ_TYPES.contains(bizType)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_f7d2a1b3", bizType);
        }
        TemplateBundle bundle = importService.buildTemplate(bizType);

        String filename = URLEncoder.encode(bundle.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
        response.setContentLength(bundle.bytes().length);

        try (OutputStream out = response.getOutputStream()) {
            out.write(bundle.bytes());
            out.flush();
        }
        log.info("[ImportTemplate] bizType={} size={} bytes", bizType, bundle.bytes().length);
    }

    /**
     * 批量导入（限频 1 次/秒）
     *
     * @param bizType 业务类型
     * @param file    上传的文件
     * @return 导入结果
     * @throws IOException 读取文件时发生 I/O 异常
     */
    @Operation(summary = "批量导入（限频 1 次/秒）")
    @PostMapping("/{bizType}")
    public Result<ImportService.ImportResult> importFile(
            @PathVariable String bizType,
            @RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return Result.failed(400, "上传文件为空");
        }
        ImportService.ImportResult R = importService.importFile(bizType, file);
        log.info("[ImportFile] bizType={} fileSize={} success={} failed={}",
                bizType, file.getSize(), R.successCount(), R.failedCount());
        return Result.ok(R);
    }

    /**
     * 模板包（DTO 类 + 字节流 + 文件名）
     */
    public record TemplateBundle(Class<?> headClass, byte[] bytes, String filename) {
    }
}
