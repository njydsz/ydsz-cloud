package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.dto.FlowDeployProcessDTO;
import com.njydsz.pmis.workflow.service.FlowDefinitionService;
import com.njydsz.pmis.workflow.service.FlowTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GAP-P1: 流程模板服务实现
 *
 * <p>预置 PMIS 领域常见审批流程模板，使用静态列表维护。
 * 每个模板包含编码、名称、分类、描述以及轻量 JSON 节点/跳转定义。
 * {@link #importTemplate} 通过 {@link FlowDefinitionService#deploy} 部署为草稿。
 *
 * <p>预置模板清单：
 * <ul>
 *   <li>purchase_approval — 采购审批</li>
 *   <li>expense_reimbursement — 费用报销</li>
 *   <li>business_trip — 出差申请</li>
 *   <li>seal_request — 用印申请</li>
 *   <li>contract_approval — 合同审批</li>
 *   <li>project_initiation — 项目立项</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTemplateServiceImpl implements FlowTemplateService {

    private final FlowDefinitionService definitionService;

    /** 预置模板列表（类加载时初始化） */
    private static final List<Map<String, Object>> TEMPLATES = new ArrayList<>();

    static {
        initTemplates();
    }

    @Override
    public List<Map<String, Object>> listTemplates(String category) {
        try {
            if (!StringUtils.hasText(category)) {
                return List.copyOf(TEMPLATES);
            }
            return TEMPLATES.stream()
                    .filter(t -> category.equals(t.get("category")))
                    .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            log.error("[FlowTemplate] 列出模板异常: category={} err={}", category, e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public Long importTemplate(String templateCode, Long tenantId) {
        try {
            if (!StringUtils.hasText(templateCode)) {
                throw new BizException(BizErrorCode.BAD_REQUEST, "templateCode 不能为空");
            }

            Map<String, Object> template = findTemplate(templateCode);
            if (template == null) {
                throw new BizException(BizErrorCode.NOT_FOUND, "模板不存在: " + templateCode);
            }

            // 构建 FlowDeployProcessDTO
            FlowDeployProcessDTO dto = new FlowDeployProcessDTO();
            dto.setFlowCode((String) template.get("code"));
            dto.setFlowName((String) template.get("name"));
            dto.setCategory((String) template.get("category"));
            dto.setDescription((String) template.get("description"));
            dto.setVersion("1.0");
            dto.setTenantId(tenantId);

            @SuppressWarnings("unchecked")
            List<FlowDeployProcessDTO.FlowNodeDTO> nodes =
                    (List<FlowDeployProcessDTO.FlowNodeDTO>) template.get("nodes");
            @SuppressWarnings("unchecked")
            List<FlowDeployProcessDTO.FlowSkipDTO> skips =
                    (List<FlowDeployProcessDTO.FlowSkipDTO>) template.get("skips");
            dto.setNodes(nodes);
            dto.setSkips(skips);

            // 部署为草稿
            Long definitionId = definitionService.deploy(dto);
            log.info("[FlowTemplate] 模板导入成功: templateCode={} definitionId={} tenantId={}",
                    templateCode, definitionId, tenantId);
            return definitionId;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[FlowTemplate] 模板导入异常: templateCode={} err={}",
                    templateCode, e.getMessage(), e);
            throw new BizException(BizErrorCode.INTERNAL_ERROR,
                    "模板导入失败: " + templateCode + " — " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> previewTemplate(String templateCode) {
        try {
            if (!StringUtils.hasText(templateCode)) {
                return Map.of();
            }
            Map<String, Object> template = findTemplate(templateCode);
            if (template == null) {
                return Map.of();
            }
            // 返回完整模板信息（含 nodes / skips）
            Map<String, Object> result = new LinkedHashMap<>(template);
            return result;
        } catch (Exception e) {
            log.error("[FlowTemplate] 预览模板异常: templateCode={} err={}",
                    templateCode, e.getMessage(), e);
            return Map.of();
        }
    }

    // ============================== 私有方法 ==============================

    /**
     * 按编码查找模板
     */
    private Map<String, Object> findTemplate(String code) {
        return TEMPLATES.stream()
                .filter(t -> code.equals(t.get("code")))
                .findFirst()
                .orElse(null);
    }

    // ============================== 模板初始化 ==============================

    /**
     * 初始化预置模板
     */
    private static void initTemplates() {
        // 1. 采购审批
        TEMPLATES.add(buildTemplate(
                "purchase_approval", "采购审批", "采购管理",
                "适用于项目采购、零星采购的审批流程，按金额分级审批",
                buildNodes(
                        node("start", "开始", 0, null),
                        node("applicant", "申请人提交", 1, "user:${initiatorId}"),
                        node("dept_manager", "部门经理审批", 1, "role:dept_manager"),
                        node("finance", "财务审批", 1, "role:finance"),
                        node("gm", "总经理审批", 1, "role:general_manager"),
                        node("end", "结束", 6, null)
                ),
                buildSkips(
                        skip("start", "applicant", "PASS", null),
                        skip("applicant", "dept_manager", "PASS", "${amount <= 50000}"),
                        skip("applicant", "finance", "PASS", "${amount > 50000}"),
                        skip("dept_manager", "end", "PASS", null),
                        skip("finance", "gm", "PASS", null),
                        skip("gm", "end", "PASS", null)
                )
        ));

        // 2. 费用报销
        TEMPLATES.add(buildTemplate(
                "expense_reimbursement", "费用报销", "财务管理",
                "适用于员工日常费用报销，按金额分级审批",
                buildNodes(
                        node("start", "开始", 0, null),
                        node("submit", "提交报销", 1, "user:${initiatorId}"),
                        node("dept_leader", "部门负责人审批", 1, "role:dept_leader"),
                        node("finance_check", "财务审核", 1, "role:finance"),
                        node("cashier", "出纳付款", 1, "role:cashier"),
                        node("end", "结束", 6, null)
                ),
                buildSkips(
                        skip("start", "submit", "PASS", null),
                        skip("submit", "dept_leader", "PASS", null),
                        skip("dept_leader", "finance_check", "PASS", null),
                        skip("finance_check", "cashier", "PASS", null),
                        skip("cashier", "end", "PASS", null)
                )
        ));

        // 3. 出差申请
        TEMPLATES.add(buildTemplate(
                "business_trip", "出差申请", "行政管理",
                "适用于员工出差申请审批，包含出差事由、行程、预算",
                buildNodes(
                        node("start", "开始", 0, null),
                        node("apply", "提交申请", 1, "user:${initiatorId}"),
                        node("dept_approval", "部门审批", 1, "role:dept_manager"),
                        node("hr_record", "HR备案", 2, "role:hr"),
                        node("end", "结束", 6, null)
                ),
                buildSkips(
                        skip("start", "apply", "PASS", null),
                        skip("apply", "dept_approval", "PASS", null),
                        skip("dept_approval", "hr_record", "PASS", null),
                        skip("hr_record", "end", "PASS", null)
                )
        ));

        // 4. 用印申请
        TEMPLATES.add(buildTemplate(
                "seal_request", "用印申请", "行政管理",
                "适用于公司印章使用申请审批",
                buildNodes(
                        node("start", "开始", 0, null),
                        node("apply", "提交用印申请", 1, "user:${initiatorId}"),
                        node("dept_manager", "部门经理审批", 1, "role:dept_manager"),
                        node("legal_check", "法务审核", 1, "role:legal"),
                        node("gm_approval", "总经理审批", 1, "role:general_manager"),
                        node("end", "结束", 6, null)
                ),
                buildSkips(
                        skip("start", "apply", "PASS", null),
                        skip("apply", "dept_manager", "PASS", null),
                        skip("dept_manager", "legal_check", "PASS", "${sealType == 'official'}"),
                        skip("dept_manager", "end", "PASS", "${sealType != 'official'}"),
                        skip("legal_check", "gm_approval", "PASS", null),
                        skip("gm_approval", "end", "PASS", null)
                )
        ));

        // 5. 合同审批
        TEMPLATES.add(buildTemplate(
                "contract_approval", "合同审批", "合同管理",
                "适用于各类合同签订前的审批流程",
                buildNodes(
                        node("start", "开始", 0, null),
                        node("draft", "起草合同", 1, "user:${initiatorId}"),
                        node("dept_review", "部门审核", 1, "role:dept_manager"),
                        node("legal_review", "法务审核", 1, "role:legal"),
                        node("finance_review", "财务审核", 1, "role:finance"),
                        node("gm_sign", "总经理签批", 1, "role:general_manager"),
                        node("end", "结束", 6, null)
                ),
                buildSkips(
                        skip("start", "draft", "PASS", null),
                        skip("draft", "dept_review", "PASS", null),
                        skip("dept_review", "legal_review", "PASS", null),
                        skip("legal_review", "finance_review", "PASS", null),
                        skip("finance_review", "gm_sign", "PASS", null),
                        skip("gm_sign", "end", "PASS", null)
                )
        ));

        // 6. 项目立项
        TEMPLATES.add(buildTemplate(
                "project_initiation", "项目立项", "项目管理",
                "适用于新项目立项审批流程",
                buildNodes(
                        node("start", "开始", 0, null),
                        node("initiate", "发起立项", 1, "user:${initiatorId}"),
                        node("pmo_review", "PMO审核", 1, "role:pmo"),
                        node("finance_review", "财务评估", 1, "role:finance"),
                        node("gm_approval", "总经理审批", 1, "role:general_manager"),
                        node("end", "结束", 6, null)
                ),
                buildSkips(
                        skip("start", "initiate", "PASS", null),
                        skip("initiate", "pmo_review", "PASS", null),
                        skip("pmo_review", "finance_review", "PASS", null),
                        skip("finance_review", "gm_approval", "PASS", null),
                        skip("gm_approval", "end", "PASS", null)
                )
        ));

        log.info("[FlowTemplate] 预置模板初始化完成: count={}", TEMPLATES.size());
    }

    // ============================== 模板构建辅助 ==============================

    /**
     * 构建模板 Map
     */
    private static Map<String, Object> buildTemplate(String code, String name, String category,
                                                      String description,
                                                      List<FlowDeployProcessDTO.FlowNodeDTO> nodes,
                                                      List<FlowDeployProcessDTO.FlowSkipDTO> skips) {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("code", code);
        template.put("name", name);
        template.put("category", category);
        template.put("description", description);
        template.put("nodes", nodes);
        template.put("skips", skips);
        return template;
    }

    /**
     * 构建节点列表
     */
    private static List<FlowDeployProcessDTO.FlowNodeDTO> buildNodes(FlowDeployProcessDTO.FlowNodeDTO... nodes) {
        return new ArrayList<>(List.of(nodes));
    }

    /**
     * 构建跳转列表
     */
    private static List<FlowDeployProcessDTO.FlowSkipDTO> buildSkips(FlowDeployProcessDTO.FlowSkipDTO... skips) {
        return new ArrayList<>(List.of(skips));
    }

    /**
     * 创建节点 DTO
     */
    private static FlowDeployProcessDTO.FlowNodeDTO node(String nodeCode, String nodeName,
                                                          int nodeType, String permissionFlag) {
        FlowDeployProcessDTO.FlowNodeDTO n = new FlowDeployProcessDTO.FlowNodeDTO();
        n.setNodeCode(nodeCode);
        n.setNodeName(nodeName);
        n.setNodeType(nodeType);
        n.setPermissionFlag(permissionFlag);
        return n;
    }

    /**
     * 创建跳转 DTO
     */
    private static FlowDeployProcessDTO.FlowSkipDTO skip(String from, String to,
                                                          String skipType, String condition) {
        FlowDeployProcessDTO.FlowSkipDTO s = new FlowDeployProcessDTO.FlowSkipDTO();
        s.setFromNodeCode(from);
        s.setToNodeCode(to);
        s.setSkipType(skipType);
        s.setSkipCondition(condition);
        s.setSkipName(from + " → " + to);
        return s;
    }
}
