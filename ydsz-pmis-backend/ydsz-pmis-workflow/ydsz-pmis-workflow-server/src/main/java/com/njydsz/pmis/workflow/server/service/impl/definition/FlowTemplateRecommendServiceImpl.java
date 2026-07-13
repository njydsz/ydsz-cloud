package com.njydsz.pmis.workflow.server.service.impl.definition;

import com.njydsz.pmis.workflow.domain.entity.FlowTemplateDO;
import com.njydsz.pmis.workflow.domain.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.infra.mapper.definition.FlowTemplateMapper;
import com.njydsz.pmis.workflow.infra.mapper.instance.FlowInstanceMapper;
import com.njydsz.pmis.workflow.server.service.FlowTemplateRecommendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * P2-2: 审批模板智能推荐服务实现
 *
 * <p>推荐算法：
 * <ol>
 *   <li>用户历史频率（权重 0.5）：统计用户历史发起的流程类型频次</li>
 *   <li>模板热度（权重 0.3）：模板 use_count 全局排序</li>
 *   <li>业务类型匹配（权重 0.2）：根据 businessType 过滤相关分类</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTemplateRecommendServiceImpl implements FlowTemplateRecommendService {

    private final FlowTemplateMapper templateMapper;
    private final FlowInstanceMapper instanceMapper;

    /** 业务类型到模板分类的映射 */
    private static final Map<String, String> BUSINESS_CATEGORY_MAP = new LinkedHashMap<>();

    static {
        BUSINESS_CATEGORY_MAP.put("LEAVE", "HR");
        BUSINESS_CATEGORY_MAP.put("OVERTIME", "HR");
        BUSINESS_CATEGORY_MAP.put("BUSINESS_TRIP", "HR");
        BUSINESS_CATEGORY_MAP.put("RESIGNATION", "HR");
        BUSINESS_CATEGORY_MAP.put("RECRUITMENT", "HR");
        BUSINESS_CATEGORY_MAP.put("EXPENSE", "FINANCE");
        BUSINESS_CATEGORY_MAP.put("PAYMENT", "FINANCE");
        BUSINESS_CATEGORY_MAP.put("BUDGET", "FINANCE");
        BUSINESS_CATEGORY_MAP.put("PROCUREMENT", "FINANCE");
        BUSINESS_CATEGORY_MAP.put("PROJECT", "PROJECT");
        BUSINESS_CATEGORY_MAP.put("ADMIN", "ADMIN");
        BUSINESS_CATEGORY_MAP.put("ASSET", "ADMIN");
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> recommendTemplates(String userId, String tenantId, int topN) {
        if (userId == null || topN <= 0) {
            return List.of();
        }
        int limit = Math.min(topN, 10);

        // 1. 获取全部模板（最新版本）
        List<FlowTemplateDO> allTemplates = templateMapper.selectByCategory(null);
        if (allTemplates == null || allTemplates.isEmpty()) {
            return List.of();
        }

        // 2. 获取用户历史发起记录
        Map<String, Integer> userFlowCount = new LinkedHashMap<>();
        try {
            List<FlowInstanceDO> instances = instanceMapper.selectByInitiator(userId, null);
            if (instances != null) {
                for (FlowInstanceDO inst : instances) {
                    String flowCode = inst.getFlowCode();
                    userFlowCount.merge(flowCode, 1, Integer::sum);
                }
            }
        } catch (Exception e) {
            log.warn("[TemplateRecommend] 获取用户历史记录失败: userId={} err={}", userId, e.getMessage());
        }

        // 3. 计算每个模板的推荐分数
        int maxUserCount = userFlowCount.values().stream().max(Integer::compare).orElse(1);
        long maxUseCount = allTemplates.stream()
                .mapToLong(t -> t.getUseCount() != null ? t.getUseCount() : 0)
                .max().orElse(1);

        List<Map<String, Object>> scored = new ArrayList<>();
        for (FlowTemplateDO template : allTemplates) {
            double score = 0.0;
            String reason = "";

            // 用户历史频率（权重 0.5）
            String flowCode = template.getTemplateCode();
            int userCount = userFlowCount.getOrDefault(flowCode, 0);
            if (userCount > 0) {
                score += 0.5 * ((double) userCount / maxUserCount);
                reason = "您近期发起过 " + userCount + " 次";
            }

            // 模板热度（权重 0.3）
            long useCount = template.getUseCount() != null ? template.getUseCount() : 0;
            if (useCount > 0) {
                score += 0.3 * ((double) useCount / maxUseCount);
                if (reason.isEmpty()) {
                    reason = "热门模板（使用 " + useCount + " 次）";
                }
            }

            // 基础分（权重 0.2）：所有模板都有
            score += 0.2;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("templateCode", template.getTemplateCode());
            item.put("templateName", template.getTemplateName());
            item.put("category", template.getCategory());
            item.put("description", template.getDescription());
            item.put("icon", template.getIcon());
            item.put("useCount", useCount);
            item.put("score", Math.round(score * 100.0) / 100.0);
            item.put("reason", reason);
            scored.add(item);
        }

        // 4. 按分数降序排序，取 Top N
        scored.sort((a, b) -> Double.compare(
                (Double) b.get("score"),
                (Double) a.get("score")));

        return scored.subList(0, Math.min(limit, scored.size()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> recommendByBusinessType(String userId, String tenantId,
                                                                String businessType, int topN) {
        if (topN <= 0) {
            return List.of();
        }
        int limit = Math.min(topN, 10);

        // 根据 businessType 推断模板分类
        String targetCategory = BUSINESS_CATEGORY_MAP.getOrDefault(
                businessType != null ? businessType.toUpperCase() : "", "GENERAL");

        // 获取该分类的模板
        List<FlowTemplateDO> templates = templateMapper.selectByCategory(targetCategory);
        if (templates == null || templates.isEmpty()) {
            // 兜底：返回全部模板
            templates = templateMapper.selectByCategory(null);
            if (templates == null || templates.isEmpty()) {
                return List.of();
            }
        }

        // 按 use_count 降序排序
        List<FlowTemplateDO> sorted = templates.stream()
                .sorted(Comparator.comparing(
                        (FlowTemplateDO t) -> t.getUseCount() != null ? t.getUseCount() : 0,
                        Comparator.reverseOrder()))
                .limit(limit)
                .collect(Collectors.toList());

        List<Map<String, Object>> result = new ArrayList<>();
        for (FlowTemplateDO template : sorted) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("templateCode", template.getTemplateCode());
            item.put("templateName", template.getTemplateName());
            item.put("category", template.getCategory());
            item.put("description", template.getDescription());
            item.put("icon", template.getIcon());
            item.put("useCount", template.getUseCount() != null ? template.getUseCount() : 0);
            item.put("reason", "匹配业务类型: " + businessType);
            result.add(item);
        }
        return result;
    }
}
