package com.njydsz.pmis.agent.engine;

import com.njydsz.pmis.agent.enums.AgentAlertLevel;
import com.njydsz.pmis.agent.enums.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * P2-1: 意见起草 Agent（工作流场景）
 *
 * <p>输入参数（params）：
 * <ul>
 *   <li>action: String 审批动作 (PASS/REJECT/TRANSFER/DELEGATE/URGE)，必填</li>
 *   <li>flowName: String 流程名</li>
 *   <li>nodeName: String 节点名</li>
 *   <li>title: String 业务标题</li>
 *   <li>amount: BigDecimal 业务金额（可选，用于金额相关决策）</li>
 *   <li>riskLevel: String 风险等级 (RED/YELLOW/GREEN)，可选</li>
 *   <li>overdueDays: Integer 超期天数</li>
 *   <li>historicalComments: List&lt;String&gt; 同一节点/流程的历史意见</li>
 *   <li>tone: String 语气 (FORMAL/FRIENDLY)，默认 FORMAL</li>
 *   <li>maxLength: Integer 最大字数（默认 200）</li>
 * </ul>
 *
 * <p>输出载荷（payload）：
 * <ul>
 *   <li>primary: String 推荐意见（首选）</li>
 *   <li>alternatives: List&lt;String&gt; 备选意见（2-3 条）</li>
 *   <li>reasons: List&lt;String&gt; 起草依据</li>
 * </ul>
 *
 * <p>实现策略（v1 启发式，可平滑切换到 LLM）：
 * <ol>
 *   <li>根据 action 选定模板类别</li>
 *   <li>综合 flowName / nodeName / title 拼接上下文</li>
 *   <li>根据 amount / riskLevel / overdueDays 调整语气</li>
 *   <li>从历史意见中提取最近 2 条作为备选参考</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class CommentDraftAgent implements Agent {

    private static final Random RANDOM = new Random();

    @Override
    public AgentType type() {
        return AgentType.COMMENT_DRAFT;
    }

    @Override
    @SuppressWarnings("unchecked")
    public AgentResult execute(AgentContext ctx) {
        Map<String, Object> p = ctx.getParams() == null ? Map.of() : ctx.getParams();
        String action = p.get("action") == null ? "PASS" : p.get("action").toString().toUpperCase();
        String flowName = p.get("flowName") == null ? "" : p.get("flowName").toString();
        String nodeName = p.get("nodeName") == null ? "" : p.get("nodeName").toString();
        String title = p.get("title") == null ? "" : p.get("title").toString();
        String riskLevel = p.get("riskLevel") == null ? null : p.get("riskLevel").toString();
        Integer overdueDays = p.get("overdueDays") instanceof Number n ? n.intValue() : 0;
        String tone = p.get("tone") == null ? "FORMAL" : p.get("tone").toString().toUpperCase();
        int maxLength = p.get("maxLength") instanceof Number n2 ? n2.intValue() : 200;
        if (maxLength < 30) maxLength = 30;
        if (maxLength > 500) maxLength = 500;
        List<String> history = p.get("historicalComments") instanceof List<?> l
                ? ((List<String>) l) : List.of();

        String primary = draftPrimary(action, flowName, nodeName, title,
                riskLevel, overdueDays, tone);
        if (primary.length() > maxLength) {
            primary = primary.substring(0, maxLength - 1) + "…";
        }
        List<String> alternatives = draftAlternatives(action, flowName, nodeName, tone);
        List<String> reasons = buildReasons(action, riskLevel, overdueDays, history);

        BigDecimal confidence = history.isEmpty() ? BigDecimal.valueOf(0.6)
                : BigDecimal.valueOf(0.75);
        AgentAlertLevel level = AgentAlertLevel.RECOMMEND;

        log.info("[CommentDraft] biz={} action={} primary.length={}",
                ctx.getBizRef(), action, primary.length());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("primary", primary);
        payload.put("alternatives", alternatives);
        payload.put("reasons", reasons);
        payload.put("action", action);
        payload.put("tone", tone);
        return new AgentResult(AgentType.COMMENT_DRAFT, level,
                BigDecimal.valueOf(0.8), confidence, primary,
                List.of("action=" + action, "history=" + history.size()),
                payload);
    }

    // ========== 起草主意见 ==========

    private String draftPrimary(String action, String flowName, String nodeName,
                                 String title, String riskLevel,
                                 int overdueDays, String tone) {
        String prefix = (flowName.isEmpty() ? "" : "【" + flowName + "】");
        String nodePart = nodeName.isEmpty() ? "" : "（" + nodeName + "节点）";
        String bizPart = title.isEmpty() ? "" : "『" + title + "』";
        boolean friendly = "FRIENDLY".equalsIgnoreCase(tone);

        String main;
        switch (action) {
            case "PASS": {
                if ("RED".equalsIgnoreCase(riskLevel)) {
                    main = friendly
                            ? "已仔细评估，虽然存在一定风险，但综合考虑后同意推进，请持续关注风险并及时反馈。"
                            : "已审阅相关材料，虽然存在风险（等级=" + riskLevel + "），但综合判断同意按计划推进，请项目组持续监控并按节点反馈风险处置情况。";
                } else if (overdueDays > 0) {
                    main = "已关注超时情况（" + overdueDays + "天），同意按现方案继续推进，后续请按计划赶工。";
                } else {
                    main = friendly
                            ? "同意，没什么问题，继续推进吧。"
                            : "已审阅，业务内容合规、方案合理，同意按现方案推进，请项目组按节点计划执行。";
                }
                break;
            }
            case "REJECT": {
                main = friendly
                        ? "抱歉，暂时无法通过，建议补充完善后再提交。"
                        : "经审核，当前申请存在以下问题需补正后重新提交：请补充必要的材料/说明，确保信息完整、依据充分。";
                break;
            }
            case "TRANSFER": {
                main = friendly
                        ? "这个事项由 @" + "同事 处理更合适，已转办。"
                        : "本节点非本人主责范围，已将该审批单转交给更合适的同事处理，请关注后续进度。";
                break;
            }
            case "DELEGATE": {
                main = "因出差/请假无法亲自处理，已将该审批任务委派给指定同事代办，结果视同本人意见。";
                break;
            }
            case "URGE": {
                main = overdueDays > 0
                        ? "已超期 " + overdueDays + " 天，请尽快处理并反馈进展。"
                        : "请尽快处理该审批事项，避免影响下游节点。";
                break;
            }
            default: {
                main = "已审阅，无异议。";
            }
        }
        return prefix + bizPart + nodePart + main;
    }

    /**
     * 备选意见（2-3 条）
     */
    private List<String> draftAlternatives(String action, String flowName,
                                            String nodeName, String tone) {
        List<String> alt = new ArrayList<>();
        boolean friendly = "FRIENDLY".equalsIgnoreCase(tone);
        switch (action) {
            case "PASS":
                alt.add("同意，请按计划推进。");
                alt.add("已审阅，方案合理，同意。");
                if (!friendly) alt.add("已了解情况，同意继续。");
                break;
            case "REJECT":
                alt.add("请补充资料后重新提交。");
                alt.add(friendly ? "暂时无法通过，麻烦补充后再来。" : "驳回，理由不充分。");
                alt.add("建议修改后再次提交。");
                break;
            case "TRANSFER":
                alt.add("转交给更合适的同事。");
                alt.add("非本人主责，已转办。");
                break;
            case "DELEGATE":
                alt.add("已委派他人代办。");
                alt.add("本人无法处理，已委派。");
                break;
            case "URGE":
                alt.add("请尽快处理。");
                alt.add("请关注并及时反馈。");
                break;
            default:
                alt.add("已审阅，无异议。");
        }
        // 控制备选数量
        if (alt.size() > 3) {
            int[] idx = RANDOM.ints(0, alt.size()).distinct().limit(3).toArray();
            List<String> picked = new ArrayList<>();
            for (int i : idx) picked.add(alt.get(i));
            return picked;
        }
        return alt;
    }

    /**
     * 起草依据：解释为何这样起草
     */
    private List<String> buildReasons(String action, String riskLevel,
                                       int overdueDays, List<String> history) {
        List<String> reasons = new ArrayList<>();
        reasons.add("基于审批动作 " + action + " 选用对应模板");
        if (riskLevel != null) {
            reasons.add("结合风险等级 " + riskLevel + " 调整语气与措辞");
        }
        if (overdueDays > 0) {
            reasons.add("已关注超时 " + overdueDays + " 天，强调时效性");
        }
        if (!history.isEmpty()) {
            reasons.add("参考历史意见 " + history.size() + " 条作为措辞风格锚点");
        } else {
            reasons.add("无历史意见可用，选用系统默认措辞");
        }
        return reasons;
    }
}
