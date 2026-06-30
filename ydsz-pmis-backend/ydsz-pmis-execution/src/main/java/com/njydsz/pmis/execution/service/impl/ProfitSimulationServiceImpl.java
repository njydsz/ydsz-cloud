package com.njydsz.pmis.execution.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.ProfitSimulationCreateDTO;
import com.njydsz.pmis.execution.dto.SimulationStatusDTO;
import com.njydsz.pmis.execution.entity.ProfitSimulationDO;
import com.njydsz.pmis.execution.enums.SimulationStatus;
import com.njydsz.pmis.execution.mapper.ProfitSimulationMapper;
import com.njydsz.pmis.execution.service.ProfitSimulationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfitSimulationServiceImpl implements ProfitSimulationService {

    private final ProfitSimulationMapper mapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ProfitSimulationCreateDTO dto) {
        if (dto == null) throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        if (!StringUtils.hasText(dto.getSimulationCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "测算编号不能为空");
        }
        if (!StringUtils.hasText(dto.getSimulationName())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "测算名称不能为空");
        }
        if (dto.getInitiationId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "项目 ID 不能为空");
        }
        if (mapper.selectByCode(dto.getSimulationCode()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "测算编号已存在: " + dto.getSimulationCode());
        }
        ProfitSimulationDO s = new ProfitSimulationDO();
        BeanUtils.copyProperties(dto, s);
        if (!StringUtils.hasText(s.getScenarioType())) s.setScenarioType("BASE");
        s.setStatus(SimulationStatus.DRAFT.getCode());
        // 默认 version：当前项目最大版本 + 1
        Integer maxVer = mapper.maxVersion(dto.getInitiationId());
        s.setVersion(maxVer == null ? 1 : maxVer + 1);
        if (s.getContractAmount() == null) s.setContractAmount(BigDecimal.ZERO);
        // 简化计算：合同金额作为 externalRevenue；利润未配置成本时按目标毛利率反推
        if (s.getExternalRevenue() == null) s.setExternalRevenue(s.getContractAmount());
        if (s.getTargetMargin() == null) s.setTargetMargin(BigDecimal.ZERO);
        if (s.getInternalCost() == null && s.getTargetMargin().signum() > 0) {
            // internalCost = revenue * (1 - targetMargin)
            BigDecimal cost = s.getExternalRevenue().multiply(
                    BigDecimal.ONE.subtract(s.getTargetMargin()))
                    .setScale(2, RoundingMode.HALF_UP);
            s.setInternalCost(cost);
        }
        if (s.getInternalCost() == null) s.setInternalCost(BigDecimal.ZERO);
        // 利润指标
        s.setGrossProfit(s.getExternalRevenue().subtract(s.getInternalCost()));
        s.setGrossMargin(s.getExternalRevenue().signum() == 0
                ? BigDecimal.ZERO
                : s.getGrossProfit().divide(s.getExternalRevenue(), 4, RoundingMode.HALF_UP));
        if (s.getTenantId() == null) s.setTenantId(1L);
        if (s.getProviderTraceId() == null) s.setProviderTraceId("");

        mapper.insert(s);
        log.info("[ProfitSim] 创建测算: code={} project={} v{} scenario={} margin={}",
                s.getSimulationCode(), s.getInitiationId(), s.getVersion(),
                s.getScenarioType(), s.getGrossMargin());
        return s.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(SimulationStatusDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        }
        ProfitSimulationDO s = mapper.selectById(dto.getId());
        if (s == null) throw new BizException(BizErrorCode.NOT_FOUND, "测算不存在");
        SimulationStatus from = SimulationStatus.fromCode(s.getStatus());
        SimulationStatus to = SimulationStatus.fromCode(dto.getTargetStatus());
        if (to == null) throw new BizException(BizErrorCode.BAD_REQUEST, "未知状态: " + dto.getTargetStatus());
        if (from == null || !from.canTransitTo(to)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "状态不允许迁移: " + (from == null ? "未知" : from.getDesc()) + " → " + to.getDesc());
        }
        s.setStatus(to.getCode());
        if (to == SimulationStatus.APPROVED) {
            s.setApproverName(dto.getApproverName());
            s.setApprovedAt(LocalDateTime.now());
        }
        mapper.updateById(s);
        log.info("[ProfitSim] 状态迁移: id={} {} -> {}", dto.getId(), from.getCode(), to.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (id == null) throw new BizException(BizErrorCode.BAD_REQUEST, "ID 不能为空");
        ProfitSimulationDO s = mapper.selectById(id);
        if (s == null) return;
        SimulationStatus st = SimulationStatus.fromCode(s.getStatus());
        if (st == SimulationStatus.APPROVED || st == SimulationStatus.ARCHIVED) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "已审批/归档的测算不可删除");
        }
        mapper.deleteById(id);
    }

    @Override
    public ProfitSimulationDO getById(Long id) {
        if (id == null) throw new BizException(BizErrorCode.BAD_REQUEST, "ID 不能为空");
        ProfitSimulationDO s = mapper.selectById(id);
        if (s == null) throw new BizException(BizErrorCode.NOT_FOUND, "测算不存在");
        return s;
    }

    @Override
    public List<ProfitSimulationDO> listByInitiation(Long initiationId) {
        if (initiationId == null) return List.of();
        return mapper.selectByInitiation(initiationId);
    }

    @Override
    public List<Map<String, Object>> compare(Long initiationId) {
        if (initiationId == null) return List.of();
        List<ProfitSimulationDO> list = mapper.selectByInitiation(initiationId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ProfitSimulationDO s : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getId());
            m.put("code", s.getSimulationCode());
            m.put("name", s.getSimulationName());
            m.put("version", s.getVersion());
            m.put("scenario", s.getScenarioType());
            m.put("revenue", s.getExternalRevenue());
            m.put("cost", s.getInternalCost());
            m.put("profit", s.getGrossProfit());
            m.put("margin", s.getGrossMargin());
            m.put("target", s.getTargetMargin());
            m.put("marginAchieved", s.getTargetMargin() != null
                    && s.getGrossMargin() != null
                    && s.getGrossMargin().compareTo(s.getTargetMargin()) >= 0);
            m.put("status", s.getStatus());
            result.add(m);
        }
        return result;
    }

    @Override
    public Page<ProfitSimulationDO> page(int page, int size, Long initiationId, String scenarioType, String status) {
        Page<ProfitSimulationDO> p = new Page<>(page, size);
        LambdaQueryWrapper<ProfitSimulationDO> w = new LambdaQueryWrapper<>();
        if (initiationId != null) w.eq(ProfitSimulationDO::getInitiationId, initiationId);
        if (StringUtils.hasText(scenarioType)) w.eq(ProfitSimulationDO::getScenarioType, scenarioType);
        if (StringUtils.hasText(status)) w.eq(ProfitSimulationDO::getStatus, status);
        w.orderByDesc(ProfitSimulationDO::getVersion);
        return mapper.selectPage(p, w);
    }
}
