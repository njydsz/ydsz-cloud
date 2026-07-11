package com.njydsz.pmis.agent.server.tool;

import com.njydsz.pmis.agent.server.engine.AgentContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BPMN XML 结构完整性校验工具（P1-1 落地，P1-6 加固）
 *
 * <p>内置 Agent 工具，用于在流程定义入库 / 发布前对 BPMN 2.0 XML 进行轻量级
 * 结构校验，确保流程图至少包含 definitions / process / startEvent / endEvent
 * 等关键节点，避免无效流程进入运行时引擎。
 *
 * <p>校验维度（基于 {@link DocumentBuilder} 严格 XML 解析，P1-6 加固）：
 * <ul>
 *   <li>XML 格式合法（标签闭合、嵌套正确，能被 XML 解析器成功解析）</li>
 *   <li>根元素 localName 为 {@code definitions}（忽略命名空间前缀：bpmn: / bpmn2: / 自定义）</li>
 *   <li>至少存在一个 {@code process} 子元素（忽略命名空间前缀）</li>
 *   <li>至少存在一个 {@code startEvent} 元素（忽略命名空间前缀）</li>
 *   <li>至少存在一个 {@code endEvent} 元素（忽略命名空间前缀）</li>
 * </ul>
 *
 * <p>相比 P1-1 的字符串包含匹配，严格 XML 解析可避免：
 * <ul>
 *   <li>命名空间前缀变化（bpmn2: / 自定义前缀）导致的误报</li>
 *   <li>注释或 CDATA 内的误匹配导致的漏报</li>
 *   <li>标签未闭合 / 嵌套错误但字符串匹配仍通过的问题</li>
 * </ul>
 *
 * <p>调用示例（LLM function-calling）：
 * <pre>
 * {
 *   "name": "bpmn_validate",
 *   "parameters": { "bpmnXml": "<bpmn:definitions>...</bpmn:definitions>" }
 * }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-1)
 */
@Slf4j
@Component
public class BpmnValidatorTool implements AgentTool {

    /** 必须校验的结构元素 local name（忽略命名空间前缀） */
    private static final String ELEM_DEFINITIONS = "definitions";
    private static final String ELEM_PROCESS = "process";
    private static final String ELEM_START_EVENT = "startEvent";
    private static final String ELEM_END_EVENT = "endEvent";

    /**
     * 复用的 DocumentBuilderFactory（线程安全：Factory 可复用，DocumentBuilder 不可）。
     *
     * <p>关闭命名空间感知（{@code setNamespaceAware(false)}）：允许未声明 xmlns 的
     * XML（如 {@code <bpmn:definitions>} 无 xmlns 声明）通过解析，由 {@link #localName(String)}
     * 手动剥离前缀以兼容 bpmn: / bpmn2: / 自定义前缀。
     *
     * <p>XXE 防护：禁用 DOCTYPE 声明与外部实体，避免 XXE 攻击。
     */
    private static final DocumentBuilderFactory DOCUMENT_BUILDER_FACTORY;

    static {
        DOCUMENT_BUILDER_FACTORY = DocumentBuilderFactory.newInstance();
        DOCUMENT_BUILDER_FACTORY.setNamespaceAware(false);
        // XXE 防护：禁用 DOCTYPE 与外部实体
        try {
            DOCUMENT_BUILDER_FACTORY.setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl", true);
            DOCUMENT_BUILDER_FACTORY.setFeature(
                    "http://xml.org/sax/features/external-general-entities", false);
            DOCUMENT_BUILDER_FACTORY.setFeature(
                    "http://xml.org/sax/features/external-parameter-entities", false);
            DOCUMENT_BUILDER_FACTORY.setFeature(
                    "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        } catch (ParserConfigurationException ignored) {
            // 某些解析器实现可能不支持上述特性，忽略以便继续工作（已禁用 DOCTYPE 足以防御 XXE）
        }
    }

    @Override
    public String name() {
        return "bpmn_validate";
    }

    @Override
    public String description() {
        return "校验 BPMN 2.0 XML 结构完整性（definitions/process/startEvent/endEvent）";
    }

    @Override
    public Map<String, Class<?>> parameterSchema() {
        return Map.of("bpmnXml", String.class);
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, AgentContext ctx) {
        // 参数提取与空值校验
        Object raw = parameters == null ? null : parameters.get("bpmnXml");
        String bpmnXml = raw == null ? null : String.valueOf(raw);
        if (bpmnXml == null || bpmnXml.isBlank()) {
            log.warn("[bpmn_validate] BPMN XML 为空, bizRef={}", ctx == null ? null : ctx.getBizRef());
            return ToolResult.failure("BPMN XML 为空");
        }

        log.info("[bpmn_validate] 开始校验 BPMN XML, length={}, bizRef={}",
                bpmnXml.length(), ctx == null ? null : ctx.getBizRef());

        // 使用 DocumentBuilder 严格解析 XML，捕获解析异常
        Document document;
        try {
            document = parseXml(bpmnXml);
        } catch (SAXException e) {
            String msg = "BPMN XML 解析失败（格式非法）: " + e.getMessage();
            log.warn("[bpmn_validate] {}", msg);
            return ToolResult.failure(msg);
        } catch (IOException e) {
            String msg = "BPMN XML 读取失败: " + e.getMessage();
            log.warn("[bpmn_validate] {}", msg);
            return ToolResult.failure(msg);
        } catch (ParserConfigurationException e) {
            String msg = "XML 解析器配置异常: " + e.getMessage();
            log.warn("[bpmn_validate] {}", msg);
            return ToolResult.failure(msg);
        }

        // 校验根元素 localName 是否为 definitions
        Element root = document.getDocumentElement();
        List<String> missingElements = new ArrayList<>();
        if (root == null || !ELEM_DEFINITIONS.equals(localName(root.getNodeName()))) {
            missingElements.add(ELEM_DEFINITIONS);
        }

        // 遍历所有后代元素，按 localName 检查关键结构是否存在
        boolean hasProcess = false;
        boolean hasStartEvent = false;
        boolean hasEndEvent = false;
        if (root != null) {
            NodeList all = root.getElementsByTagName("*");
            for (int i = 0; i < all.getLength(); i++) {
                String ln = localName(all.item(i).getNodeName());
                if (ELEM_PROCESS.equals(ln)) {
                    hasProcess = true;
                } else if (ELEM_START_EVENT.equals(ln)) {
                    hasStartEvent = true;
                } else if (ELEM_END_EVENT.equals(ln)) {
                    hasEndEvent = true;
                }
            }
        }
        if (!hasProcess) {
            missingElements.add(ELEM_PROCESS);
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
            output = "BPMN XML 结构校验通过：definitions / process / startEvent / endEvent 均存在。";
        } else {
            output = "BPMN XML 结构校验未通过，缺失元素：" + missingElements;
        }

        // 构造结构化数据（供程序逻辑使用）
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("valid", valid);
        data.put("missingElements", missingElements);

        log.info("[bpmn_validate] 校验完成, valid={}, missing={}", valid, missingElements);

        return ToolResult.success(output, data);
    }

    /**
     * 使用 {@link DocumentBuilder} 解析 XML 字符串。
     *
     * <p>每次调用创建新的 {@link DocumentBuilder}（DocumentBuilder 非线程安全），
     * 但复用线程安全的 {@link DocumentBuilderFactory}。同时设置空
     * {@link org.xml.sax.EntityResolver} 阻断外部实体加载。
     *
     * @param xml XML 字符串
     * @return 解析后的 {@link Document}
     * @throws SAXException                 XML 格式非法
     * @throws IOException                  IO 异常
     * @throws ParserConfigurationException 解析器配置异常
     */
    private static Document parseXml(String xml)
            throws SAXException, IOException, ParserConfigurationException {
        DocumentBuilder builder = DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
        // 阻断外部实体加载（XXE 防护）
        builder.setEntityResolver((publicId, systemId) ->
                new InputSource(new StringReader("")));
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 从节点 nodeName 中提取 localName（忽略命名空间前缀）。
     *
     * <p>兼容 {@code bpmn:definitions} / {@code bpmn2:definitions} / {@code definitions}
     * 等多种形式，统一返回冒号后的部分（无冒号则返回原值）。
     *
     * @param nodeName 节点全名（可能含前缀）
     * @return localName（不含前缀）
     */
    private static String localName(String nodeName) {
        if (nodeName == null) {
            return "";
        }
        int idx = nodeName.indexOf(':');
        return idx >= 0 ? nodeName.substring(idx + 1) : nodeName;
    }
}
