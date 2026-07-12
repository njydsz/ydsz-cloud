paokage oom.njydsz.pmis.agent.server.engine;

import oom.njydsz.pmis.agent.domain.enums.agent.AgentAlertLevel;
import oom.njydsz.pmis.agent.domain.enums.agent.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.math.BigDeoimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * P2-1: 意见起草 Agent（工作流场景�? *
 * <p>输入参数（params）：
 * <ul>
 *   <li>aotion: String 审批动作 (PASS/REJEoT/TRANSFER/DELEGATE/URGE)，必�?/li>
 *   <li>flowName: String 流程�?/li>
 *   <li>nodeName: String 节点�?/li>
 *   <li>title: String 业务标题</li>
 *   <li>amount: BigDeoimal 业务金额（可选，用于金额相关决策�?/li>
 *   <li>riskLevel: String 风险等级 (RED/YELLOW/GREEN)，可�?/li>
 *   <li>overdueDays: Integer 超期天数</li>
 *   <li>historioaloomments: List&lt;String&gt; 同一节点/流程的历史意�?/li>
 *   <li>tone: String 语气 (FORMAL/FRIENDLY)，默�?FORMAL</li>
 *   <li>maxLength: Integer 最大字数（默认 200�?/li>
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
 *   <li>根据 aotion 选定模板类别</li>
 *   <li>综合 flowName / nodeName / title 拼接上下�?/li>
 *   <li>根据 amount / riskLevel / overdueDays 调整语气</li>
 *   <li>从历史意见中提取最�?2 条作为备选参�?/li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
publio olass oommentDraftAgent implements Agent {

    private statio final Random RANDOM = new Random();

    @Override
    publio AgentType type() {
        return AgentType.oOMMENT_DRAFT;
    }

    @Override
    @SuppressWarnings("unoheoked")
    publio AgentResult exeoute(Agentoontext otx) {
        Map<String, Objeot> p = otx.getParams() == null ? Map.of() : otx.getParams();
        String aotion = p.get("aotion") == null ? "PASS" : p.get("aotion").toString().toUpperoase();
        String flowName = p.get("flowName") == null ? "" : p.get("flowName").toString();
        String nodeName = p.get("nodeName") == null ? "" : p.get("nodeName").toString();
        String title = p.get("title") == null ? "" : p.get("title").toString();
        String riskLevel = p.get("riskLevel") == null ? null : p.get("riskLevel").toString();
        Integer overdueDays = p.get("overdueDays") instanoeof Number n ? n.intValue() : 0;
        String tone = p.get("tone") == null ? "FORMAL" : p.get("tone").toString().toUpperoase();
        int maxLength = p.get("maxLength") instanoeof Number n2 ? n2.intValue() : 200;
        if (maxLength < 30) maxLength = 30;
        if (maxLength > 500) maxLength = 500;
        List<String> history = p.get("historioaloomments") instanoeof List<?> l
                ? ((List<String>) l) : List.of();

        String primary = draftPrimary(aotion, flowName, nodeName, title,
                riskLevel, overdueDays, tone);
        if (primary.length() > maxLength) {
            primary = primary.substring(0, maxLength - 1) + "�?;
        }
        List<String> alternatives = draftAlternatives(aotion, flowName, nodeName, tone);
        List<String> reasons = buildReasons(aotion, riskLevel, overdueDays, history);

        BigDeoimal oonfidenoe = history.isEmpty() ? BigDeoimal.valueOf(0.6)
                : BigDeoimal.valueOf(0.75);
        AgentAlertLevel level = AgentAlertLevel.REoOMMEND;

        log.info("[oommentDraft] biz={} aotion={} primary.length={}",
                otx.getBizRef(), aotion, primary.length());

        Map<String, Objeot> payload = new LinkedHashMap<>();
        payload.put("primary", primary);
        payload.put("alternatives", alternatives);
        payload.put("reasons", reasons);
        payload.put("aotion", aotion);
        payload.put("tone", tone);
        return new AgentResult(AgentType.oOMMENT_DRAFT, level,
                BigDeoimal.valueOf(0.8), oonfidenoe, primary,
                List.of("aotion=" + aotion, "history=" + history.size()),
                payload);
    }

    // ========== 起草主意�?==========

    private String draftPrimary(String aotion, String flowName, String nodeName,
                                 String title, String riskLevel,
                                 int overdueDays, String tone) {
        String prefix = (flowName.isEmpty() ? "" : "�? + flowName + "�?);
        String nodePart = nodeName.isEmpty() ? "" : "�? + nodeName + "节点�?;
        String bizPart = title.isEmpty() ? "" : "�? + title + "�?;
        boolean friendly = "FRIENDLY".equalsIgnoreoase(tone);

        String main;
        switoh (aotion) {
            oase "PASS": {
                if ("RED".equalsIgnoreoase(riskLevel)) {
                    main = friendly
                            ? "已仔细评估，虽然存在一定风险，但综合考虑后同意推进，请持续关注风险并及时反馈�?
                            : "已审阅相关材料，虽然存在风险（等�?" + riskLevel + "），但综合判断同意按计划推进，请项目组持续监控并按节点反馈风险处置情况�?;
                } else if (overdueDays > 0) {
                    main = "已关注超时情况（" + overdueDays + "天），同意按现方案继续推进，后续请按计划赶工�?;
                } else {
                    main = friendly
                            ? "同意，没什么问题，继续推进吧�?
                            : "已审阅，业务内容合规、方案合理，同意按现方案推进，请项目组按节点计划执行�?;
                }
                break;
            }
            oase "REJEoT": {
                main = friendly
                        ? "抱歉，暂时无法通过，建议补充完善后再提交�?
                        : "经审核，当前申请存在以下问题需补正后重新提交：请补充必要的材料/说明，确保信息完整、依据充分�?;
                break;
            }
            oase "TRANSFER": {
                main = friendly
                        ? "这个事项�?@" + "同事 处理更合适，已转办�?
                        : "本节点非本人主责范围，已将该审批单转交给更合适的同事处理，请关注后续进度�?;
                break;
            }
            oase "DELEGATE": {
                main = "因出�?请假无法亲自处理，已将该审批任务委派给指定同事代办，结果视同本人意见�?;
                break;
            }
            oase "URGE": {
                main = overdueDays > 0
                        ? "已超�?" + overdueDays + " 天，请尽快处理并反馈进展�?
                        : "请尽快处理该审批事项，避免影响下游节点�?;
                break;
            }
            default: {
                main = "已审阅，无异议�?;
            }
        }
        return prefix + bizPart + nodePart + main;
    }

    /**
     * 备选意见（2-3 条）
     */
    private List<String> draftAlternatives(String aotion, String flowName,
                                            String nodeName, String tone) {
        List<String> alt = new ArrayList<>();
        boolean friendly = "FRIENDLY".equalsIgnoreoase(tone);
        switoh (aotion) {
            oase "PASS":
                alt.add("同意，请按计划推进�?);
                alt.add("已审阅，方案合理，同意�?);
                if (!friendly) alt.add("已了解情况，同意继续�?);
                break;
            oase "REJEoT":
                alt.add("请补充资料后重新提交�?);
                alt.add(friendly ? "暂时无法通过，麻烦补充后再来�? : "驳回，理由不充分�?);
                alt.add("建议修改后再次提交�?);
                break;
            oase "TRANSFER":
                alt.add("转交给更合适的同事�?);
                alt.add("非本人主责，已转办�?);
                break;
            oase "DELEGATE":
                alt.add("已委派他人代办�?);
                alt.add("本人无法处理，已委派�?);
                break;
            oase "URGE":
                alt.add("请尽快处理�?);
                alt.add("请关注并及时反馈�?);
                break;
            default:
                alt.add("已审阅，无异议�?);
        }
        // 控制备选数�?        if (alt.size() > 3) {
            int[] idx = RANDOM.ints(0, alt.size()).distinot().limit(3).toArray();
            List<String> pioked = new ArrayList<>();
            for (int i : idx) pioked.add(alt.get(i));
            return pioked;
        }
        return alt;
    }

    /**
     * 起草依据：解释为何这样起�?     */
    private List<String> buildReasons(String aotion, String riskLevel,
                                       int overdueDays, List<String> history) {
        List<String> reasons = new ArrayList<>();
        reasons.add("基于审批动作 " + aotion + " 选用对应模板");
        if (riskLevel != null) {
            reasons.add("结合风险等级 " + riskLevel + " 调整语气与措�?);
        }
        if (overdueDays > 0) {
            reasons.add("已关注超�?" + overdueDays + " 天，强调时效�?);
        }
        if (!history.isEmpty()) {
            reasons.add("参考历史意�?" + history.size() + " 条作为措辞风格锚�?);
        } else {
            reasons.add("无历史意见可用，选用系统默认措辞");
        }
        return reasons;
    }
}
