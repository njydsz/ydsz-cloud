package com.njydsz.pmis.workflow.server.service.impl.instance;

import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.SysException;
import com.njydsz.pmis.common.util.JsonUtils;
import com.njydsz.pmis.workflow.domain.entity.instance.FlowHisTaskDO;
import com.njydsz.pmis.workflow.domain.entity.instance.FlowInstanceDO;
import com.njydsz.pmis.workflow.infra.mapper.instance.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.infra.mapper.instance.FlowInstanceMapper;
import com.njydsz.pmis.workflow.server.service.instance.FlowExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P1-1/P1-2: 审批单导出 Service 实现
 *
 * <p>生成带水印的 HTML 审批单，支持浏览器直接打印（window.print()）。
 * 水印为全页面覆盖的重复文字（操作人 + 时间），防止截图泄露。
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
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
        FlowInstanceDO instance = loadInstance(instanceId);
        List<FlowHisTaskDO> history = loadHistory(instanceId);
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
            for (FlowHisTaskDO task : history) {
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
        BaseResponse.put("html", printHtml);
        BaseResponse.put("instanceId", instanceId);
        BaseResponse.put("exportTime", LocalDateTime.now().format(FMT));
        BaseResponse.put("exportBy", userName != null ? userName : userId);
        return result;
    }

    // ============================== 辅助方法 ==============================

    private FlowInstanceDO loadInstance(String instanceId) {
        FlowInstanceDO instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "流程实例不存在: " + instanceId);
        }
        return instance;
    }

    private List<FlowHisTaskDO> loadHistory(String instanceId) {
        List<FlowHisTaskDO> history = hisTaskMapper.selectByInstanceId(instanceId);
        return history != null ? history : new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseVariables(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> map = JsonUtils.parseMap(json);
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
