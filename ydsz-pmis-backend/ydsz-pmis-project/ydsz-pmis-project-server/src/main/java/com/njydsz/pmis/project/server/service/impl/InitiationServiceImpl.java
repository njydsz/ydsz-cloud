package com.njydsz.pmis.project.server.service.impl;

import com.njydsz.pmis.common.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.auth.annotation.DataScope;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.exception.SysException;
import com.njydsz.pmis.common.security.DataScopeHelper;
import com.njydsz.pmis.project.server.assembler.NameAssembler;
import com.njydsz.pmis.project.domain.dto.BudgetItemDTO;
import com.njydsz.pmis.project.domain.dto.GateReviewDTO;
import com.njydsz.pmis.project.domain.dto.InitiationCreateDTO;
import com.njydsz.pmis.project.domain.dto.InitiationStageDTO;
import com.njydsz.pmis.project.domain.entity.BudgetItemDO;
import com.njydsz.pmis.project.domain.entity.GateReviewDO;
import com.njydsz.pmis.project.domain.entity.InitiationDO;
import com.njydsz.pmis.project.domain.enums.GateCode;
import com.njydsz.pmis.project.domain.enums.InitiationStage;
import com.njydsz.pmis.workflow.api.client.WorkflowServiceClient;
import com.njydsz.pmis.project.infra.mapper.BudgetItemMapper;
import com.njydsz.pmis.project.infra.mapper.GateReviewMapper;
import com.njydsz.pmis.project.infra.mapper.InitiationMapper;
import com.njydsz.pmis.project.server.service.InitiationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 立项服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InitiationServiceImpl implements InitiationService {

    /** 允许的预算分类集合：人工/采购/费用/外包/其他 */
    private static final Set<String> BUDGET_CATEGORIES =
            Set.of("LABOR", "PURCHASE", "EXPENSE", "OUTSOURCE", "OTHER");

    /** 允许的门径评审结果集合 */
    private static final Set<String> GATE_RESULTS =
            Set.of("PENDING", "PASSED", "REJECTED", "CONDITIONAL");

    /** 立项 Mapper */
    private final InitiationMapper initiationMapper;
    /** 预算明细 Mapper */
    private final BudgetItemMapper budgetItemMapper;
    /** 门径评审 Mapper */
    private final GateReviewMapper gateReviewMapper;
    /** 名称装配器，用于跨服务解析客户/PM/发起人名称（Feign + try-catch 降级） */
    private final NameAssembler nameAssembler;
    /** 工作流 Feign 客户端，用于启动立项审批流程 */
    private final WorkflowServiceClient workflowServiceClient;

    // ============= 立项主表 =============

    /**
     * 创建立项。
     * <p>处理流程：参数校验 → 项目编号唯一性预检 → 属性拷贝 → 默认值兜底（阶段/级别/租户）
     * → 工期计算 → 名称装配 → 持久化。</p>
     *
     * @param dto 立项创建参数
     * @return 立项 ID
     * @throws SysException 项目编号重复或参数非法时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(InitiationCreateDTO dto) {
        validate(dto);
        if (initiationMapper.selectByCode(dto.getProjectCode()) != null) {
            throw new SysException(StandardResultCode.DUPLICATE_KEY, "error.project.msg_32756e2a", dto.getProjectCode());
        }
        InitiationDO o = new InitiationDO();
        BeanUtils.copyProperties(dto, o);
        if (!StringUtils.hasText(o.getStage())) {
            o.setStage(InitiationStage.PRE_INITIATION.getCode());
        }
        if (!StringUtils.hasText(o.getProjectLevel())) {
            o.setProjectLevel("C");
        }
        if (o.getTenantId() == null) {
            o.setTenantId(TenantContext.getTenantId());
        }
        if (o.getPlannedStartDate() != null && o.getPlannedEndDate() != null) {
            long days = ChronoUnit.DAYS.between(o.getPlannedStartDate(), o.getPlannedEndDate());
            o.setDurationDays((int) Math.max(0, days));
        }
        // 装配客户/PM/发起人名称（容错，Feign 调用失败不阻塞创建）
        assembleNames(o);
        initiationMapper.insert(o);
        log.info("[Initiation] 创建立项: code={} name={}", o.getProjectCode(), o.getProjectName());
        return o.getId();
    }

    /**
     * 立项阶段迁移。
     * <p>校验当前阶段与目标阶段的合法性（{@link InitiationStage#canTransitTo}），
     * 迁移至 APPROVED 时自动设置门径为 CD1。</p>
     *
     * @param dto 阶段迁移参数
     * @throws SysException 阶段非法或迁移路径不允许时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStage(InitiationStageDTO dto) {
        InitiationDO o = getById(dto.getId());
        InitiationStage from = InitiationStage.fromCode(o.getStage());
        InitiationStage to = InitiationStage.fromCode(dto.getTargetStage());
        if (to == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.project.msg_8453405e", dto.getTargetStage());
        }
        if (from == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.project.msg_3895d38d", o.getStage());
        }
        if (!from.canTransitTo(to)) {
            throw new SysException(StandardResultCode.BAD_REQUEST,
                    "error.project.msg_fc28e9a4", from.getDesc(), to.getDesc());
        }
        String gate = to == InitiationStage.APPROVED ? GateCode.CD1.name() : o.getCurrentGate();
        initiationMapper.updateStage(o.getId(), to.getCode(), gate);
        log.info("[Initiation] 阶段迁移: id={} {} -> {}", o.getId(), from.getCode(), to.getCode());
    }

    /**
     * 删除立项（按主键）。
     *
     * @param id 立项 ID
     * @throws SysException 立项不存在时抛出
     */
    @Override
    public void delete(String id) {
        InitiationDO o = getById(id);
        initiationMapper.deleteById(o.getId());
        log.info("[Initiation] 删除立项: id={}", id);
    }

    /**
     * 根据主键查询立项详情，并装配客户/PM/发起人名称。
     *
     * @param id 立项 ID
     * @return 立项实体（含名称）
     * @throws SysException 立项不存在时抛出
     */
    @Override
    @Transactional(readOnly = true)
    public InitiationDO getById(String id) {
        InitiationDO o = initiationMapper.selectById(id);
        if (o == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "error.project.msg_f7fde8f5");
        }
        assembleNames(o);
        return o;
    }

    /**
     * 分页查询立项，支持关键词、阶段、级别、PM 过滤，自动注入数据权限 SQL，
     * 并对结果集中每条记录装配名称。
     *
     * @param page        页码（从 1 开始）
     * @param size        每页大小
     * @param keyword     关键词（编号/名称/客户名），可空
     * @param stage       阶段码，可空
     * @param projectLevel 项目级别，可空
     * @param pmId        PM ID，可空
     * @return 分页结果（每条记录已装配名称）
     */
    @Override
    @DataScope(deptColumn = "business_dept_id", userColumn = "created_by")
    @Transactional(readOnly = true)
    public Page<InitiationDO> page(int page, int size, String keyword, String stage,
                                   String projectLevel, String pmId) {
        Page<InitiationDO> p = new Page<>(page, size);
        LambdaQueryWrapper<InitiationDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(InitiationDO::getProjectCode, keyword)
                    .or().like(InitiationDO::getProjectName, keyword)
                    .or().like(InitiationDO::getCustomerName, keyword));
        }
        if (StringUtils.hasText(stage)) w.eq(InitiationDO::getStage, stage);
        if (StringUtils.hasText(projectLevel)) w.eq(InitiationDO::getProjectLevel, projectLevel);
        if (pmId != null) w.eq(InitiationDO::getPmId, pmId);
        // 数据权限 SQL 注入
        String ds = DataScopeHelper.buildSqlFragment("", "", "business_dept_id", "created_by");
        if (!ds.isEmpty()) w.apply(ds);
        w.orderByDesc(InitiationDO::getCreatedAt);
        Page<InitiationDO> R = initiationMapper.selectPage(p, w);
        if (R != null && R.getRecords() != null && !R.getRecords().isEmpty()) {
            batchAssembleNames(R.getRecords());
        }
        return R;
    }

    /**
     * 批量装配名称（避免 N+1 远程调用）
     *
     * <p>先收集所有 customerId 和 pmId/sponsorId（员工 ID），一次性批量查询，
     * 再循环填充到每条记录中。装配字段与 {@link #assembleNames(InitiationDO)} 保持一致：
     * customerName / pmName / sponsorName。
     *
     * @param records 分页记录列表
     */
    private void batchAssembleNames(List<InitiationDO> records) {
        // 收集需要解析的 ID
        Set<String> customerIds = new HashSet<>();
        Set<String> employeeIds = new HashSet<>();
        for (InitiationDO rec : records) {
            if (!StringUtils.hasText(rec.getCustomerName()) && rec.getCustomerId() != null) {
                customerIds.add(rec.getCustomerId());
            }
            if (!StringUtils.hasText(rec.getPmName()) && rec.getPmId() != null) {
                employeeIds.add(rec.getPmId());
            }
            if (!StringUtils.hasText(rec.getSponsorName()) && rec.getSponsorId() != null) {
                employeeIds.add(rec.getSponsorId());
            }
        }
        // 批量查询
        Map<String, String> customerNames = customerIds.isEmpty()
                ? Map.of() : nameAssembler.batchCustomerName(new ArrayList<>(customerIds));
        Map<String, String> employeeNames = employeeIds.isEmpty()
                ? Map.of() : nameAssembler.batchEmployeeName(new ArrayList<>(employeeIds));
        // 填充名称
        for (InitiationDO rec : records) {
            if (!StringUtils.hasText(rec.getCustomerName()) && rec.getCustomerId() != null) {
                String n = customerNames.get(rec.getCustomerId());
                if (n != null) rec.setCustomerName(n);
            }
            if (!StringUtils.hasText(rec.getPmName()) && rec.getPmId() != null) {
                String n = employeeNames.get(rec.getPmId());
                if (n != null) rec.setPmName(n);
            }
            if (!StringUtils.hasText(rec.getSponsorName()) && rec.getSponsorId() != null) {
                String n = employeeNames.get(rec.getSponsorId());
                if (n != null) rec.setSponsorName(n);
            }
        }
    }

    // ============= 预算 =============

    /**
     * 新增预算明细，并触发预算总额重算。
     * <p>若金额为空但有数量×单价，则自动计算金额。</p>
     *
     * @param dto 预算明细参数
     * @return 预算明细 ID
     * @throws SysException 立项不存在或参数非法时抛出
     */
    @Override
    public String addBudgetItem(BudgetItemDTO dto) {
        validateBudget(dto);
        if (initiationMapper.selectById(dto.getInitiationId()) == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "error.project.msg_f7fde8f5");
        }
        BudgetItemDO b = new BudgetItemDO();
        BeanUtils.copyProperties(dto, b);
        if (b.getAmount() == null && b.getQuantity() != null && b.getUnitPrice() != null) {
            b.setAmount(b.getQuantity().multiply(b.getUnitPrice()));
        }
        budgetItemMapper.insert(b);
        // 重新汇总预算
        recomputeBudget(dto.getInitiationId());
        log.info("[Initiation] 新增预算明细: init={} cat={} amt={}",
                dto.getInitiationId(), dto.getCategory(), b.getAmount());
        return b.getId();
    }

    /**
     * 删除预算明细，并触发预算总额重算。
     *
     * @param id 预算明细 ID
     * @throws SysException 预算明细不存在时抛出
     */
    @Override
    public void deleteBudgetItem(String id) {
        BudgetItemDO b = budgetItemMapper.selectById(id);
        if (b == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "error.project.msg_6b9c2579");
        }
        budgetItemMapper.deleteById(id);
        recomputeBudget(b.getInitiationId());
    }

    /**
     * 查询立项的所有预算明细。
     *
     * @param initiationId 立项 ID
     * @return 预算明细列表，立项 ID 为空时返回空列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<BudgetItemDO> listBudget(String initiationId) {
        if (initiationId == null) return List.of();
        return budgetItemMapper.selectByInitiationId(initiationId);
    }

    /**
     * 按分类汇总预算金额。
     *
     * @param initiationId 立项 ID
     * @return 每种分类对应的汇总金额列表，立项 ID 为空时返回空列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> sumBudgetByCategory(String initiationId) {
        if (initiationId == null) return List.of();
        return budgetItemMapper.sumByCategory(initiationId);
    }

    /**
     * 重新计算立项的预算总额并回写主表。
     * <p>累加所有明细的金额（null 跳过），更新到 InitiationDO.budgetAmount。</p>
     *
     * @param initiationId 立项 ID
     * @return 重算后的预算总额
     */
    @Override
    public BigDecimal recomputeBudget(String initiationId) {
        List<BudgetItemDO> items = budgetItemMapper.selectByInitiationId(initiationId);
        BigDecimal total = BigDecimal.ZERO;
        for (BudgetItemDO b : items) {
            if (b.getAmount() != null) {
                total = total.add(b.getAmount());
            }
        }
        InitiationDO o = initiationMapper.selectById(initiationId);
        if (o != null) {
            o.setBudgetAmount(total);
            initiationMapper.updateById(o);
        }
        return total;
    }

    // ============= 门径 =============

    /**
     * 提交一次门径评审。
     * <p>校验门径编码与评审结果合法性 → 复用或新建评审记录 → 持久化 →
     * 若评审通过则推进立项的 currentGate 到下一门径。</p>
     *
     * @param dto 门径评审参数
     * @return 评审记录 ID
     * @throws SysException 门径编码非法或评审结果非法时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String reviewGate(GateReviewDTO dto) {
        InitiationDO o = getById(dto.getInitiationId());
        GateCode gate = GateCode.fromCode(dto.getGateCode());
        if (gate == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.project.msg_e08dfe9a", dto.getGateCode());
        }
        if (!GATE_RESULTS.contains(dto.getReviewResult().toUpperCase())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.project.msg_64b97ca8", dto.getReviewResult());
        }
        GateReviewDO existing = gateReviewMapper.selectByInitiationAndGate(o.getId(), gate.name());
        GateReviewDO record = existing != null ? existing : new GateReviewDO();
        record.setInitiationId(o.getId());
        record.setGateCode(gate.name());
        record.setGateName(gate.name() + " Gate");
        record.setReviewResult(dto.getReviewResult().toUpperCase());
        record.setDecisionBasis(dto.getDecisionBasis());
        record.setConditions(dto.getConditions());
        record.setReviewAt(LocalDateTime.now());
        GateCode next = GateCode.next(gate);
        record.setNextGate(next == null ? null : next.name());

        if (existing == null) {
            gateReviewMapper.insert(record);
        } else {
            gateReviewMapper.updateById(record);
        }
        // 通过则更新立项 currentGate
        if ("PASSED".equalsIgnoreCase(dto.getReviewResult()) && next != null) {
            o.setCurrentGate(next.name());
            initiationMapper.updateById(o);
        }
        log.info("[Initiation] 门径评审: init={} gate={} R={}",
                o.getId(), gate.name(), dto.getReviewResult());
        return record.getId();
    }

    /**
     * 查询立项的所有门径评审记录。
     *
     * @param initiationId 立项 ID
     * @return 评审记录列表，立项 ID 为空时返回空列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<GateReviewDO> listGateReviews(String initiationId) {
        if (initiationId == null) return List.of();
        return gateReviewMapper.selectByInitiationId(initiationId);
    }

    // ============= 统计 =============

    /**
     * 按阶段聚合计数（租户维度）。
     *
     * @param tenantId 租户 ID，可空（默认 "1"）
     * @return 每种阶段对应的数量列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> aggregateByStage(String tenantId) {
        if (tenantId == null) tenantId = TenantContext.getTenantId();
        return initiationMapper.aggregateByStage(tenantId);
    }

    // ============= 流程集成 =============

    /**
     * 启动立项审批流程（Feign 调用 workflow 服务）。
     * <p>若已存在 workflowId 则跳过；Feign 调用失败时返回 null 不抛异常，
     * 以保证主业务流不被工作流故障阻塞。</p>
     *
     * <p><b>P0-3 修复</b>：移除 @GlobalTransactional 注解。原注解与 try-catch 吞异常的容错策略矛盾——
     * Seata 全局事务依赖异常传播触发回滚，但本方法的业务语义是"失败返回 null 不阻断主流程"，
     * 导致 @GlobalTransactional 形同虚设：Feign 失败时异常被 catch，Seata 误判为成功并提交全局事务，
     * 若 workflow 端已注册分支事务，分支被错误提交，产生孤儿流程实例。
     * 现保留本地 @Transactional 保证 DB 写入原子性，分布式一致性由上层补偿/对账机制兜底。</p>
     *
     * @param id          立项 ID
     * @param initiatorId 发起人 ID
     * @return 流程实例 ID；已存在或调用失败时返回 null
     * @throws SysException 立项不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String startProcess(String id, String initiatorId) {
        InitiationDO o = getById(id);
        if (StringUtils.hasText(o.getWorkflowId())) {
            log.info("[Initiation] 立项 {} 已存在流程实例: {}，跳过启动", id, o.getWorkflowId());
            return o.getWorkflowId();
        }
        Map<String, Object> body = new HashMap<>();
        body.put("businessKey", "PMIS_INIT_" + o.getId());
        body.put("processDefinitionKey", "pmis-initiation");
        body.put("initiator", initiatorId);
        Map<String, Object> vars = new HashMap<>();
        vars.put("initiationId", o.getId());
        vars.put("projectCode", o.getProjectCode());
        vars.put("projectName", o.getProjectName());
        vars.put("projectType", o.getProjectType());
        vars.put("projectLevel", o.getProjectLevel());
        vars.put("estimatedAmount", o.getEstimatedAmount());
        vars.put("customerId", o.getCustomerId());
        vars.put("pmId", o.getPmId());
        body.put("variables", vars);

        String processInstanceId = null;
        try {
            BaseResponse<String> r = workflowServiceClient.startProcess(body);
            if (r != null && r.isSuccess() && r.getData() != null) {
                processInstanceId = r.getData();
            } else {
                log.warn("[Initiation] 启动审批流失败 initiation={} msg={}", id,
                        r == null ? "null" : r.getMessage());
                return null;
            }
        } catch (Exception e) {
            log.error("[Initiation] Feign 调用 workflow 失败: {}", e.getMessage());
            return null;
        }
        o.setWorkflowId(processInstanceId);
        initiationMapper.updateById(o);
        log.info("[Initiation] 立项 {} 启动审批流: instanceId={}", id, processInstanceId);
        return processInstanceId;
    }

    /**
     * 装配客户/PM/发起人名称（仅在原值为空时填充），Feign 调用失败容错。
     *
     * @param initiation 立项实体（将被原地修改）
     */
    @Override
    public void assembleNames(InitiationDO initiation) {
        if (initiation == null) return;
        if (nameAssembler == null) return;
        if (!StringUtils.hasText(initiation.getCustomerName()) && initiation.getCustomerId() != null) {
            String n = safeCustomerName(initiation.getCustomerId());
            if (n != null) initiation.setCustomerName(n);
        }
        if (!StringUtils.hasText(initiation.getPmName()) && initiation.getPmId() != null) {
            String n = safeEmployeeName(initiation.getPmId());
            if (n != null) initiation.setPmName(n);
        }
        if (!StringUtils.hasText(initiation.getSponsorName()) && initiation.getSponsorId() != null) {
            String n = safeEmployeeName(initiation.getSponsorId());
            if (n != null) initiation.setSponsorName(n);
        }
    }

    /**
     * 生成预算快照（用于报表/导出），包含立项核心字段与预算/估算金额。
     *
     * @param id 立项 ID
     * @return 快照 Map（按插入顺序保留）
     * @throws SysException 立项不存在时抛出
     */
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> budgetSnapshot(String id) {
        InitiationDO o = getById(id);
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("initiationId", o.getId());
        snap.put("projectCode", o.getProjectCode());
        snap.put("projectName", o.getProjectName());
        snap.put("budgetAmount", o.getBudgetAmount());
        snap.put("estimatedAmount", o.getEstimatedAmount());
        snap.put("stage", o.getStage());
        return snap;
    }

    // ============= 流程状态联动（供 workflow 模块 Feign 调用） =============

    /**
     * 标记立项为审批中（APPROVING），保留当前门径不变。
     *
     * @param id 立项 ID
     * @throws SysException 立项不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markProcessing(String id) {
        InitiationDO o = initiationMapper.selectById(id);
        if (o == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "error.project.msg_f7fde8f5");
        }
        initiationMapper.updateStage(id, InitiationStage.APPROVING.getCode(), o.getCurrentGate());
        log.info("[Initiation] 标记审批中: id={} prevStage={}", id, o.getStage());
    }

    /**
     * 标记立项为已批准（APPROVED），并设置门径为 CD1。
     *
     * @param id 立项 ID
     * @throws SysException 立项不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markApproved(String id) {
        InitiationDO o = initiationMapper.selectById(id);
        if (o == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "error.project.msg_f7fde8f5");
        }
        initiationMapper.updateStage(id, InitiationStage.APPROVED.getCode(), GateCode.CD1.name());
        log.info("[Initiation] 标记已批准: id={} prevStage={}", id, o.getStage());
    }

    /**
     * 标记立项为已驳回（REJECTED），保留当前门径不变。
     *
     * @param id     立项 ID
     * @param reason 驳回原因（可空，仅用于日志）
     * @throws SysException 立项不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRejected(String id, String reason) {
        InitiationDO o = initiationMapper.selectById(id);
        if (o == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "error.project.msg_f7fde8f5");
        }
        initiationMapper.updateStage(id, InitiationStage.REJECTED.getCode(), o.getCurrentGate());
        log.info("[Initiation] 标记已驳回: id={} prevStage={} reason={}", id, o.getStage(), reason);
    }

    /**
     * 容错解析客户名称，Feign 调用失败时返回 null。
     *
     * @param id 客户 ID
     * @return 客户名称，调用失败返回 null
     */
    private String safeCustomerName(String id) {
        try { return nameAssembler.resolveCustomer(id); }
        catch (Exception e) { log.warn("[Initiation] 容错解析客户名称失败: id={}", id, e); return null; }
    }

    /**
     * 容错解析员工名称，Feign 调用失败时返回 null。
     *
     * @param id 员工 ID
     * @return 员工名称，调用失败返回 null
     */
    private String safeEmployeeName(String id) {
        try { return nameAssembler.resolveEmployee(id); }
        catch (Exception e) { log.warn("[Initiation] 容错解析员工名称失败: id={}", id, e); return null; }
    }

    // ============= 校验 =============

    /**
     * 校验立项创建参数：编号/名称/客户/类型必填，结束日期不早于开始日期。
     *
     * @param dto 立项创建参数
     * @throws SysException 参数非法时抛出
     */
    private void validate(InitiationCreateDTO dto) {
        if (dto == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.project.msg_d9712a58");
        }
        if (!StringUtils.hasText(dto.getProjectCode())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.project.msg_5e628290");
        }
        if (!StringUtils.hasText(dto.getProjectName())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.project.msg_68b28145");
        }
        if (dto.getCustomerId() == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.project.msg_6de1fd36");
        }
        if (!StringUtils.hasText(dto.getProjectType())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.project.msg_40dfe929");
        }
        if (dto.getPlannedStartDate() != null && dto.getPlannedEndDate() != null
                && dto.getPlannedEndDate().isBefore(dto.getPlannedStartDate())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.project.msg_7e6b1218");
        }
    }

    /**
     * 校验预算明细参数：立项 ID 必填，分类必须在 {@link #BUDGET_CATEGORIES} 范围内。
     *
     * @param dto 预算明细参数
     * @throws SysException 参数非法时抛出
     */
    private void validateBudget(BudgetItemDTO dto) {
        if (dto == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.project.msg_d9712a58");
        }
        if (dto.getInitiationId() == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.project.msg_779da94d");
        }
        if (!BUDGET_CATEGORIES.contains(dto.getCategory().toUpperCase())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.project.msg_b33fbb09", dto.getCategory());
        }
    }
}
