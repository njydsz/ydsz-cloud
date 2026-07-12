package com.njydsz.pmis.finance.server.service.impl.finance;

import com.njydsz.pmis.common.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.auth.annotation.DataScope;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.common.security.DataScopeHelper;
import com.njydsz.pmis.finance.domain.dto.ProfitSimulationCreateDTO;
import com.njydsz.pmis.finance.domain.dto.SimulationStatusDTO;
import com.njydsz.pmis.finance.domain.entity.ProfitSimulationDO;
import com.njydsz.pmis.finance.domain.enums.SimulationStatus;
import com.njydsz.pmis.finance.infra.mapper.ProfitSimulationMapper;
import com.njydsz.pmis.finance.server.service.finance.ProfitSimulationService;
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

/**
 * 利润测算服务实现
 *
 * <p>负责利润测算方案的创建、状态迁移、版本管理与多版本对比。
 * 状态机：DRAFT → SUBMITTED → APPROVED/REJECTED → ARCHIVED。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfitSimulationServiceImpl implements ProfitSimulationService {

    /** 利润测算 Mapper */
    private final ProfitSimulationMapper mapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(ProfitSimulationCreateDTO dto) {
        if (dto == null) throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_d9712a58");
        if (!StringUtils.hasText(dto.getSimulationCode())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_dd45c4cb");
        }
        if (!StringUtils.hasText(dto.getSimulationName())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_00a76083");
        }
        if (dto.getInitiationId() == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_576c2b5e");
        }
        if (mapper.selectByCode(dto.getSimulationCode()) != null) {
            throw new SysException(StandardResultCode.DUPLICATE_KEY, "error.execution.msg_0e48bb17", dto.getSimulationCode());
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
        if (s.getTenantId() == null) s.setTenantId(TenantContext.getTenantId());
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
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_d9712a58");
        }
        ProfitSimulationDO s = mapper.selectById(dto.getId());
        if (s == null) throw new SysException(StandardResultCode.NOT_FOUND, "error.execution.msg_a246acf1");
        SimulationStatus from = SimulationStatus.fromCode(s.getStatus());
        SimulationStatus to = SimulationStatus.fromCode(dto.getTargetStatus());
        if (to == null) throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_7bc741c6", dto.getTargetStatus());
        if (from == null || !from.canTransitTo(to)) {
            throw new SysException(StandardResultCode.BAD_REQUEST,
                    "error.execution.msg_01c65a70", (from == null ? "未知" : from.getDesc()), to.getDesc());
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
    public void delete(String id) {
        if (id == null) throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_411b6827");
        ProfitSimulationDO s = mapper.selectById(id);
        if (s == null) return;
        SimulationStatus st = SimulationStatus.fromCode(s.getStatus());
        if (st == SimulationStatus.APPROVED || st == SimulationStatus.ARCHIVED) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_feb72f89");
        }
        mapper.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public ProfitSimulationDO getById(String id) {
        if (id == null) throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_411b6827");
        ProfitSimulationDO s = mapper.selectById(id);
        if (s == null) throw new SysException(StandardResultCode.NOT_FOUND, "error.execution.msg_a246acf1");
        return s;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProfitSimulationDO> listByInitiation(String initiationId) {
        if (initiationId == null) return List.of();
        return mapper.selectByInitiation(initiationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> compare(String initiationId) {
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
            BaseResponse.add(m);
        }
        return result;
    }

    @Override
    @DataScope(deptColumn = "dept_id", userColumn = "created_by")
    @Transactional(readOnly = true)
    public Page<ProfitSimulationDO> page(int page, int size, String initiationId, String scenarioType, String status) {
        Page<ProfitSimulationDO> p = new Page<>(page, size);
        LambdaQueryWrapper<ProfitSimulationDO> w = new LambdaQueryWrapper<>();
        if (initiationId != null) w.eq(ProfitSimulationDO::getInitiationId, initiationId);
        if (StringUtils.hasText(scenarioType)) w.eq(ProfitSimulationDO::getScenarioType, scenarioType);
        if (StringUtils.hasText(status)) w.eq(ProfitSimulationDO::getStatus, status);
        // 数据权限 SQL 注入
        String ds = DataScopeHelper.buildSqlFragment("", "", "dept_id", "created_by");
        if (!ds.isEmpty()) w.apply(ds);
        w.orderByDesc(ProfitSimulationDO::getVersion);
        return mapper.selectPage(p, w);
    }
}
