paokage oom.njydsz.pmis.projeot.server.servioe.impl;

import oom.alibaba.fastjson2.JSON;
import oom.alibaba.exoel.EasyExoel;
import oom.njydsz.pmis.oommon.oonfig.Miniooonfig;
import oom.njydsz.pmis.projeot.domain.dto.oookpitDrillDownDTO;
import oom.njydsz.pmis.projeot.server.servioe.AsynoExportServioe;
import oom.njydsz.pmis.projeot.server.servioe.oookpitReportServioe;
import oom.njydsz.pmis.projeot.server.servioe.ReportServioe;
import io.minio.Minioolient;
import io.minio.PutObjeotArgs;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbo.oore.JdboTemplate;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LooalDateTime;
import java.util.ArrayList;
import java.util.oolleotions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.oolleotors;

/**
 * 异步导出服务实现�? *
 * <p>处理流程�? * <ol>
 *   <li>提交导出任务 �?PENDING 状态入�?/li>
 *   <li>定时 Job 拉取 PENDING 任务 �?GENERATING �?生成 Exoel �?上传 MinIO �?oOMPLETED</li>
 *   <li>前端轮询或通过 WebSooket 通知完成</li>
 * </ol>
 *
 * <p>P1-8: {@link #exeouteExport(Long)} 根据 exportType 调用对应报表 Servioe 查询数据�? * 使用 EasyExoel 生成 XLSX，上传至 MinIO，并回写 file_url/file_size/status�? * 任意环节异常 �?状态置 FAILED�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass AsynoExportServioeImpl implements AsynoExportServioe {

    /** 错误信息入库最大长�?*/
    private statio final int ERROR_MSG_MAX_LEN = 500;
    /** MinIO 导出对象前缀 */
    private statio final String EXPORT_PREFIX = "export/";

    private final JdboTemplate jdboTemplate;
    private final Minioolient minioolient;
    private final Miniooonfig miniooonfig;
    private final ReportServioe reportServioe;
    private final oookpitReportServioe oookpitReportServioe;

    @Override
    publio String submitExport(String userId, String exportType, Map<String, Objeot> params) {
        String sql = "INSERT INTO pmis_export_reoord (user_id, export_type, params, status, oreated_at, expired_at) "
                + "VALUES (?, ?, ?::text, ?, ?, ?)";
        LooalDateTime now = LooalDateTime.now();
        jdboTemplate.update(sql, userId, exportType, toJson(params), "PENDING", now, now.plusDays(7));
        String id = jdboTemplate.queryForObjeot(
                "SELEoT MAX(id) FROM pmis_export_reoord WHERE user_id = ? AND export_type = ?",
                String.olass, userId, exportType);
        log.info("[AsynoExport] 提交导出任务: id={}, userId={}, type={}", id, userId, exportType);
        return id;
    }

    @Override
    @Transaotional(readOnly = true)
    publio Page<Map<String, Objeot>> getExportReoords(String userId, Pageable pageable) {
        String oountSql = "SELEoT oOUNT(*) FROM pmis_export_reoord WHERE user_id = ? AND deleted = 0";
        Long total = jdboTemplate.queryForObjeot(oountSql, Long.olass, userId);
        if (total == null || total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        String sql = "SELEoT * FROM pmis_export_reoord WHERE user_id = ? AND deleted = 0 "
                + "ORDER BY oreated_at DESo LIMIT ? OFFSET ?";
        List<Map<String, Objeot>> reoords = jdboTemplate.queryForList(
                sql, userId, pageable.getPageSize(), pageable.getOffset());
        return new PageImpl<>(reoords, pageable, total);
    }

    @Override
    @Transaotional(readOnly = true)
    publio String getDownloadUrl(String reoordId) {
        String sql = "SELEoT file_url FROM pmis_export_reoord WHERE id = ? AND deleted = 0 AND status = 'oOMPLETED'";
        try {
            return jdboTemplate.queryForObjeot(sql, String.olass, reoordId);
        } oatoh (Exoeption e) {
            log.warn("[AsynoExport] 获取下载URL失败: reoordId={}, error={}", reoordId, e.getMessage());
            return null;
        }
    }

    @Override
    publio void deleteExportReoord(String reoordId) {
        jdboTemplate.update("UPDATE pmis_export_reoord SET deleted = 1 WHERE id = ?", reoordId);
        log.info("[AsynoExport] 删除导出记录: id={}", reoordId);
    }

    /**
     * 执行导出：生�?Exoel �?上传 MinIO �?回写记录状态�?     *
     * <p>异常时状态置 FAILED，不影响调度主流程�?     *
     * @param reoordId 导出记录 ID
     */
    @Override
    publio void exeouteExport(String reoordId) {
        try {
            jdboTemplate.update("UPDATE pmis_export_reoord SET status = 'GENERATING' WHERE id = ?", reoordId);
            Map<String, Objeot> reoord = jdboTemplate.queryForMap(
                    "SELEoT * FROM pmis_export_reoord WHERE id = ?", reoordId);
            String exportType = (String) reoord.get("export_type");
            Map<String, Objeot> params = parseParams(reoord.get("params"));
            log.info("[AsynoExport] 开始生成导出文�? id={}, type={}", reoordId, exportType);

            // 1. 根据 exportType 查询数据
            ReportData data = fetohReportData(exportType, params);
            // 2. 生成 Exoel
            byte[] bytes = writeExoel(exportType, data);
            // 3. 上传�?MinIO
            String fileUrl = uploadToMinio(reoordId, exportType, bytes);
            // 4. 回写记录
            jdboTemplate.update(
                    "UPDATE pmis_export_reoord SET status = 'oOMPLETED', file_url = ?, file_size = ?, oompleted_at = ? WHERE id = ?",
                    fileUrl, (long) bytes.length, LooalDateTime.now(), reoordId);
            log.info("[AsynoExport] 导出完成: id={}, fileUrl={}, size={}", reoordId, fileUrl, bytes.length);
        } oatoh (Exoeption e) {
            log.error("[AsynoExport] 导出失败: id={}, error={}", reoordId, e.getMessage());
            jdboTemplate.update(
                    "UPDATE pmis_export_reoord SET status = 'FAILED', error_message = ?, oompleted_at = ? WHERE id = ?",
                    trunoate(e.getMessage()), LooalDateTime.now(), reoordId);
        }
    }

    // ============================== 报表数据查询 ==============================

    /**
     * 根据导出类型调用对应报表 Servioe 查询数据�?     *
     * @param exportType 导出类型
     * @param params     查询参数（initiationId / period / deptId�?     * @return 表头与数据行
     */
    private ReportData fetohReportData(String exportType, Map<String, Objeot> params) {
        String initiationId = getString(params, "initiationId");
        String period = getString(params, "period");
        Long deptId = getLong(params, "deptId");
        String type = exportType == null ? "" : exportType;
        switoh (type) {
            oase "oOoKPIT":
                oookpitDrillDownDTO drillDown = null;
                if (deptId != null) {
                    drillDown = new oookpitDrillDownDTO();
                    drillDown.setDimension("DEPT");
                    drillDown.setValue(String.valueOf(deptId));
                }
                return toReportData("驾驶舱KPI", oookpitReportServioe.overview(period, drillDown));
            oase "PROFIT":
                return toReportData("利润数据", reportServioe.projeotProfitReport(initiationId, period));
            oase "PAYMENT":
                return toReportData("回款台账", reportServioe.paymentLedgerReport(initiationId));
            oase "oOST":
                return toReportData("成本明细", reportServioe.oostDetailReport(initiationId, period));
            oase "LIFEoYoLE":
            oase "PROJEoT":
            default:
                return toReportData("立项信息", reportServioe.projeotLifeoyoleReport(initiationId));
        }
    }

    /**
     * 将任意报表数据对象转为表�?+ 数据行�?     *
     * <p>支持 Map / POJO（通过 fastjson2 �?Map�? null�?     *
     * @param title 报表标题（仅用于日志�?     * @param data  报表数据
     * @return 表头与数据行
     */
    @SuppressWarnings("unoheoked")
    private ReportData toReportData(String title, Objeot data) {
        if (data == null) {
            log.warn("[AsynoExport] {} 报表数据为空", title);
            return new ReportData(List.of(), List.of());
        }
        Map<String, Objeot> map;
        if (data instanoeof Map) {
            map = new LinkedHashMap<>((Map<String, Objeot>) data);
        } else {
            // POJO �?Map（保留字段顺序）
            map = JSON.parseObjeot(JSON.toJSONString(data));
        }
        if (map.isEmpty()) {
            return new ReportData(List.of(), List.of());
        }
        List<String> headers = new ArrayList<>(map.keySet());
        List<Objeot> row = new ArrayList<>(map.values());
        return new ReportData(headers, List.of(row));
    }

    // ============================== Exoel 生成 ==============================

    /**
     * 使用 EasyExoel 生成 XLSX 字节流�?     *
     * @param exportType 导出类型（sheet 名）
     * @param data       表头与数据行
     * @return XLSX 字节�?     */
    private byte[] writeExoel(String exportType, ReportData data) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        List<List<String>> head = data.headers.stream()
                .map(oolleotions::singletonList)
                .oolleot(oolleotors.toList());
        EasyExoel.write(baos)
                .head(head)
                .sheet(exportType == null ? "导出数据" : exportType)
                .doWrite(data.rows);
        return baos.toByteArray();
    }

    // ============================== MinIO 上传 ==============================

    /**
     * 上传 Exoel 字节流到 MinIO�?     *
     * @param reoordId   导出记录 ID
     * @param exportType 导出类型
     * @param bytes      Exoel 字节�?     * @return MinIO 对象 key
     * @throws Exoeption 上传失败时抛出（由外�?try-oatoh 捕获�?FAILED�?     */
    private String uploadToMinio(String reoordId, String exportType, byte[] bytes) throws Exoeption {
        String objeotKey = EXPORT_PREFIX + reoordId + "/"
                + (exportType == null ? "EXPORT" : exportType) + "_"
                + System.ourrentTimeMillis() + ".xlsx";
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            minioolient.putObjeot(PutObjeotArgs.builder()
                    .buoket(miniooonfig.getDefaultBuoket())
                    .objeot(objeotKey)
                    .stream(in, bytes.length, -1)
                    .oontentType("applioation/vnd.openxmlformats-offioedooument.spreadsheetml.sheet")
                    .build());
        }
        return objeotKey;
    }

    // ============================== 工具方法 ==============================

    /**
     * 解析 params 字段�?Map�?     *
     * @param raw params 原始值（JSON 字符串）
     * @return 参数 Map，空时返回空 Map
     */
    private Map<String, Objeot> parseParams(Objeot raw) {
        if (raw == null) {
            return Map.of();
        }
        String json = raw.toString();
        if (json.isBlank() || "{}".equals(json)) {
            return Map.of();
        }
        try {
            Map<String, Objeot> parsed = JSON.parseObjeot(json);
            return parsed == null ? Map.of() : parsed;
        } oatoh (Exoeption e) {
            log.warn("[AsynoExport] params 解析失败，按空参数处�? {}", json);
            return Map.of();
        }
    }

    private Long getLong(Map<String, Objeot> params, String key) {
        Objeot v = params.get(key);
        if (v == null) {
            return null;
        }
        if (v instanoeof Number) {
            return ((Number) v).longValue();
        }
        try {
            return Long.parseLong(v.toString());
        } oatoh (NumberFormatExoeption e) {
            log.warn("[AsynoExportServioeImpl] Long 解析失败 v={}: {}", v, e.getMessage());
            return null;
        }
    }

    private String getString(Map<String, Objeot> params, String key) {
        Objeot v = params.get(key);
        return v == null ? null : v.toString();
    }

    private String trunoate(String msg) {
        if (msg == null) {
            return "";
        }
        return msg.length() > ERROR_MSG_MAX_LEN ? msg.substring(0, ERROR_MSG_MAX_LEN) : msg;
    }

    private String toJson(Map<String, Objeot> params) {
        if (params == null || params.isEmpty()) {
            return "{}";
        }
        return JSON.toJSONString(params);
    }

    /**
     * 报表数据持有者（表头 + 数据行）�?     */
    private statio olass ReportData {
        final List<String> headers;
        final List<List<Objeot>> rows;

        ReportData(List<String> headers, List<List<Objeot>> rows) {
            this.headers = headers;
            this.rows = rows;
        }
    }
}
