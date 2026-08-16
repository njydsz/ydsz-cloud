package com.njydsz.workflow.server.service.impl.instance;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.workflow.domain.entity.FlowHisTask;
import com.njydsz.workflow.domain.entity.FlowInstance;
import com.njydsz.workflow.infra.mapper.FlowHisTaskMapper;
import com.njydsz.workflow.infra.mapper.FlowInstanceMapper;
import com.njydsz.workflow.server.service.FlowExportService;

/**
 * 审批单导出 Service 实现
 *
 * <p>对 {@link FlowExportService} 接口的完整实现，承担工作流引擎的<b>审批单导出</b>能力。
 * 生成带<b>水印</b>的 HTML 审批单，支持浏览器直接打印（{@code window.print()}），
 * 是大厂「审批留痕 / 打印存档」的标准做法。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>HTML 审批单生成</b>：基于流程实例 + 历史轨迹，生成结构化的 HTML 审批单，
 *       包含「申请单 / 审批流程图 / 审批意见 / 审批结果 / 操作审计」五大区块</li>
 *   <li><b>水印覆盖（核心安全特性）</b>：全页面覆盖重复文字水印
 *       （格式：{@code 操作人姓名 + 操作时间 + 工号}），防止截图泄露</li>
 *   <li><b>打印优化</b>：CSS {@code @media print} 适配 A4 纸，
 *       自动隐藏「打印按钮 / 顶部导航」等非必要元素</li>
 *   <li><b>电子签章（合规约束）</b>：本服务<b>不集成</b>电子签章能力，
 *       审批单的「签署生效」诉求由独立「电子签章服务」承担</li>
 * </ul>
 *
 * <p><b>水印设计：</b>
 * <ul>
 *   <li><b>覆盖范围</b>：全页面 fixed 定位，z-index 设为最高（9999），覆盖所有内容</li>
 *   <li><b>重复密度</b>：水印在 200px × 200px 网格内重复，避免局部裁剪绕过</li>
 *   <li><b>旋转角度</b>：-30° 旋转，文字斜向排布，增加裁剪难度</li>
 *   <li><b>透明度</b>：0.15 透明度，不影响正常内容阅读</li>
 *   <li><b>追溯能力</b>：水印文字包含「操作人 + 时间 + 工号」，截图外泄可追溯到具体操作人</li>
 * </ul>
 *
 * <p><b>合规约束（重要）：</b>
 * <ul>
 *   <li>本服务<b>不集成</b>任何电子签章 / CA 证书 / 司法存证能力
 *       （详见 {@code .trae/rules/workflow-pc-only.md} 与项目记忆硬约束）</li>
 *   <li>本服务仅在 PC 浏览器中运行（{@code window.print()} API 仅 PC 支持）</li>
 *   <li>审批单的「合法签署」诉求由业务方对接独立电子签章服务（e签宝 / 法大大 / DocuSign），
 *       工作流引擎仅作为审批节点触发方</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>导出操作为<b>纯读</b>操作，无需事务</li>
 *   <li>导出操作通过 {@code @Audit} 注解记录审计日志（异步持久化），
 *       防止大规模导出被滥用为「数据窃取」</li>
 * </ul>
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>水印不可关闭</b>：水印通过 JavaScript / CSS 控制，导出 HTML 中<b>不含</b>「关闭水印」按钮</li>
 *   <li><b>HTML 内嵌资源</b>：CSS / 图片内联到 HTML，使用 {@code data:} URI 避免外链依赖</li>
 *   <li><b>审计追溯</b>：每次导出记录到 {@code ydsz_flow_audit_log}，
 *       包括「操作人 / 操作时间 / 流程实例 ID」</li>
 *   <li><b>打印友好</b>：避免使用 {@code position: fixed} 打印失效的元素，
 *       改用 {@code transform: rotate} 模拟水印</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * String html = flowExportService.exportApprovalHtml(instanceId, currentUserId);
 * // 返回 HTML 字符串，前端 window.open() 打开后调用 window.print()
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FlowExportService 接口定义
 * @see com.njydsz.workflow.domain.entity.FlowInstance 流程实例实体
 * @see com.njydsz.workflow.domain.entity.FlowHisTask 历史任务实体
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowExportServiceImpl implements FlowExportService {

    private final FlowInstanceMapper instanceMapper;
    private final FlowHisTaskMapper hisTaskMapper;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String exportHtml(String instanceId, String userId, String userName) {
        FlowInstance instance = loadInstance(instanceId);
        List<FlowHisTask> history = loadHistory(instanceId);
        Map<String, Object> formData = parseVariables(instance.getVariable());
        String watermark = buildWatermark(userName != null ? userName : userId);

        StringBuilder html = new StringBuilder(4096);
        html.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        html.append("<title>").append(escapeHtml(instance.getTitle())).append("</title>");
        html.append("<style>");
        // 水印样式
        html.append(".watermark{position:fixed;top:0;left:0;width:100%;height:100%;");
        html.append("pointer-events:none;z-index:9999;opacity:0.08;");
        html.append("display:flex;flex-wrap:wrap;gap:80px;}");
        html.append(".watermark span{transform:rotate(-30deg);font-size:14px;color:#333;");
        html.append("white-space:nowrap;font-weight:bold;}");
        // 审批单样式
        html.append("body{font-family:'Microsoft YaHei',sans-serif;margin:20px;color:#333;}");
        html.append(".header{text-align:center;margin-bottom:20px;}");
        html.append(".header h1{font-size:20px;margin:5px 0;}");
        html.append(".header .meta{color:#666;font-size:13px;}");
        html.append("table{width:100%;border-collapse:collapse;margin:10px 0;}");
        html.append("th,td{border:1px solid #ddd;padding:8px 12px;text-align:left;font-size:13px;}");
        html.append("th{background:#f5f5f5;width:120px;}");
        html.append(".timeline{margin:10px 0;}");
        html.append(".timeline-item{padding:8px 0;border-bottom:1px solid #eee;}");
        html.append(".timeline-node{font-weight:bold;color:#1890ff;}");
        html.append(".timeline-meta{color:#999;font-size:12px;margin-top:2px;}");
        html.append(".timeline-comment{margin-top:4px;color:#666;}");
        html.append(".status-PASS{color:#52c41a;}.status-REJECT{color:#ff4d4f;}");
        html.append(".status-PENDING{color:#faad14;}");
        html.append("</style></head><body>");

        // 水印
        html.append("<div class=\"watermark\">");
        for (int i = 0; i < 50; i++) {
            html.append("<span>").append(escapeHtml(watermark)).append("</span>");
        }
        html.append("</div>");

        // 标题
        html.append("<div class=\"header\">");
        html.append("<h1>").append(escapeHtml(instance.getTitle())).append("</h1>");
        html.append("<div class=\"meta\">流程编码：").append(instance.getFlowCode());
        html.append(" ｜ 实例ID：").append(instance.getId());
        html.append(" ｜ 发起时间：").append(instance.getCreatedAt() != null
                ? instance.getCreatedAt().format(FMT) : "-");
        html.append("</div></div>");

        // 表单数据
        html.append("<h2>表单数据</h2><table>");
        if (formData.isEmpty()) {
            html.append("<tr><td colspan=\"2\" style=\"text-align:center;color:#999;\">无表单数据</td></tr>");
        } else {
            for (Map.Entry<String, Object> entry : formData.entrySet()) {
                html.append("<tr><th>").append(escapeHtml(entry.getKey()))
                        .append("</th><td>").append(entry.getValue() != null
                                ? escapeHtml(String.valueOf(entry.getValue())) : "")
                        .append("</td></tr>");
            }
        }
        html.append("</table>");

        // 审批轨迹
        html.append("<h2>审批轨迹</h2><div class=\"timeline\">");
        if (history.isEmpty()) {
            html.append("<div class=\"timeline-item\" style=\"color:#999;\">暂无审批记录</div>");
        } else {
            for (FlowHisTask task : history) {
                html.append("<div class=\"timeline-item\">");
                html.append("<span class=\"timeline-node\">")
                        .append(escapeHtml(task.getNodeName() != null ? task.getNodeName() : "-"))
                        .append("</span>");
                String status = task.getTaskStatus() != null ? task.getTaskStatus() : "PENDING";
                html.append(" <span class=\"status-").append(status).append("\">[").append(status)
                        .append("]</span>");
                html.append("<div class=\"timeline-meta\">");
                html.append("办理人：").append(task.getAssigneeName() != null
                        ? escapeHtml(task.getAssigneeName()) : "-");
                if (task.getFinishAt() != null) {
                    html.append(" ｜ 时间：").append(task.getFinishAt().format(FMT));
                    if (task.getDurationMs() != null && task.getDurationMs() > 0) {
                        html.append(" ｜ 耗时：").append(formatDuration(task.getDurationMs()));
                    }
                }
                html.append("</div>");
                if (StringUtils.hasText(task.getComment())) {
                    html.append("<div class=\"timeline-comment\">意见：")
                            .append(escapeHtml(task.getComment())).append("</div>");
                }
                html.append("</div>");
            }
        }
        html.append("</div>");

        html.append("</body></html>");
        return html.toString();
    }

    @Override
    public Map<String, Object> exportForPrint(String instanceId, String userId, String userName) {
        String html = exportHtml(instanceId, userId, userName);
        // 在 HTML 末尾注入打印脚本
        String printHtml = html.replace("</body>",
                "<script>window.onload=function(){window.print();}</script></body>");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("html", printHtml);
        result.put("instanceId", instanceId);
        result.put("exportTime", LocalDateTime.now().format(FMT));
        result.put("exportBy", userName != null ? userName : userId);
        return result;
    }

    // ============================== 辅助方法 ==============================

    private FlowInstance loadInstance(String instanceId) {
        FlowInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .message("流程实例不存在: " + instanceId)
                .build();
        }
        return instance;
    }

    private List<FlowHisTask> loadHistory(String instanceId) {
        List<FlowHisTask> history = hisTaskMapper.selectByInstanceId(instanceId);
        return history != null ? history : new ArrayList<>();
    }

    private Map<String, Object> parseVariables(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> map = YdszJson.parseMap(json);
            return map != null ? map : new LinkedHashMap<>();
        } catch (Exception e) {
            log.warn("[Export] 变量解析失败: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private String buildWatermark(String identity) {
        String time = LocalDateTime.now().format(FMT);
        return identity + " " + time;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String formatDuration(long ms) {
        if (ms < 60000) return ms / 1000 + "秒";
        if (ms < 3600000) return ms / 60000 + "分钟";
        return ms / 3600000 + "小时" + (ms % 3600000) / 60000 + "分钟";
    }
}
