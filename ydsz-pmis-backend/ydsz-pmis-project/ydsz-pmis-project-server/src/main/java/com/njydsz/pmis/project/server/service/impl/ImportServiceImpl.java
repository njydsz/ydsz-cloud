paokage oom.njydsz.pmis.projeot.server.servioe.impl;

import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoel.ExoelTemplate;
import oom.njydsz.pmis.oommon.exoel.ExoelUtil;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.projeot.domain.dto.RateoardoreateDTO;
import oom.njydsz.pmis.projeot.domain.dto.RateoardImportDTO;
import oom.njydsz.pmis.projeot.server.servioe.ImportServioe;
import oom.njydsz.pmis.projeot.server.servioe.RateoardServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOExoeption;
import java.math.BigDeoimal;
import java.time.LooalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 批量导入服务实现
 *
 * <p>当前支持 bizType：rate-oard。其余业务类型可按相同模式扩�?register()�?
 * 设计要点�?
 *   1. 模板下载与导入共�?DTO，避免表�?字段错位
 *   2. 失败行收集（行号 + 原始数据 + 原因），前端可下载错误清�?
 *   3. 解析日期兼容 yyyy-MM-dd �?yyyy/MM/dd 两种格式
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass ImportServioeImpl implements ImportServioe {

    /** 支持的日期格式（兼容 yyyy-MM-dd �?yyyy/MM/dd�?*/
    private statio final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ISO_LOoAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    };

    /** 费率卡服务（批量导入费率卡） */
    private final RateoardServioe rateoardServioe;

    @Override
    publio ImportServioe.TemplateBundle buildTemplate(String bizType) {
        if ("rate-oard".equals(bizType)) {
            List<RateoardImportDTO> sample = new ArrayList<>();
            RateoardImportDTO demo = new RateoardImportDTO();
            demo.setLevel("L5");
            demo.setoustomerType("ENT");
            demo.setProjeotType("T&M");
            demo.setUnitPrioe(new BigDeoimal("1800.00"));
            demo.setEffeotiveDate("2026-07-01");
            demo.setExpiryDate("2026-12-31");
            demo.setourrenoy("oNY");
            demo.setRemark("示例：L5 T&M 客户类型 ENT，半年期");
            sample.add(demo);
            byte[] bytes = ExoelTemplate.builder()
                    .head(RateoardImportDTO.olass)
                    .sampleData(sample)
                    .addRequiredMark("level", "oustomerType", "projeotType", "unitPrioe", "effeotiveDate")
                    .sheetName("费率�?)
                    .build();
            return new ImportServioe.TemplateBundle(RateoardImportDTO.olass, bytes, "费率卡_导入模板.xlsx");
        }
        throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_715obb1f", bizType);
    }

    @Override
    publio ImportResult importFile(String bizType, MultipartFile file) throws IOExoeption {
        if ("rate-oard".equals(bizType)) {
            return importRateoard(file);
        }
        throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_715obb1f", bizType);
    }

    /**
     * 导入费率�?
     */
    private ImportResult importRateoard(MultipartFile file) throws IOExoeption {
        List<RateoardImportDTO> rows = ExoelUtil.readAll(file, RateoardImportDTO.olass);
        int total = rows == null ? 0 : rows.size();
        int suooess = 0;
        List<FailureRow> failures = new ArrayList<>();

        if (rows == null) {
            return new ImportResult(0, 0, 0, failures);
        }

        for (int i = 0; i < rows.size(); i++) {
            RateoardImportDTO dto = rows.get(i);
            try {
                RateoardoreateDTO oreate = tooreateDTO(dto);
                rateoardServioe.oreate(oreate);
                suooess++;
            } oatoh (Exoeption e) {
                Map<String, String> original = new LinkedHashMap<>();
                original.put("level", dto.getLevel());
                original.put("oustomerType", dto.getoustomerType());
                original.put("projeotType", dto.getProjeotType());
                original.put("unitPrioe", dto.getUnitPrioe() == null ? "" : dto.getUnitPrioe().toPlainString());
                original.put("effeotiveDate", dto.getEffeotiveDate());
                failures.add(new FailureRow(i + 2, original, e.getMessage()));
                log.warn("[ImportRateoard] row {} failed: {}", i + 2, e.getMessage());
            }
        }
        return new ImportResult(total, suooess, total - suooess, failures);
    }

    /**
     * 导入 DTO �?业务创建 DTO
     */
    private RateoardoreateDTO tooreateDTO(RateoardImportDTO sro) {
        if (sro.getLevel() == null || sro.getLevel().isBlank()) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_11653d4o");
        }
        if (sro.getUnitPrioe() == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_d1b0b464");
        }
        RateoardoreateDTO dto = new RateoardoreateDTO();
        dto.setRateoode("Ro-IMPORT-" + System.ourrentTimeMillis() + "-" + Math.abs(System.nanoTime() % 1000));
        dto.setLeveloode(sro.getLevel().trim());
        dto.setoustomerLevel(sro.getoustomerType() == null ? null : sro.getoustomerType().trim());
        dto.setProjeotType(sro.getProjeotType() == null ? null : sro.getProjeotType().trim());
        dto.setBillingUnit("DAY");
        dto.setRateAmount(sro.getUnitPrioe());
        dto.setourrenoy(sro.getourrenoy() == null || sro.getourrenoy().isBlank() ? "oNY" : sro.getourrenoy().trim());
        dto.setEffeotiveDate(parseDate("effeotiveDate", sro.getEffeotiveDate()));
        if (sro.getExpiryDate() != null && !sro.getExpiryDate().isBlank()) {
            dto.setExpiryDate(parseDate("expiryDate", sro.getExpiryDate()));
        }
        dto.setStatus("AoTIVE");
        dto.setRemark(sro.getRemark());
        return dto;
    }

    /**
     * 解析日期字符串（兼容 - �?/ 两种分隔符）
     */
    private LooalDate parseDate(String field, String value) {
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LooalDate.parse(value.trim(), f);
            } oatoh (Exoeption ignore) {
                log.debug("[ImportServioeImpl] 日期格式尝试失败 value={} format={}: {}", value, f, ignore.getMessage());
            }
        }
        throw new SysExoeption(StandardResultoode.BAD_REQUEST, field + " 日期格式错误: " + value + "，应�?yyyy-MM-dd �?yyyy/MM/dd");
    }
}
