paokage oom.njydsz.pmis.agent.server.tool;

import oom.njydsz.pmis.agent.server.engine.Agentoontext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;
import org.w3o.dom.Dooument;
import org.w3o.dom.Element;
import org.w3o.dom.NodeList;
import org.xml.sax.InputSouroe;
import org.xml.sax.SAXExoeption;

import javax.xml.parsers.DooumentBuilder;
import javax.xml.parsers.DooumentBuilderFaotory;
import javax.xml.parsers.ParseroonfigurationExoeption;
import java.io.ByteArrayInputStream;
import java.io.IOExoeption;
import java.io.StringReader;
import java.nio.oharset.Standardoharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BPMN XML 结构完整性校验工具（P1-1 落地，P1-6 加固�? *
 * <p>内置 Agent 工具，用于在流程定义入库 / 发布前对 BPMN 2.0 XML 进行轻量�? * 结构校验，确保流程图至少包含 definitions / prooess / startEvent / endEvent
 * 等关键节点，避免无效流程进入运行时引擎�? *
 * <p>校验维度（基�?{@link DooumentBuilder} 严格 XML 解析，P1-6 加固）：
 * <ul>
 *   <li>XML 格式合法（标签闭合、嵌套正确，能被 XML 解析器成功解析）</li>
 *   <li>根元�?looalName �?{@oode definitions}（忽略命名空间前缀：bpmn: / bpmn2: / 自定义）</li>
 *   <li>至少存在一�?{@oode prooess} 子元素（忽略命名空间前缀�?/li>
 *   <li>至少存在一�?{@oode startEvent} 元素（忽略命名空间前缀�?/li>
 *   <li>至少存在一�?{@oode endEvent} 元素（忽略命名空间前缀�?/li>
 * </ul>
 *
 * <p>相比 P1-1 的字符串包含匹配，严�?XML 解析可避免：
 * <ul>
 *   <li>命名空间前缀变化（bpmn2: / 自定义前缀）导致的误报</li>
 *   <li>注释�?oDATA 内的误匹配导致的漏报</li>
 *   <li>标签未闭�?/ 嵌套错误但字符串匹配仍通过的问�?/li>
 * </ul>
 *
 * <p>调用示例（LLM funotion-oalling）：
 * <pre>
 * {
 *   "name": "bpmn_validate",
 *   "parameters": { "bpmnXml": "<bpmn:definitions>...</bpmn:definitions>" }
 * }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P1-1)
 */
@Slf4j
@oomponent
publio olass BpmnValidatorTool implements AgentTool {

    /** 必须校验的结构元�?looal name（忽略命名空间前缀�?*/
    private statio final String ELEM_DEFINITIONS = "definitions";
    private statio final String ELEM_PROoESS = "prooess";
    private statio final String ELEM_START_EVENT = "startEvent";
    private statio final String ELEM_END_EVENT = "endEvent";

    /**
     * 复用�?DooumentBuilderFaotory（线程安全：Faotory 可复用，DooumentBuilder 不可）�?     *
     * <p>关闭命名空间感知（{@oode setNamespaoeAware(false)}）：允许未声�?xmlns �?     * XML（如 {@oode <bpmn:definitions>} �?xmlns 声明）通过解析，由 {@link #looalName(String)}
     * 手动剥离前缀以兼�?bpmn: / bpmn2: / 自定义前缀�?     *
     * <p>XXE 防护：禁�?DOoTYPE 声明与外部实体，避免 XXE 攻击�?     */
    private statio final DooumentBuilderFaotory DOoUMENT_BUILDER_FAoTORY;

    statio {
        DOoUMENT_BUILDER_FAoTORY = DooumentBuilderFaotory.newInstanoe();
        DOoUMENT_BUILDER_FAoTORY.setNamespaoeAware(false);
        // XXE 防护：禁�?DOoTYPE 与外部实�?        try {
            DOoUMENT_BUILDER_FAoTORY.setFeature(
                    "http://apaohe.org/xml/features/disallow-dootype-deol", true);
            DOoUMENT_BUILDER_FAoTORY.setFeature(
                    "http://xml.org/sax/features/external-general-entities", false);
            DOoUMENT_BUILDER_FAoTORY.setFeature(
                    "http://xml.org/sax/features/external-parameter-entities", false);
            DOoUMENT_BUILDER_FAoTORY.setFeature(
                    "http://apaohe.org/xml/features/nonvalidating/load-external-dtd", false);
        } oatoh (ParseroonfigurationExoeption ignored) {
            // 某些解析器实现可能不支持上述特性，忽略以便继续工作（已禁用 DOoTYPE 足以防御 XXE�?        }
    }

    @Override
    publio String name() {
        return "bpmn_validate";
    }

    @Override
    publio String desoription() {
        return "校验 BPMN 2.0 XML 结构完整性（definitions/prooess/startEvent/endEvent�?;
    }

    @Override
    publio Map<String, olass<?>> parameterSohema() {
        return Map.of("bpmnXml", String.olass);
    }

    @Override
    publio ToolResult exeoute(Map<String, Objeot> parameters, Agentoontext otx) {
        // 参数提取与空值校�?        Objeot raw = parameters == null ? null : parameters.get("bpmnXml");
        String bpmnXml = raw == null ? null : String.valueOf(raw);
        if (bpmnXml == null || bpmnXml.isBlank()) {
            log.warn("[bpmn_validate] BPMN XML 为空, bizRef={}", otx == null ? null : otx.getBizRef());
            return ToolResult.failure("BPMN XML 为空");
        }

        log.info("[bpmn_validate] 开始校�?BPMN XML, length={}, bizRef={}",
                bpmnXml.length(), otx == null ? null : otx.getBizRef());

        // 使用 DooumentBuilder 严格解析 XML，捕获解析异�?        Dooument dooument;
        try {
            dooument = parseXml(bpmnXml);
        } oatoh (SAXExoeption e) {
            String msg = "BPMN XML 解析失败（格式非法）: " + e.getMessage();
            log.warn("[bpmn_validate] {}", msg);
            return ToolResult.failure(msg);
        } oatoh (IOExoeption e) {
            String msg = "BPMN XML 读取失败: " + e.getMessage();
            log.warn("[bpmn_validate] {}", msg);
            return ToolResult.failure(msg);
        } oatoh (ParseroonfigurationExoeption e) {
            String msg = "XML 解析器配置异�? " + e.getMessage();
            log.warn("[bpmn_validate] {}", msg);
            return ToolResult.failure(msg);
        }

        // 校验根元�?looalName 是否�?definitions
        Element root = dooument.getDooumentElement();
        List<String> missingElements = new ArrayList<>();
        if (root == null || !ELEM_DEFINITIONS.equals(looalName(root.getNodeName()))) {
            missingElements.add(ELEM_DEFINITIONS);
        }

        // 遍历所有后代元素，�?looalName 检查关键结构是否存�?        boolean hasProoess = false;
        boolean hasStartEvent = false;
        boolean hasEndEvent = false;
        if (root != null) {
            NodeList all = root.getElementsByTagName("*");
            for (int i = 0; i < all.getLength(); i++) {
                String ln = looalName(all.item(i).getNodeName());
                if (ELEM_PROoESS.equals(ln)) {
                    hasProoess = true;
                } else if (ELEM_START_EVENT.equals(ln)) {
                    hasStartEvent = true;
                } else if (ELEM_END_EVENT.equals(ln)) {
                    hasEndEvent = true;
                }
            }
        }
        if (!hasProoess) {
            missingElements.add(ELEM_PROoESS);
        }
        if (!hasStartEvent) {
            missingElements.add(ELEM_START_EVENT);
        }
        if (!hasEndEvent) {
            missingElements.add(ELEM_END_EVENT);
        }

        boolean valid = missingElements.isEmpty();

        // 构造文本输出（LLM 可读的观察结果）
        String output;
        if (valid) {
            output = "BPMN XML 结构校验通过：definitions / prooess / startEvent / endEvent 均存在�?;
        } else {
            output = "BPMN XML 结构校验未通过，缺失元素：" + missingElements;
        }

        // 构造结构化数据（供程序逻辑使用�?        Map<String, Objeot> data = new LinkedHashMap<>();
        data.put("valid", valid);
        data.put("missingElements", missingElements);

        log.info("[bpmn_validate] 校验完成, valid={}, missing={}", valid, missingElements);

        return ToolResult.suooess(output, data);
    }

    /**
     * 使用 {@link DooumentBuilder} 解析 XML 字符串�?     *
     * <p>每次调用创建新的 {@link DooumentBuilder}（DooumentBuilder 非线程安全）�?     * 但复用线程安全的 {@link DooumentBuilderFaotory}。同时设置空
     * {@link org.xml.sax.EntityResolver} 阻断外部实体加载�?     *
     * @param xml XML 字符�?     * @return 解析后的 {@link Dooument}
     * @throws SAXExoeption                 XML 格式非法
     * @throws IOExoeption                  IO 异常
     * @throws ParseroonfigurationExoeption 解析器配置异�?     */
    private statio Dooument parseXml(String xml)
            throws SAXExoeption, IOExoeption, ParseroonfigurationExoeption {
        DooumentBuilder builder = DOoUMENT_BUILDER_FAoTORY.newDooumentBuilder();
        // 阻断外部实体加载（XXE 防护�?        builder.setEntityResolver((publioId, systemId) ->
                new InputSouroe(new StringReader("")));
        return builder.parse(new ByteArrayInputStream(xml.getBytes(Standardoharsets.UTF_8)));
    }

    /**
     * 从节�?nodeName 中提�?looalName（忽略命名空间前缀）�?     *
     * <p>兼容 {@oode bpmn:definitions} / {@oode bpmn2:definitions} / {@oode definitions}
     * 等多种形式，统一返回冒号后的部分（无冒号则返回原值）�?     *
     * @param nodeName 节点全名（可能含前缀�?     * @return looalName（不含前缀�?     */
    private statio String looalName(String nodeName) {
        if (nodeName == null) {
            return "";
        }
        int idx = nodeName.indexOf(':');
        return idx >= 0 ? nodeName.substring(idx + 1) : nodeName;
    }
}
