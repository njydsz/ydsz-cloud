package com.njydsz.pmis.sales.service.impl.contract;

import com.njydsz.pmis.common.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.DataScope;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.aspect.DataScopeAspect;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.DataScopeHelper;
import com.njydsz.pmis.sales.assembler.NameAssembler;
import com.njydsz.pmis.sales.dto.contract.ContractCreateDTO;
import com.njydsz.pmis.sales.dto.contract.ContractStatusDTO;
import com.njydsz.pmis.sales.engine.ContractRiskEvaluator;
import com.njydsz.pmis.sales.entity.contract.ContractDO;
import com.njydsz.pmis.sales.enums.contract.ContractStatus;
import com.njydsz.pmis.sales.enums.execution.RiskLevel;
import com.njydsz.pmis.sales.mapper.contract.ContractMapper;
import com.njydsz.pmis.sales.service.contract.ContractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 合同服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractServiceImpl implements ContractService {

    /** 合同 Mapper */
    private final ContractMapper contractMapper;
    /** 名称装配器（用于 Feign 补齐客户/负责人名称） */
    private final NameAssembler nameAssembler;

    /**
     * 创建合同。
     * <p>处理流程：参数校验 → 编号唯一性预检 → 属性拷贝 →
     * 默认状态 DRAFT、默认币种 CNY、默认租户 → 自动风险评估 → 持久化。</p>
     *
     * @param dto 合同创建参数
     * @return 合同 ID
     * @throws BizException 编号重复或参数非法时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(ContractCreateDTO dto) {
        validate(dto);
        if (contractMapper.selectByCode(dto.getContractCode()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "error.project.msg_f038adba", dto.getContractCode());
        }
        ContractDO c = new ContractDO();
        BeanUtils.copyProperties(dto, c);
        if (!StringUtils.hasText(c.getStatus())) {
            c.setStatus(ContractStatus.DRAFT.getCode());
        }
        if (!StringUtils.hasText(c.getCurrency())) {
            c.setCurrency("CNY");
        }
        if (c.getTenantId() == null) c.setTenantId(TenantContext.getTenantId());
        // 自动风险评估
        if (!StringUtils.hasText(c.getRiskLevel())) {
            c.setRiskLevel(ContractRiskEvaluator.evaluate(c).name());
        }
        contractMapper.insert(c);
        // 装配名称（满足"create 路径必须装配 foreign-key name"约束）
        assembleNames(c);
        log.info("[Contract] 创建合同: code={} name={}", c.getContractCode(), c.getContractName());
        return c.getId();
    }

    /**
     * 合同状态迁移（遵循 ContractStatus 状态机）。
     *
     * @param dto 状态迁移参数
     * @throws BizException 合同不存在、目标状态未知或迁移路径非法时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(ContractStatusDTO dto) {
        ContractDO c = getById(dto.getId());
        ContractStatus from = ContractStatus.fromCode(c.getStatus());
        ContractStatus to = ContractStatus.fromCode(dto.getTargetStatus());
        if (to == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_7bc741c6", dto.getTargetStatus());
        }
        if (from == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_2e33226a", c.getStatus());
        }
        if (!from.canTransitTo(to)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "error.project.msg_01c65a70", from.getDesc(), to.getDesc());
        }
        contractMapper.updateStatus(c.getId(), to.getCode());
        log.info("[Contract] 状态迁移: id={} {} -> {}", c.getId(), from.getCode(), to.getCode());
    }

    /**
     * 删除合同（逻辑删除）。
     *
     * @param id 合同 ID
     * @throws BizException 合同不存在时抛出
     */
    @Override
    public void delete(String id) {
        ContractDO c = getById(id);
        contractMapper.deleteById(c.getId());
        log.info("[Contract] 删除合同: id={}", id);
    }

    /**
     * 根据合同 ID 查询合同详情。
     * <p>查询结果会通过 Feign 补齐客户/负责人名称。</p>
     *
     * @param id 合同 ID
     * @return 合同实体
     * @throws BizException 合同不存在时抛出
     */
    @Override
    @Transactional(readOnly = true)
    public ContractDO getById(String id) {
        ContractDO c = contractMapper.selectById(id);
        if (c == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.project.msg_22d39b90");
        }
        // P0-4: 越权防护 - 非超管只能查看自己创建的合同
        DataScopeAspect.assertAllowByOwner(c.getCreatedBy());
        assembleNames(c);
        return c;
    }

    /**
     * 分页查询合同列表，按创建时间倒序。
     * <p>结果集中的每条记录会通过 Feign 补齐客户/负责人名称。</p>
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词（编号/名称/客户名），可空
     * @param status       状态码，可空
     * @param contractType 合同类型，可空
     * @param riskLevel    风险等级，可空
     * @return 分页结果
     */
    @Override
    @DataScope(userColumn = "created_by")
    @Transactional(readOnly = true)
    public Page<ContractDO> page(int page, int size, String keyword, String status,
                                 String contractType, String riskLevel) {
        Page<ContractDO> p = new Page<>(page, size);
        LambdaQueryWrapper<ContractDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(ContractDO::getContractCode, keyword)
                    .or().like(ContractDO::getContractName, keyword)
                    .or().like(ContractDO::getCustomerName, keyword));
        }
        if (StringUtils.hasText(status)) w.eq(ContractDO::getStatus, status);
        if (StringUtils.hasText(contractType)) w.eq(ContractDO::getContractType, contractType);
        if (StringUtils.hasText(riskLevel)) w.eq(ContractDO::getRiskLevel, riskLevel);
        // P0-5: 数据权限 SQL 注入
        String ds = DataScopeHelper.buildSqlFragment("", "", "dept_id", "created_by");
        if (!ds.isEmpty()) w.apply(ds);
        w.orderByDesc(ContractDO::getCreatedAt);
        Page<ContractDO> result = contractMapper.selectPage(p, w);
        if (result != null && result.getRecords() != null) {
            batchAssembleNames(result.getRecords());
        }
        return result;
    }

    /**
     * 重新计算风险等级并落库。
     *
     * @param id 合同 ID
     * @return 风险等级码（RiskLevel.code）
     * @throws BizException 合同不存在时抛出
     */
    @Override
    public String evaluateRisk(String id) {
        ContractDO c = getById(id);
        RiskLevel level = ContractRiskEvaluator.evaluate(c);
        c.setRiskLevel(level.name());
        contractMapper.updateById(c);
        return level.name();
    }

    /**
     * 按状态聚合计数。
     *
     * @param tenantId 租户 ID，为空时取 TenantContext.getTenantId()
     * @return 每种状态对应的数量列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> aggregateByStatus(String tenantId) {
        if (tenantId == null) tenantId = TenantContext.getTenantId();
        return contractMapper.aggregateByStatus(tenantId);
    }

    /**
     * 按风险等级聚合计数。
     *
     * @param tenantId 租户 ID，为空时取 TenantContext.getTenantId()
     * @return 每种风险等级对应的数量列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> aggregateByRisk(String tenantId) {
        if (tenantId == null) tenantId = TenantContext.getTenantId();
        return contractMapper.aggregateByRisk(tenantId);
    }

    /**
     * 校验合同创建参数。
     *
     * @param dto 合同创建参数
     * @throws BizException 参数为空、编号/名称/类型/客户/负责人缺失、金额为负或日期不合法时抛出
     */
    private void validate(ContractCreateDTO dto) {
        if (dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_d9712a58");
        }
        if (!StringUtils.hasText(dto.getContractCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_8d3e1723");
        }
        if (!StringUtils.hasText(dto.getContractName())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_c6c8edbf");
        }
        if (!StringUtils.hasText(dto.getCustomerId())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_6de1fd36");
        }
        if (!StringUtils.hasText(dto.getContractType())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_fc52e1b0");
        }
        if (dto.getTotalAmount() == null || dto.getTotalAmount().signum() < 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_8ece143c");
        }
        if (!StringUtils.hasText(dto.getOwnerId())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_26804acb");
        }
        if (dto.getEffectiveDate() != null && dto.getExpireDate() != null
                && dto.getExpireDate().isBefore(dto.getEffectiveDate())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.project.msg_40094d71");
        }
    }

    /**
     * 装配客户/负责人名称。
     * <p>仅当名称为空且对应 ID 不为空时通过 Feign 调用用户服务补齐；调用失败静默忽略。</p>
     *
     * @param c 合同实体，为空或装配器为空时直接返回
     */
    private void assembleNames(ContractDO c) {
        if (c == null || nameAssembler == null) return;
        if (!StringUtils.hasText(c.getCustomerName()) && StringUtils.hasText(c.getCustomerId())) {
            try {
                String n = nameAssembler.resolveCustomer(c.getCustomerId());
                if (n != null) c.setCustomerName(n);
            } catch (Exception e) { log.warn("解析客户名称失败 customerId={}: {}", c.getCustomerId(), e.getMessage(), e); }
        }
        if (!StringUtils.hasText(c.getOwnerName()) && StringUtils.hasText(c.getOwnerId())) {
            try {
                String n = nameAssembler.resolveEmployee(c.getOwnerId());
                if (n != null) c.setOwnerName(n);
            } catch (Exception e) { log.warn("解析负责人名称失败 ownerId={}: {}", c.getOwnerId(), e.getMessage(), e); }
        }
    }

    /**
     * 批量装配客户/负责人名称（避免 N+1 Feign 调用）。
     * <p>三步法：① 用 Set 去重收集待解析 ID；② 一次性批量 Feign 查询；③ Map 查找填充名称。
     * 装配字段与 {@link #assembleNames(ContractDO)} 保持一致。</p>
     *
     * @param records 合同列表，为空或装配器为空时直接返回
     */
    private void batchAssembleNames(List<ContractDO> records) {
        if (records == null || records.isEmpty() || nameAssembler == null) return;
        // 第 1 步：收集需要解析的 ID（用 Set 去重）
        Set<String> customerIds = new HashSet<>();
        Set<String> employeeIds = new HashSet<>();
        for (ContractDO rec : records) {
            if (!StringUtils.hasText(rec.getCustomerName()) && StringUtils.hasText(rec.getCustomerId())) {
                customerIds.add(rec.getCustomerId());
            }
            if (!StringUtils.hasText(rec.getOwnerName()) && StringUtils.hasText(rec.getOwnerId())) {
                employeeIds.add(rec.getOwnerId());
            }
        }
        // 第 2 步：一次性批量查询（空集合守卫，Set → ArrayList 转换）
        Map<String, String> customerNames = customerIds.isEmpty()
                ? Map.of() : nameAssembler.batchCustomerName(new ArrayList<>(customerIds));
        Map<String, String> employeeNames = employeeIds.isEmpty()
                ? Map.of() : nameAssembler.batchEmployeeName(new ArrayList<>(employeeIds));
        // 第 3 步：循环填充名称（Map 查找，Feign 失败时 Map 为空自然跳过）
        for (ContractDO rec : records) {
            if (!StringUtils.hasText(rec.getCustomerName()) && StringUtils.hasText(rec.getCustomerId())) {
                String n = customerNames.get(rec.getCustomerId());
                if (n != null) rec.setCustomerName(n);
            }
            if (!StringUtils.hasText(rec.getOwnerName()) && StringUtils.hasText(rec.getOwnerId())) {
                String n = employeeNames.get(rec.getOwnerId());
                if (n != null) rec.setOwnerName(n);
            }
        }
    }
}
