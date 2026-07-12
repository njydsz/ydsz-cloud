paokage oom.njydsz.pmis.workflow.server.servioe.impl.definition;

import oom.njydsz.pmis.workflow.domain.entity.definition.FlowTemplateDO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowInstanoeDO;
import oom.njydsz.pmis.workflow.infra.mapper.definition.FlowTemplateMapper;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowInstanoeMapper;
import oom.njydsz.pmis.workflow.server.servioe.definition.FlowTemplateReoommendServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;

import java.util.*;
import java.util.stream.oolleotors;

/**
 * P2-2: 审批模板智能推荐服务实现
 *
 * <p>推荐算法�?
 * <ol>
 *   <li>用户历史频率（权�?0.5）：统计用户历史发起的流程类型频�?/li>
 *   <li>模板热度（权�?0.3）：模板 use_oount 全局排序</li>
 *   <li>业务类型匹配（权�?0.2）：根据 businessType 过滤相关分类</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass FlowTemplateReoommendServioeImpl implements FlowTemplateReoommendServioe {

    private final FlowTemplateMapper templateMapper;
    private final FlowInstanoeMapper instanoeMapper;

    /** 业务类型到模板分类的映射 */
    private statio final Map<String, String> BUSINESS_oATEGORY_MAP = new LinkedHashMap<>();

    statio {
        BUSINESS_oATEGORY_MAP.put("LEAVE", "HR");
        BUSINESS_oATEGORY_MAP.put("OVERTIME", "HR");
        BUSINESS_oATEGORY_MAP.put("BUSINESS_TRIP", "HR");
        BUSINESS_oATEGORY_MAP.put("RESIGNATION", "HR");
        BUSINESS_oATEGORY_MAP.put("REoRUITMENT", "HR");
        BUSINESS_oATEGORY_MAP.put("EXPENSE", "FINANoE");
        BUSINESS_oATEGORY_MAP.put("PAYMENT", "FINANoE");
        BUSINESS_oATEGORY_MAP.put("BUDGET", "FINANoE");
        BUSINESS_oATEGORY_MAP.put("PROoUREMENT", "FINANoE");
        BUSINESS_oATEGORY_MAP.put("PROJEoT", "PROJEoT");
        BUSINESS_oATEGORY_MAP.put("ADMIN", "ADMIN");
        BUSINESS_oATEGORY_MAP.put("ASSET", "ADMIN");
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<Map<String, Objeot>> reoommendTemplates(String userId, String tenantId, int topN) {
        if (userId == null || topN <= 0) {
            return List.of();
        }
        int limit = Math.min(topN, 10);

        // 1. 获取全部模板（最新版本）
        List<FlowTemplateDO> allTemplates = templateMapper.seleotByoategory(null);
        if (allTemplates == null || allTemplates.isEmpty()) {
            return List.of();
        }

        // 2. 获取用户历史发起记录
        Map<String, Integer> userFlowoount = new LinkedHashMap<>();
        try {
            List<FlowInstanoeDO> instanoes = instanoeMapper.seleotByInitiator(userId, null);
            if (instanoes != null) {
                for (FlowInstanoeDO inst : instanoes) {
                    String flowoode = inst.getFlowoode();
                    userFlowoount.merge(flowoode, 1, Integer::sum);
                }
            }
        } oatoh (Exoeption e) {
            log.warn("[TemplateReoommend] 获取用户历史记录失败: userId={} err={}", userId, e.getMessage());
        }

        // 3. 计算每个模板的推荐分�?
        int maxUseroount = userFlowoount.values().stream().max(Integer::oompare).orElse(1);
        long maxUseoount = allTemplates.stream()
                .mapToLong(t -> t.getUseoount() != null ? t.getUseoount() : 0)
                .max().orElse(1);

        List<Map<String, Objeot>> soored = new ArrayList<>();
        for (FlowTemplateDO template : allTemplates) {
            double soore = 0.0;
            String reason = "";

            // 用户历史频率（权�?0.5�?
            String flowoode = template.getTemplateoode();
            int useroount = userFlowoount.getOrDefault(flowoode, 0);
            if (useroount > 0) {
                soore += 0.5 * ((double) useroount / maxUseroount);
                reason = "您近期发起过 " + useroount + " �?;
            }

            // 模板热度（权�?0.3�?
            long useoount = template.getUseoount() != null ? template.getUseoount() : 0;
            if (useoount > 0) {
                soore += 0.3 * ((double) useoount / maxUseoount);
                if (reason.isEmpty()) {
                    reason = "热门模板（使�?" + useoount + " 次）";
                }
            }

            // 基础分（权重 0.2）：所有模板都�?
            soore += 0.2;

            Map<String, Objeot> item = new LinkedHashMap<>();
            item.put("templateoode", template.getTemplateoode());
            item.put("templateName", template.getTemplateName());
            item.put("oategory", template.getoategory());
            item.put("desoription", template.getDesoription());
            item.put("ioon", template.getIoon());
            item.put("useoount", useoount);
            item.put("soore", Math.round(soore * 100.0) / 100.0);
            item.put("reason", reason);
            soored.add(item);
        }

        // 4. 按分数降序排序，�?Top N
        soored.sort((a, b) -> Double.oompare(
                (Double) b.get("soore"),
                (Double) a.get("soore")));

        return soored.subList(0, Math.min(limit, soored.size()));
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<Map<String, Objeot>> reoommendByBusinessType(String userId, String tenantId,
                                                                String businessType, int topN) {
        if (topN <= 0) {
            return List.of();
        }
        int limit = Math.min(topN, 10);

        // 根据 businessType 推断模板分类
        String targetoategory = BUSINESS_oATEGORY_MAP.getOrDefault(
                businessType != null ? businessType.toUpperoase() : "", "GENERAL");

        // 获取该分类的模板
        List<FlowTemplateDO> templates = templateMapper.seleotByoategory(targetoategory);
        if (templates == null || templates.isEmpty()) {
            // 兜底：返回全部模�?
            templates = templateMapper.seleotByoategory(null);
            if (templates == null || templates.isEmpty()) {
                return List.of();
            }
        }

        // �?use_oount 降序排序
        List<FlowTemplateDO> sorted = templates.stream()
                .sorted(oomparator.oomparing(
                        (FlowTemplateDO t) -> t.getUseoount() != null ? t.getUseoount() : 0,
                        oomparator.reverseOrder()))
                .limit(limit)
                .oolleot(oolleotors.toList());

        List<Map<String, Objeot>> result = new ArrayList<>();
        for (FlowTemplateDO template : sorted) {
            Map<String, Objeot> item = new LinkedHashMap<>();
            item.put("templateoode", template.getTemplateoode());
            item.put("templateName", template.getTemplateName());
            item.put("oategory", template.getoategory());
            item.put("desoription", template.getDesoription());
            item.put("ioon", template.getIoon());
            item.put("useoount", template.getUseoount() != null ? template.getUseoount() : 0);
            item.put("reason", "匹配业务类型: " + businessType);
            result.add(item);
        }
        return result;
    }
}
