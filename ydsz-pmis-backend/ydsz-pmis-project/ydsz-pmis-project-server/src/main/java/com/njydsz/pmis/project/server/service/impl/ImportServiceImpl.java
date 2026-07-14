package com.njydsz.pmis.project.server.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.pmis.common.excel.core.ExcelFacade;
import com.njydsz.pmis.common.core.response.BaseResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.project.domain.dto.RateCardCreateDTO;
import com.njydsz.pmis.project.domain.dto.RateCardImportDTO;
import com.njydsz.pmis.project.server.service.ImportService;
import com.njydsz.pmis.project.server.service.RateCardService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 批量导入服务实现
 *
 * <p>当前支持 bizType：rate-card。其余业务类型可按相同模式扩展 register()。
 * 设计要点：
 *   1. 模板下载与导入共用 DTO，避免表头/字段错位
 *   2. 失败行收集（行号 + 原始数据 + 原因），前端可下载错误清单
 *   3. 解析日期兼容 yyyy-MM-dd 与 yyyy/MM/dd 两种格式
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportServiceImpl implements ImportService {

    /** 支持的日期格式（兼容 yyyy-MM-dd 与 yyyy/MM/dd） */
    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    };

    /** 费率卡服务（批量导入费率卡） */
    private final RateCardService rateCardService;

    @Override
    public ImportService.TemplateBundle buildTemplate(String bizType) {
        if ("rate-card".equals(bizType)) {
            List<RateCardImportDTO> sample = new ArrayList<>();
            RateCardImportDTO demo = new RateCardImportDTO();
            demo.setLevel("L5");
            demo.setCustomerType("ENT");
            demo.setProjectType("T&M");
            demo.setUnitPrice(new BigDecimal("1800.00"));
            demo.setEffectiveDate("2026-07-01");
            demo.setExpiryDate("2026-12-31");
            demo.setCurrency("CNY");
            demo.setRemark("示例：L5 T&M 客户类型 ENT，半年期");
            sample.add(demo);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ExcelFacade.write(out, RateCardImportDTO.class)
                    .headRowNumber(0)
                    .sheet("费率卡")
                    .doWrite(sample);
            byte[] bytes = out.toByteArray();
            return new ImportService.TemplateBundle(RateCardImportDTO.class, bytes, "费率卡_导入模板.xlsx");
        }
        throw new SysException(BaseResultCode.BAD_REQUEST, "error.execution.msg_715cbb1f", bizType);
    }

    @Override
    public ImportResult importFile(String bizType, MultipartFile file) throws IOException {
        if ("rate-card".equals(bizType)) {
            return importRateCard(file);
        }
        throw new SysException(BaseResultCode.BAD_REQUEST, "error.execution.msg_715cbb1f", bizType);
    }

    /**
     * 导入费率卡
     */
    private ImportResult importRateCard(MultipartFile file) throws IOException {
        List<RateCardImportDTO> rows = ExcelFacade.read(file.getInputStream(), RateCardImportDTO.class)
                .headRowNumber(0)
                .sheet()
                .doReadAll();
        int total = rows == null ? 0 : rows.size();
        int success = 0;
        List<FailureRow> failures = new ArrayList<>();

        if (rows == null) {
            return new ImportResult(0, 0, 0, failures);
        }

        for (int i = 0; i < rows.size(); i++) {
            RateCardImportDTO dto = rows.get(i);
            try {
                RateCardCreateDTO create = toCreateDTO(dto);
                rateCardService.create(create);
                success++;
            } catch (Exception e) {
                Map<String, String> original = new LinkedHashMap<>();
                original.put("level", dto.getLevel());
                original.put("customerType", dto.getCustomerType());
                original.put("projectType", dto.getProjectType());
                original.put("unitPrice", dto.getUnitPrice() == null ? "" : dto.getUnitPrice().toPlainString());
                original.put("effectiveDate", dto.getEffectiveDate());
                failures.add(new FailureRow(i + 2, original, e.getMessage()));
                log.warn("[ImportRateCard] row {} failed: {}", i + 2, e.getMessage());
            }
        }
        return new ImportResult(total, success, total - success, failures);
    }

    /**
     * 导入 DTO → 业务创建 DTO
     */
    private RateCardCreateDTO toCreateDTO(RateCardImportDTO src) {
        if (src.getLevel() == null || src.getLevel().isBlank()) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.execution.msg_11653d4c");
        }
        if (src.getUnitPrice() == null) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.execution.msg_d1b0b464");
        }
        RateCardCreateDTO dto = new RateCardCreateDTO();
        dto.setRateCode("RC-IMPORT-" + System.currentTimeMillis() + "-" + Math.abs(System.nanoTime() % 1000));
        dto.setLevelCode(src.getLevel().trim());
        dto.setCustomerLevel(src.getCustomerType() == null ? null : src.getCustomerType().trim());
        dto.setProjectType(src.getProjectType() == null ? null : src.getProjectType().trim());
        dto.setBillingUnit("DAY");
        dto.setRateAmount(src.getUnitPrice());
        dto.setCurrency(src.getCurrency() == null || src.getCurrency().isBlank() ? "CNY" : src.getCurrency().trim());
        dto.setEffectiveDate(parseDate("effectiveDate", src.getEffectiveDate()));
        if (src.getExpiryDate() != null && !src.getExpiryDate().isBlank()) {
            dto.setExpiryDate(parseDate("expiryDate", src.getExpiryDate()));
        }
        dto.setStatus("ACTIVE");
        dto.setRemark(src.getRemark());
        return dto;
    }

    /**
     * 解析日期字符串（兼容 - 与 / 两种分隔符）
     */
    private LocalDate parseDate(String field, String value) {
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(value.trim(), f);
            } catch (Exception ignore) {
                log.debug("[ImportServiceImpl] 日期格式尝试失败 value={} format={}: {}", value, f, ignore.getMessage());
            }
        }
        throw new SysException(BaseResultCode.BAD_REQUEST, field + " 日期格式错误: " + value + "，应为 yyyy-MM-dd 或 yyyy/MM/dd");
    }
}
