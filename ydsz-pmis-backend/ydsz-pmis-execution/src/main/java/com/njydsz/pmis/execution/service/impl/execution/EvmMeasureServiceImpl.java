package com.njydsz.pmis.execution.service.impl.execution;

import com.njydsz.pmis.common.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.config.ThresholdProvider;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.execution.EvmMeasureCreateDTO;
import com.njydsz.pmis.execution.engine.EvmCalculator;
import com.njydsz.pmis.execution.entity.execution.EvmMeasureDO;
import com.njydsz.pmis.execution.mapper.execution.EvmMeasureMapper;
import com.njydsz.pmis.execution.service.execution.EvmMeasureService;
import com.njydsz.pmis.execution.vo.execution.EvmMeasureVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * EVM 挣值测量服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvmMeasureServiceImpl implements EvmMeasureService {

    /** EVM 挣值度量 Mapper */
    private final EvmMeasureMapper evmMapper;
    /** 阈值配置提供者（SPI/CPI 红黄灯阈值） */
    private final ThresholdProvider thresholdProvider;

    /**
     * 项目级 EVM 基线版本号: initiationId -> 自增版本号.
     * 内存维护, 进程重启后从 1 重新计数 (与已存量的 baselineVersion=N 的测量无冲突).
     */
    private final Map<String, AtomicInteger> baselineVersions = new ConcurrentHashMap<>();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String save(EvmMeasureCreateDTO dto) {
        if (dto == null) throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_d9712a58");
        if (dto.getInitiationId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_576c2b5e");
        }
        if (!StringUtils.hasText(dto.getPeriod())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_d53b5f27");
        }
        if (dto.getPv() == null || dto.getEv() == null || dto.getAc() == null || dto.getBac() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_e52f5250");
        }
        if (dto.getMeasureDate() == null) dto.setMeasureDate(LocalDate.now());

        EvmCalculator.EVMResult r = EvmCalculator.calculate(
                dto.getPv(), dto.getEv(), dto.getAc(), dto.getBac(),
                thresholdProvider.cpiYellow(),
                thresholdProvider.cpiRed(),
                thresholdProvider.spiYellow(),
                thresholdProvider.spiRed());

        // 幂等：相同 initiation+wbs+period 视为同一条
        EvmMeasureDO existing = evmMapper.selectByInitiationAndPeriod(
                dto.getInitiationId(), dto.getWbsTaskId(), dto.getPeriod());
        EvmMeasureDO m = existing != null ? existing : new EvmMeasureDO();
        if (existing == null) {
            BeanUtils.copyProperties(dto, m);
            m.setTenantId(TenantContext.getTenantId());
            m.setProviderTraceId("");
        } else {
            m.setPv(dto.getPv());
            m.setEv(dto.getEv());
            m.setAc(dto.getAc());
            m.setBac(dto.getBac());
            m.setMeasureDate(dto.getMeasureDate());
            m.setRemark(dto.getRemark());
        }
        m.setCv(r.cv);
        m.setSv(r.sv);
        m.setCpi(r.cpi);
        m.setSpi(r.spi);
        m.setEac(r.eac);
        m.setVac(r.vac);
        m.setEtc(r.etc);
        m.setTcpi(r.tcpi);
        m.setAlertLevel(r.alertLevel.getCode());
        m.setAlertReason(r.alertReason);

        if (existing == null) evmMapper.insert(m);
        else evmMapper.updateById(m);
        log.info("[EVM] 保存测量: initiation={} period={} cpi={} spi={} alert={}",
                m.getInitiationId(), m.getPeriod(), m.getCpi(), m.getSpi(), m.getAlertLevel());
        return m.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public EvmMeasureVO getById(String id) {
        if (id == null) throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_411b6827");
        EvmMeasureDO m = evmMapper.selectById(id);
        if (m == null) throw new BizException(BizErrorCode.NOT_FOUND, "error.execution.msg_c14ffd5d");
        return toVo(m);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvmMeasureVO> listByInitiation(String initiationId) {
        if (initiationId == null) return List.of();
        List<EvmMeasureDO> list = evmMapper.selectByInitiation(initiationId);
        if (list == null || list.isEmpty()) return List.of();
        return list.stream().map(this::toVo).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvmMeasureVO> listByWbs(String wbsTaskId) {
        if (wbsTaskId == null) return List.of();
        List<EvmMeasureDO> list = evmMapper.selectByWbs(wbsTaskId);
        if (list == null || list.isEmpty()) return List.of();
        return list.stream().map(this::toVo).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> trend(String initiationId) {
        if (initiationId == null) return List.of();
        return evmMapper.trendByPeriod(initiationId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(String initiationId) {
        Map<String, Object> dash = new HashMap<>();
        if (initiationId == null) return dash;
        List<EvmMeasureDO> all = evmMapper.selectByInitiation(initiationId);
        if (all.isEmpty()) {
            dash.put("alertLevel", "NORMAL");
            dash.put("measureCount", 0);
            return dash;
        }
        EvmMeasureDO latest = all.get(0);
        BigDecimal totalCv = BigDecimal.ZERO;
        BigDecimal totalSv = BigDecimal.ZERO;
        BigDecimal totalVac = BigDecimal.ZERO;
        int yellow = 0, red = 0;
        for (EvmMeasureDO m : all) {
            if (m.getCv() != null) totalCv = totalCv.add(m.getCv());
            if (m.getSv() != null) totalSv = totalSv.add(m.getSv());
            if (m.getVac() != null) totalVac = totalVac.add(m.getVac());
            if ("YELLOW".equals(m.getAlertLevel())) yellow++;
            else if ("RED".equals(m.getAlertLevel())) red++;
        }
        dash.put("latestPeriod", latest.getPeriod());
        dash.put("latestCpi", latest.getCpi());
        dash.put("latestSpi", latest.getSpi());
        dash.put("latestEac", latest.getEac());
        dash.put("latestVac", latest.getVac());
        dash.put("latestAlert", latest.getAlertLevel());
        dash.put("totalCv", totalCv);
        dash.put("totalSv", totalSv);
        dash.put("totalVac", totalVac);
        dash.put("measureCount", all.size());
        dash.put("yellowCount", yellow);
        dash.put("redCount", red);
        return dash;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EvmMeasureVO> page(int page, int size, String initiationId, String alertLevel) {
        Page<EvmMeasureDO> p = new Page<>(page, size);
        LambdaQueryWrapper<EvmMeasureDO> w = new LambdaQueryWrapper<>();
        if (initiationId != null) w.eq(EvmMeasureDO::getInitiationId, initiationId);
        if (StringUtils.hasText(alertLevel)) w.eq(EvmMeasureDO::getAlertLevel, alertLevel);
        w.orderByDesc(EvmMeasureDO::getPeriod);
        Page<EvmMeasureDO> doPage = evmMapper.selectPage(p, w);
        Page<EvmMeasureVO> voPage = new Page<>(doPage.getCurrent(), doPage.getSize(), doPage.getTotal());
        if (doPage.getRecords() != null && !doPage.getRecords().isEmpty()) {
            voPage.setRecords(doPage.getRecords().stream().map(this::toVo).toList());
        } else {
            voPage.setRecords(List.of());
        }
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        if (id == null) throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_411b6827");
        evmMapper.deleteById(id);
    }

    @Override
    public Map<String, Object> recalculateBaseline(String initiationId, String reason) {
        Map<String, Object> result = new HashMap<>();
        if (initiationId == null) {
            result.put("ok", false);
            result.put("reason", "initiationId 不能为空");
            return result;
        }
        int affected = (int) countByInitiation(initiationId);
        int version = baselineVersions
                .computeIfAbsent(initiationId, k -> new AtomicInteger(0))
                .incrementAndGet();
        result.put("ok", true);
        result.put("initiationId", initiationId);
        result.put("baselineVersion", version);
        result.put("affectedMeasures", affected);
        result.put("recalcReason", reason);
        result.put("recalcAt", System.currentTimeMillis());
        log.info("[EVM] 基线重算: initiation={} version={} affected={} reason={}",
                initiationId, version, affected, reason);
        return result;
    }

    @Override
    public int currentBaselineVersion(String initiationId) {
        if (initiationId == null) return 0;
        AtomicInteger v = baselineVersions.get(initiationId);
        return v == null ? 0 : v.get();
    }

    private long countByInitiation(String initiationId) {
        if (initiationId == null) return 0L;
        // 用 listByInitiation.size() 简化; 大数据量场景可后续替换为 count mapper
        List<EvmMeasureDO> all = evmMapper.selectByInitiation(initiationId);
        return all == null ? 0L : all.size();
    }

    /**
     * DO → VO 转换（剥离 tenantId / providerTraceId / deleted 等敏感字段）
     *
     * <p>手写 setter 模式，参考 {@code UserAccountServiceImpl#toVo}。
     *
     * @param m EVM 测量 DO
     * @return EVM 测量 VO
     */
    private EvmMeasureVO toVo(EvmMeasureDO m) {
        if (m == null) return null;
        EvmMeasureVO v = new EvmMeasureVO();
        v.setId(m.getId());
        v.setInitiationId(m.getInitiationId());
        v.setWbsTaskId(m.getWbsTaskId());
        v.setPeriod(m.getPeriod());
        v.setPv(m.getPv());
        v.setEv(m.getEv());
        v.setAc(m.getAc());
        v.setBac(m.getBac());
        v.setCpi(m.getCpi());
        v.setSpi(m.getSpi());
        v.setCv(m.getCv());
        v.setSv(m.getSv());
        v.setEac(m.getEac());
        v.setVac(m.getVac());
        v.setEtc(m.getEtc());
        v.setTcpi(m.getTcpi());
        v.setAlertLevel(m.getAlertLevel());
        v.setAlertReason(m.getAlertReason());
        v.setMeasureDate(m.getMeasureDate());
        v.setRemark(m.getRemark());
        v.setCreatedAt(m.getCreatedAt());
        v.setUpdatedAt(m.getUpdatedAt());
        return v;
    }
}
