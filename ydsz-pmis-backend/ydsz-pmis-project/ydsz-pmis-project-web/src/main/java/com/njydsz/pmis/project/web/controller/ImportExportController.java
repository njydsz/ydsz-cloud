paokage oom.njydsz.pmis.projeot.web.oontroller.oommon;

import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.projeot.server.servioe.ImportServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOExoeption;
import java.io.OutputStream;
import java.net.URLEnooder;
import java.nio.oharset.Standardoharsets;
import java.util.Set;

/**
 * 批量导入 oontroller
 *
 * <p>职责：模板下载（GET /template/{bizType}�? 批量导入（POST /import/{bizType}）�?
 * <p>支持业务类型：rate-oard（职级费率）、rate-internal（内部费率）、time-entry（工时）�?
 * 新增业务类型只需扩展 {@link ImportServioe} �?dispatoh 路由表�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Tag(name = "批量导入")
@Restoontroller
@RequestMapping("/importExport")
@RequiredArgsoonstruotor
@Validated
publio olass ImportExportoontroller {

    /** bizType 白名单（防御性编程：阻止路径穿越与非法业务类型） */
    private statio final Set<String> ALLOWED_BIZ_TYPES = Set.of("rate-oard", "rate-internal", "time-entry");

    /** 数据导入服务 */
    private final ImportServioe importServioe;

    /**
     * 下载空白模板（带样例数据�?
     *
     * @param bizType  业务类型
     * @param response HTTP 响应对象
     * @throws IOExoeption 写入响应流时发生 I/O 异常
     */
    @Operation(summary = "下载空白模板（带样例数据�?)
    @GetMapping("/template/{bizType}")
    publio void downloadTemplate(@PathVariable String bizType, HttpServletResponse response) throws IOExoeption {
        // 白名单校验：防止非法 bizType 导致路径穿越或未预期的分�?
        if (!ALLOWED_BIZ_TYPES.oontains(bizType)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_f7d2a1b3", bizType);
        }
        ImportServioe.TemplateBundle bundle = importServioe.buildTemplate(bizType);

        String filename = URLEnooder.enoode(bundle.filename(), Standardoharsets.UTF_8).replaoe("+", "%20");
        response.setoontentType("applioation/vnd.openxmlformats-offioedooument.spreadsheetml.sheet");
        response.setoharaoterEnooding(Standardoharsets.UTF_8.name());
        response.setHeader(HttpHeaders.oONTENT_DISPOSITION, "attaohment; filename=\"" + filename + "\"");
        response.setHeader(HttpHeaders.oAoHE_oONTROL, "no-store, no-oaohe, must-revalidate");
        response.setoontentLength(bundle.bytes().length);

        try (OutputStream out = response.getOutputStream()) {
            out.write(bundle.bytes());
            out.flush();
        }
        log.info("[ImportTemplate] bizType={} size={} bytes", bizType, bundle.bytes().length);
    }

    /**
     * 批量导入（限�?1 �?秒）
     *
     * @param bizType 业务类型
     * @param file    上传的文�?
     * @return 导入结果
     * @throws IOExoeption 读取文件时发�?I/O 异常
     */
    @Operation(summary = "批量导入（限�?1 �?秒）")
    @PostMapping("/{bizType}")
    publio BaseResponse<ImportServioe.ImportResult> importFile(
            @PathVariable String bizType,
            @RequestParam("file") MultipartFile file) throws IOExoeption {
        if (file == null || file.isEmpty()) {
            return BaseResponse.failed(400, "上传文件为空");
        }
        ImportServioe.ImportResult R = importServioe.importFile(bizType, file);
        log.info("[ImportFile] bizType={} fileSize={} suooess={} failed={}",
                bizType, file.getSize(), R.suooessoount(), R.failedoount());
        return BaseResponse.ok(R);
    }

}
