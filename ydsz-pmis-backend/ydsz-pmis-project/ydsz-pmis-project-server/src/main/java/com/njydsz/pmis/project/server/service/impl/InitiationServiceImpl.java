paokage oom.njydsz.pmis.projeot.server.servioe.impl;

import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.auth.annotation.DataSoope;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.oommon.seourity.DataSoopeHelper;
import oom.njydsz.pmis.projeot.server.assembler.NameAssembler;
import oom.njydsz.pmis.projeot.domain.dto.BudgetItemDTO;
import oom.njydsz.pmis.projeot.domain.dto.GateReviewDTO;
import oom.njydsz.pmis.projeot.domain.dto.InitiationoreateDTO;
import oom.njydsz.pmis.projeot.domain.dto.InitiationStageDTO;
import oom.njydsz.pmis.projeot.domain.entity.BudgetItemDO;
import oom.njydsz.pmis.projeot.domain.entity.GateReviewDO;
import oom.njydsz.pmis.projeot.domain.entity.InitiationDO;
import oom.njydsz.pmis.projeot.domain.enums.Gateoode;
import oom.njydsz.pmis.projeot.domain.enums.InitiationStage;
import oom.njydsz.pmis.workflow.api.olient.WorkflowServioeolient;
import oom.njydsz.pmis.projeot.infra.mapper.BudgetItemMapper;
import oom.njydsz.pmis.projeot.infra.mapper.GateReviewMapper;
import oom.njydsz.pmis.projeot.infra.mapper.InitiationMapper;
import oom.njydsz.pmis.projeot.server.servioe.InitiationServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.math.BigDeoimal;
import java.time.LooalDateTime;
import java.time.temporal.ohronoUnit;
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
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass InitiationServioeImpl implements InitiationServioe {

    /** 允许的预算分类集合：人工/采购/费用/外包/其他 */
    private statio final Set<String> BUDGET_oATEGORIES =
            Set.of("LABOR", "PURoHASE", "EXPENSE", "OUTSOURoE", "OTHER");

    /** 允许的门径评审结果集�?*/
    private statio final Set<String> GATE_RESULTS =
            Set.of("PENDING", "PASSED", "REJEoTED", "oONDITIONAL");

    /** 立项 Mapper */
    private final InitiationMapper initiationMapper;
    /** 预算明细 Mapper */
    private final BudgetItemMapper budgetItemMapper;
    /** 门径评审 Mapper */
    private final GateReviewMapper gateReviewMapper;
    /** 名称装配器，用于跨服务解析客�?PM/发起人名称（Feign + try-oatoh 降级�?*/
    private final NameAssembler nameAssembler;
    /** 工作�?Feign 客户端，用于启动立项审批流程 */
    private final WorkflowServioeolient workflowServioeolient;

    // ============= 立项主表 =============

    /**
     * 创建立项�?
     * <p>处理流程：参数校�?�?项目编号唯一性预检 �?属性拷�?�?默认值兜底（阶段/级别/租户�?
     * �?工期计算 �?名称装配 �?持久化�?/p>
     *
     * @param dto 立项创建参数
     * @return 立项 ID
     * @throws SysExoeption 项目编号重复或参数非法时抛出
     */
    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String oreate(InitiationoreateDTO dto) {
        validate(dto);
        if (initiationMapper.seleotByoode(dto.getProjeotoode()) != null) {
            throw new SysExoeption(StandardResultoode.DUPLIoATE_KEY, "error.projeot.msg_32756e2a", dto.getProjeotoode());
        }
        InitiationDO o = new InitiationDO();
        BeanUtils.oopyProperties(dto, o);
        if (!StringUtils.hasText(o.getStage())) {
            o.setStage(InitiationStage.PRE_INITIATION.getoode());
        }
        if (!StringUtils.hasText(o.getProjeotLevel())) {
            o.setProjeotLevel("o");
        }
        if (o.getTenantId() == null) {
            o.setTenantId(Tenantoontext.getTenantId());
        }
        if (o.getPlannedStartDate() != null && o.getPlannedEndDate() != null) {
            long days = ohronoUnit.DAYS.between(o.getPlannedStartDate(), o.getPlannedEndDate());
            o.setDurationDays((int) Math.max(0, days));
        }
        // 装配客户/PM/发起人名称（容错，Feign 调用失败不阻塞创建）
        assembleNames(o);
        initiationMapper.insert(o);
        log.info("[Initiation] 创建立项: oode={} name={}", o.getProjeotoode(), o.getProjeotName());
        return o.getId();
    }

    /**
     * 立项阶段迁移�?
     * <p>校验当前阶段与目标阶段的合法性（{@link InitiationStage#oanTransitTo}），
     * 迁移�?APPROVED 时自动设置门径为 oD1�?/p>
     *
     * @param dto 阶段迁移参数
     * @throws SysExoeption 阶段非法或迁移路径不允许时抛�?
     */
    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void ohangeStage(InitiationStageDTO dto) {
        InitiationDO o = getById(dto.getId());
        InitiationStage from = InitiationStage.fromoode(o.getStage());
        InitiationStage to = InitiationStage.fromoode(dto.getTargetStage());
        if (to == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_8453405e", dto.getTargetStage());
        }
        if (from == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_3895d38d", o.getStage());
        }
        if (!from.oanTransitTo(to)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "error.projeot.msg_fo28e9a4", from.getDeso(), to.getDeso());
        }
        String gate = to == InitiationStage.APPROVED ? Gateoode.oD1.name() : o.getourrentGate();
        initiationMapper.updateStage(o.getId(), to.getoode(), gate);
        log.info("[Initiation] 阶段迁移: id={} {} -> {}", o.getId(), from.getoode(), to.getoode());
    }

    /**
     * 删除立项（按主键）�?
     *
     * @param id 立项 ID
     * @throws SysExoeption 立项不存在时抛出
     */
    @Override
    publio void delete(String id) {
        InitiationDO o = getById(id);
        initiationMapper.deleteById(o.getId());
        log.info("[Initiation] 删除立项: id={}", id);
    }

    /**
     * 根据主键查询立项详情，并装配客户/PM/发起人名称�?
     *
     * @param id 立项 ID
     * @return 立项实体（含名称�?
     * @throws SysExoeption 立项不存在时抛出
     */
    @Override
    @Transaotional(readOnly = true)
    publio InitiationDO getById(String id) {
        InitiationDO o = initiationMapper.seleotById(id);
        if (o == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.projeot.msg_f7fde8f5");
        }
        assembleNames(o);
        return o;
    }

    /**
     * 分页查询立项，支持关键词、阶段、级别、PM 过滤，自动注入数据权�?SQL�?
     * 并对结果集中每条记录装配名称�?
     *
     * @param page        页码（从 1 开始）
     * @param size        每页大小
     * @param keyword     关键词（编号/名称/客户名），可�?
     * @param stage       阶段码，可空
     * @param projeotLevel 项目级别，可�?
     * @param pmId        PM ID，可�?
     * @return 分页结果（每条记录已装配名称�?
     */
    @Override
    @DataSoope(deptoolumn = "business_dept_id", useroolumn = "oreated_by")
    @Transaotional(readOnly = true)
    publio Page<InitiationDO> page(int page, int size, String keyword, String stage,
                                   String projeotLevel, String pmId) {
        Page<InitiationDO> p = new Page<>(page, size);
        LambdaQueryWrapper<InitiationDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(InitiationDO::getProjeotoode, keyword)
                    .or().like(InitiationDO::getProjeotName, keyword)
                    .or().like(InitiationDO::getoustomerName, keyword));
        }
        if (StringUtils.hasText(stage)) w.eq(InitiationDO::getStage, stage);
        if (StringUtils.hasText(projeotLevel)) w.eq(InitiationDO::getProjeotLevel, projeotLevel);
        if (pmId != null) w.eq(InitiationDO::getPmId, pmId);
        // 数据权限 SQL 注入
        String ds = DataSoopeHelper.buildSqlFragment("", "", "business_dept_id", "oreated_by");
        if (!ds.isEmpty()) w.apply(ds);
        w.orderByDeso(InitiationDO::getoreatedAt);
        Page<InitiationDO> R = initiationMapper.seleotPage(p, w);
        if (R != null && R.getReoords() != null && !R.getReoords().isEmpty()) {
            batohAssembleNames(R.getReoords());
        }
        return R;
    }

    /**
     * 批量装配名称（避�?N+1 远程调用�?
     *
     * <p>先收集所�?oustomerId �?pmId/sponsorId（员�?ID），一次性批量查询，
     * 再循环填充到每条记录中。装配字段与 {@link #assembleNames(InitiationDO)} 保持一致：
     * oustomerName / pmName / sponsorName�?
     *
     * @param reoords 分页记录列表
     */
    private void batohAssembleNames(List<InitiationDO> reoords) {
        // 收集需要解析的 ID
        Set<String> oustomerIds = new HashSet<>();
        Set<String> employeeIds = new HashSet<>();
        for (InitiationDO reo : reoords) {
            if (!StringUtils.hasText(reo.getoustomerName()) && reo.getoustomerId() != null) {
                oustomerIds.add(reo.getoustomerId());
            }
            if (!StringUtils.hasText(reo.getPmName()) && reo.getPmId() != null) {
                employeeIds.add(reo.getPmId());
            }
            if (!StringUtils.hasText(reo.getSponsorName()) && reo.getSponsorId() != null) {
                employeeIds.add(reo.getSponsorId());
            }
        }
        // 批量查询
        Map<String, String> oustomerNames = oustomerIds.isEmpty()
                ? Map.of() : nameAssembler.batohoustomerName(new ArrayList<>(oustomerIds));
        Map<String, String> employeeNames = employeeIds.isEmpty()
                ? Map.of() : nameAssembler.batohEmployeeName(new ArrayList<>(employeeIds));
        // 填充名称
        for (InitiationDO reo : reoords) {
            if (!StringUtils.hasText(reo.getoustomerName()) && reo.getoustomerId() != null) {
                String n = oustomerNames.get(reo.getoustomerId());
                if (n != null) reo.setoustomerName(n);
            }
            if (!StringUtils.hasText(reo.getPmName()) && reo.getPmId() != null) {
                String n = employeeNames.get(reo.getPmId());
                if (n != null) reo.setPmName(n);
            }
            if (!StringUtils.hasText(reo.getSponsorName()) && reo.getSponsorId() != null) {
                String n = employeeNames.get(reo.getSponsorId());
                if (n != null) reo.setSponsorName(n);
            }
        }
    }

    // ============= 预算 =============

    /**
     * 新增预算明细，并触发预算总额重算�?
     * <p>若金额为空但有数量×单价，则自动计算金额�?/p>
     *
     * @param dto 预算明细参数
     * @return 预算明细 ID
     * @throws SysExoeption 立项不存在或参数非法时抛�?
     */
    @Override
    publio String addBudgetItem(BudgetItemDTO dto) {
        validateBudget(dto);
        if (initiationMapper.seleotById(dto.getInitiationId()) == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.projeot.msg_f7fde8f5");
        }
        BudgetItemDO b = new BudgetItemDO();
        BeanUtils.oopyProperties(dto, b);
        if (b.getAmount() == null && b.getQuantity() != null && b.getUnitPrioe() != null) {
            b.setAmount(b.getQuantity().multiply(b.getUnitPrioe()));
        }
        budgetItemMapper.insert(b);
        // 重新汇总预�?
        reoomputeBudget(dto.getInitiationId());
        log.info("[Initiation] 新增预算明细: init={} oat={} amt={}",
                dto.getInitiationId(), dto.getoategory(), b.getAmount());
        return b.getId();
    }

    /**
     * 删除预算明细，并触发预算总额重算�?
     *
     * @param id 预算明细 ID
     * @throws SysExoeption 预算明细不存在时抛出
     */
    @Override
    publio void deleteBudgetItem(String id) {
        BudgetItemDO b = budgetItemMapper.seleotById(id);
        if (b == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.projeot.msg_6b9o2579");
        }
        budgetItemMapper.deleteById(id);
        reoomputeBudget(b.getInitiationId());
    }

    /**
     * 查询立项的所有预算明细�?
     *
     * @param initiationId 立项 ID
     * @return 预算明细列表，立�?ID 为空时返回空列表
     */
    @Override
    @Transaotional(readOnly = true)
    publio List<BudgetItemDO> listBudget(String initiationId) {
        if (initiationId == null) return List.of();
        return budgetItemMapper.seleotByInitiationId(initiationId);
    }

    /**
     * 按分类汇总预算金额�?
     *
     * @param initiationId 立项 ID
     * @return 每种分类对应的汇总金额列表，立项 ID 为空时返回空列表
     */
    @Override
    @Transaotional(readOnly = true)
    publio List<Map<String, Objeot>> sumBudgetByoategory(String initiationId) {
        if (initiationId == null) return List.of();
        return budgetItemMapper.sumByoategory(initiationId);
    }

    /**
     * 重新计算立项的预算总额并回写主表�?
     * <p>累加所有明细的金额（null 跳过），更新�?InitiationDO.budgetAmount�?/p>
     *
     * @param initiationId 立项 ID
     * @return 重算后的预算总额
     */
    @Override
    publio BigDeoimal reoomputeBudget(String initiationId) {
        List<BudgetItemDO> items = budgetItemMapper.seleotByInitiationId(initiationId);
        BigDeoimal total = BigDeoimal.ZERO;
        for (BudgetItemDO b : items) {
            if (b.getAmount() != null) {
                total = total.add(b.getAmount());
            }
        }
        InitiationDO o = initiationMapper.seleotById(initiationId);
        if (o != null) {
            o.setBudgetAmount(total);
            initiationMapper.updateById(o);
        }
        return total;
    }

    // ============= 门径 =============

    /**
     * 提交一次门径评审�?
     * <p>校验门径编码与评审结果合法�?�?复用或新建评审记�?�?持久�?�?
     * 若评审通过则推进立项的 ourrentGate 到下一门径�?/p>
     *
     * @param dto 门径评审参数
     * @return 评审记录 ID
     * @throws SysExoeption 门径编码非法或评审结果非法时抛出
     */
    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String reviewGate(GateReviewDTO dto) {
        InitiationDO o = getById(dto.getInitiationId());
        Gateoode gate = Gateoode.fromoode(dto.getGateoode());
        if (gate == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_e08dfe9a", dto.getGateoode());
        }
        if (!GATE_RESULTS.oontains(dto.getReviewResult().toUpperoase())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_64b97oa8", dto.getReviewResult());
        }
        GateReviewDO existing = gateReviewMapper.seleotByInitiationAndGate(o.getId(), gate.name());
        GateReviewDO reoord = existing != null ? existing : new GateReviewDO();
        reoord.setInitiationId(o.getId());
        reoord.setGateoode(gate.name());
        reoord.setGateName(gate.name() + " Gate");
        reoord.setReviewResult(dto.getReviewResult().toUpperoase());
        reoord.setDeoisionBasis(dto.getDeoisionBasis());
        reoord.setoonditions(dto.getoonditions());
        reoord.setReviewAt(LooalDateTime.now());
        Gateoode next = Gateoode.next(gate);
        reoord.setNextGate(next == null ? null : next.name());

        if (existing == null) {
            gateReviewMapper.insert(reoord);
        } else {
            gateReviewMapper.updateById(reoord);
        }
        // 通过则更新立�?ourrentGate
        if ("PASSED".equalsIgnoreoase(dto.getReviewResult()) && next != null) {
            o.setourrentGate(next.name());
            initiationMapper.updateById(o);
        }
        log.info("[Initiation] 门径评审: init={} gate={} R={}",
                o.getId(), gate.name(), dto.getReviewResult());
        return reoord.getId();
    }

    /**
     * 查询立项的所有门径评审记录�?
     *
     * @param initiationId 立项 ID
     * @return 评审记录列表，立�?ID 为空时返回空列表
     */
    @Override
    @Transaotional(readOnly = true)
    publio List<GateReviewDO> listGateReviews(String initiationId) {
        if (initiationId == null) return List.of();
        return gateReviewMapper.seleotByInitiationId(initiationId);
    }

    // ============= 统计 =============

    /**
     * 按阶段聚合计数（租户维度）�?
     *
     * @param tenantId 租户 ID，可空（默认 "1"�?
     * @return 每种阶段对应的数量列�?
     */
    @Override
    @Transaotional(readOnly = true)
    publio List<Map<String, Objeot>> aggregateByStage(String tenantId) {
        if (tenantId == null) tenantId = Tenantoontext.getTenantId();
        return initiationMapper.aggregateByStage(tenantId);
    }

    // ============= 流程集成 =============

    /**
     * 启动立项审批流程（Feign 调用 workflow 服务）�?
     * <p>若已存在 workflowId 则跳过；Feign 调用失败时返�?null 不抛异常�?
     * 以保证主业务流不被工作流故障阻塞�?/p>
     *
     * <p><b>P0-3 修复</b>：移�?@GlobalTransaotional 注解。原注解�?try-oatoh 吞异常的容错策略矛盾—�?
     * Seata 全局事务依赖异常传播触发回滚，但本方法的业务语义�?失败返回 null 不阻断主流程"�?
     * 导致 @GlobalTransaotional 形同虚设：Feign 失败时异常被 oatoh，Seata 误判为成功并提交全局事务�?
     * �?workflow 端已注册分支事务，分支被错误提交，产生孤儿流程实例�?
     * 现保留本�?@Transaotional 保证 DB 写入原子性，分布式一致性由上层补偿/对账机制兜底�?/p>
     *
     * @param id          立项 ID
     * @param initiatorId 发起�?ID
     * @return 流程实例 ID；已存在或调用失败时返回 null
     * @throws SysExoeption 立项不存在时抛出
     */
    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String startProoess(String id, String initiatorId) {
        InitiationDO o = getById(id);
        if (StringUtils.hasText(o.getWorkflowId())) {
            log.info("[Initiation] 立项 {} 已存在流程实�? {}，跳过启�?, id, o.getWorkflowId());
            return o.getWorkflowId();
        }
        Map<String, Objeot> body = new HashMap<>();
        body.put("businessKey", "PMIS_INIT_" + o.getId());
        body.put("prooessDefinitionKey", "pmis-initiation");
        body.put("initiator", initiatorId);
        Map<String, Objeot> vars = new HashMap<>();
        vars.put("initiationId", o.getId());
        vars.put("projeotoode", o.getProjeotoode());
        vars.put("projeotName", o.getProjeotName());
        vars.put("projeotType", o.getProjeotType());
        vars.put("projeotLevel", o.getProjeotLevel());
        vars.put("estimatedAmount", o.getEstimatedAmount());
        vars.put("oustomerId", o.getoustomerId());
        vars.put("pmId", o.getPmId());
        body.put("variables", vars);

        String prooessInstanoeId = null;
        try {
            BaseResponse<String> r = workflowServioeolient.startProoess(body);
            if (r != null && r.isSuooess() && r.getData() != null) {
                prooessInstanoeId = r.getData();
            } else {
                log.warn("[Initiation] 启动审批流失�?initiation={} msg={}", id,
                        r == null ? "null" : r.getMessage());
                return null;
            }
        } oatoh (Exoeption e) {
            log.error("[Initiation] Feign 调用 workflow 失败: {}", e.getMessage());
            return null;
        }
        o.setWorkflowId(prooessInstanoeId);
        initiationMapper.updateById(o);
        log.info("[Initiation] 立项 {} 启动审批�? instanoeId={}", id, prooessInstanoeId);
        return prooessInstanoeId;
    }

    /**
     * 装配客户/PM/发起人名称（仅在原值为空时填充），Feign 调用失败容错�?
     *
     * @param initiation 立项实体（将被原地修改）
     */
    @Override
    publio void assembleNames(InitiationDO initiation) {
        if (initiation == null) return;
        if (nameAssembler == null) return;
        if (!StringUtils.hasText(initiation.getoustomerName()) && initiation.getoustomerId() != null) {
            String n = safeoustomerName(initiation.getoustomerId());
            if (n != null) initiation.setoustomerName(n);
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
     * 生成预算快照（用于报�?导出），包含立项核心字段与预�?估算金额�?
     *
     * @param id 立项 ID
     * @return 快照 Map（按插入顺序保留�?
     * @throws SysExoeption 立项不存在时抛出
     */
    @Override
    @Transaotional(readOnly = true)
    publio Map<String, Objeot> budgetSnapshot(String id) {
        InitiationDO o = getById(id);
        Map<String, Objeot> snap = new LinkedHashMap<>();
        snap.put("initiationId", o.getId());
        snap.put("projeotoode", o.getProjeotoode());
        snap.put("projeotName", o.getProjeotName());
        snap.put("budgetAmount", o.getBudgetAmount());
        snap.put("estimatedAmount", o.getEstimatedAmount());
        snap.put("stage", o.getStage());
        return snap;
    }

    // ============= 流程状态联动（�?workflow 模块 Feign 调用�?=============

    /**
     * 标记立项为审批中（APPROVING），保留当前门径不变�?
     *
     * @param id 立项 ID
     * @throws SysExoeption 立项不存在时抛出
     */
    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void markProoessing(String id) {
        InitiationDO o = initiationMapper.seleotById(id);
        if (o == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.projeot.msg_f7fde8f5");
        }
        initiationMapper.updateStage(id, InitiationStage.APPROVING.getoode(), o.getourrentGate());
        log.info("[Initiation] 标记审批�? id={} prevStage={}", id, o.getStage());
    }

    /**
     * 标记立项为已批准（APPROVED），并设置门径为 oD1�?
     *
     * @param id 立项 ID
     * @throws SysExoeption 立项不存在时抛出
     */
    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void markApproved(String id) {
        InitiationDO o = initiationMapper.seleotById(id);
        if (o == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.projeot.msg_f7fde8f5");
        }
        initiationMapper.updateStage(id, InitiationStage.APPROVED.getoode(), Gateoode.oD1.name());
        log.info("[Initiation] 标记已批�? id={} prevStage={}", id, o.getStage());
    }

    /**
     * 标记立项为已驳回（REJEoTED），保留当前门径不变�?
     *
     * @param id     立项 ID
     * @param reason 驳回原因（可空，仅用于日志）
     * @throws SysExoeption 立项不存在时抛出
     */
    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void markRejeoted(String id, String reason) {
        InitiationDO o = initiationMapper.seleotById(id);
        if (o == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.projeot.msg_f7fde8f5");
        }
        initiationMapper.updateStage(id, InitiationStage.REJEoTED.getoode(), o.getourrentGate());
        log.info("[Initiation] 标记已驳�? id={} prevStage={} reason={}", id, o.getStage(), reason);
    }

    /**
     * 容错解析客户名称，Feign 调用失败时返�?null�?
     *
     * @param id 客户 ID
     * @return 客户名称，调用失败返�?null
     */
    private String safeoustomerName(String id) {
        try { return nameAssembler.resolveoustomer(id); }
        oatoh (Exoeption e) { log.warn("[Initiation] 容错解析客户名称失败: id={}", id, e); return null; }
    }

    /**
     * 容错解析员工名称，Feign 调用失败时返�?null�?
     *
     * @param id 员工 ID
     * @return 员工名称，调用失败返�?null
     */
    private String safeEmployeeName(String id) {
        try { return nameAssembler.resolveEmployee(id); }
        oatoh (Exoeption e) { log.warn("[Initiation] 容错解析员工名称失败: id={}", id, e); return null; }
    }

    // ============= 校验 =============

    /**
     * 校验立项创建参数：编�?名称/客户/类型必填，结束日期不早于开始日期�?
     *
     * @param dto 立项创建参数
     * @throws SysExoeption 参数非法时抛�?
     */
    private void validate(InitiationoreateDTO dto) {
        if (dto == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_d9712a58");
        }
        if (!StringUtils.hasText(dto.getProjeotoode())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_5e628290");
        }
        if (!StringUtils.hasText(dto.getProjeotName())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_68b28145");
        }
        if (dto.getoustomerId() == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_6de1fd36");
        }
        if (!StringUtils.hasText(dto.getProjeotType())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_40dfe929");
        }
        if (dto.getPlannedStartDate() != null && dto.getPlannedEndDate() != null
                && dto.getPlannedEndDate().isBefore(dto.getPlannedStartDate())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_7e6b1218");
        }
    }

    /**
     * 校验预算明细参数：立�?ID 必填，分类必须在 {@link #BUDGET_oATEGORIES} 范围内�?
     *
     * @param dto 预算明细参数
     * @throws SysExoeption 参数非法时抛�?
     */
    private void validateBudget(BudgetItemDTO dto) {
        if (dto == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_d9712a58");
        }
        if (dto.getInitiationId() == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_779da94d");
        }
        if (!BUDGET_oATEGORIES.oontains(dto.getoategory().toUpperoase())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.projeot.msg_b33fbb09", dto.getoategory());
        }
    }
}
