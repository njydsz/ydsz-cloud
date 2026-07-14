ackage com.njydsz.pmis.common.util.xml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.dom4j.tree.DefaultDocument;

import com.njydsz.pmis.common.util.collection.MapUtils;
import com.njydsz.pmis.common.json.YdszJson;

import lombok.extern.slf4j.Slf4j;

/**
 * XML 处理工具类 - 增强版
 * 参考互联网大厂最佳实践，提供安全、高效、易用的 XML 操作
 * 
 * 主要功能：
 * 1. XXE 安全防护
 * 2. XML 与 Map/JSON/JavaBean 互转
 * 3. XPath 查询支持
 * 4. 文件/流/XML 字符串多种输入输出
 * 5. 链式调用支持
 * 
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Slf4j
public class DOMUtils {

    private static final String DEFAULT_ENCODING = StandardCharsets.UTF_8.name();

    private DOMUtils() {
        throw new IllegalStateException("Utility class - cannot be instantiated");
    }

    /**
     * 创建安全的 SAXReader (防御 XXE 攻击)
     */
    public static SAXReader createSAXReader() {
        SAXReader reader = new SAXReader();
        configureXXEProtection(reader);
        return reader;
    }

    /**
     * 配置 XXE 防护
     */
    private static void configureXXEProtection(SAXReader reader) {
        try {
            reader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
            reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            reader.setFeature("http://xml.org/sax/features/namespaces", true);
        } catch (Exception e) {
            log.warn("DOMUtils -> 开启 XXE 防护失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 将 XML 字符串解析为 Document(安全版)
     */
    public static Document parseText(String xml) throws DocumentException {
        if (xml == null || xml.trim().isEmpty()) {
            return null;
        }
        return DocumentHelper.parseText(xml);
    }

    /**
     * 从文件读取 XML Document(安全版)
     */
    public static Document readFromFile(String filePath) throws DocumentException {
        return readFromFile(new File(filePath));
    }

    /**
     * 从文件读取 XML Document(安全版)
     */
    public static Document readFromFile(File file) throws DocumentException {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("XML file does not exist: " + (file != null ? file.getPath() : "null"));
        }
        SAXReader reader = createSAXReader();
        return reader.read(file);
    }

    /**
     * 从输入流读取 XML Document(安全版)
     */
    public static Document readFromStream(InputStream inputStream) throws DocumentException {
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream is null");
        }
        SAXReader reader = createSAXReader();
        return reader.read(inputStream);
    }

    /**
     * 从 Reader 读取 XML Document(安全版)
     */
    public static Document readFromReader(Reader reader) throws DocumentException {
        if (reader == null) {
            throw new IllegalArgumentException("Reader is null");
        }
        SAXReader saxReader = createSAXReader();
        return saxReader.read(reader);
    }

    /**
     * 将 Document 格式化输出为字符串
     */
    public static String toString(Document document) {
        return toString(document, false);
    }

    /**
     * 将 Document 格式化输出为字符串
     *
     * @param document 文档对象
     * @param prettyPrint 是否格式化输出
     */
    public static String toString(Document document, boolean prettyPrint) {
        if (document == null) {
            return null;
        }
        StringWriter sw = new StringWriter();
        OutputFormat format = prettyPrint ? OutputFormat.createPrettyPrint() : OutputFormat.createCompactFormat();
        format.setEncoding(DEFAULT_ENCODING);
        XMLWriter writer = new XMLWriter(sw, format);
        try {
            writer.write(document);
            return sw.toString();
        } catch (IOException e) {
            log.error("DOMUtils -> 转换异常：{}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 将 Document 写入文件
     */
    public static void writeToFile(Document document, String filePath) throws IOException {
        writeToFile(document, new File(filePath));
    }

    /**
     * 将 Document 写入文件
     */
    public static void writeToFile(Document document, File file) throws IOException {
        if (document == null) {
            throw new IllegalArgumentException("Document is null");
        }
        if (file == null) {
            throw new IllegalArgumentException("File is null");
        }
        File parentFile = file.getParentFile();
        if (parentFile != null && !parentFile.exists()) {
            parentFile.mkdirs();
        }
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), DEFAULT_ENCODING)) {
            OutputFormat format = OutputFormat.createPrettyPrint();
            format.setEncoding(DEFAULT_ENCODING);
            XMLWriter xmlWriter = new XMLWriter(writer, format);
            xmlWriter.write(document);
        }
    }

    /**
     * 添加根节点
     */
    public static Element addRoot(Document document, String rootName) {
        if (document == null || rootName == null || rootName.trim().isEmpty()) {
            throw new IllegalArgumentException("Document or rootName is null/empty");
        }
        return document.addElement(rootName);
    }

    /**
     * 添加根节点并设置值
     */
    public static Element addRoot(Document document, String rootName, String rootValue) {
        Element root = addRoot(document, rootName);
        root.setText(rootValue == null ? "" : rootValue);
        return root;
    }

    /**
     * 添加根节点并设置属性
     */
    public static Element addRoot(Document document, String rootName, Map<String, String> attributes) {
        Element root = addRoot(document, rootName);
        if (MapUtils.isNotEmpty(attributes)) {
            attributes.forEach((key, value) -> {
                if (key != null && !key.trim().isEmpty() && value != null && !value.trim().isEmpty()) {
                    if (key.contains(":")) {
                        String namespace = key.substring(0, key.indexOf(":"));
                        root.addNamespace(namespace, value);
                    } else {
                        root.addAttribute(key, value);
                    }
                }
            });
        }
        return root;
    }

    /**
     * 获取根元素
     */
    public static Element getRootElement(Document document) {
        if (document == null) {
            return null;
        }
        return document.getRootElement();
    }

    /**
     * 根据路径获取单个元素
     */
    public static Element getElement(Element parent, String path) {
        if (parent == null || path == null || path.trim().isEmpty()) {
            return null;
        }
        return parent.element(path);
    }

    /**
     * 根据路径获取所有子元素
     */
    public static List<Element> getElements(Element parent, String path) {
        if (parent == null || path == null || path.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return parent.elements(path);
    }

    /**
     * 获取元素的文本值
     */
    public static String getText(Element element, String childPath) {
        if (element == null) {
            return null;
        }
        if (childPath == null || childPath.trim().isEmpty()) {
            return element.getTextTrim();
        }
        Element child = element.element(childPath);
        return child != null ? child.getTextTrim() : null;
    }

    /**
     * 获取元素的属性值
     */
    public static String getAttributeValue(Element element, String attributeName) {
        if (element == null || attributeName == null || attributeName.trim().isEmpty()) {
            return null;
        }
        Attribute attr = element.attribute(attributeName);
        return attr != null ? attr.getValue() : null;
    }

    /**
     * 设置元素的属性
     */
    public static void setAttribute(Element element, String name, String value) {
        if (element != null && name != null && !name.trim().isEmpty()) {
            element.addAttribute(name, value != null ? value : "");
        }
    }

    /**
     * 添加子元素
     */
    public static Element addElement(Element parent, String name) {
        if (parent == null || name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Parent element or name is null/empty");
        }
        return parent.addElement(name);
    }

    /**
     * 添加子元素并设置文本值
     */
    public static Element addElement(Element parent, String name, String text) {
        Element element = addElement(parent, name);
        element.setText(text != null ? text : "");
        return element;
    }

    /**
     * 添加子元素并设置属性
     */
    public static Element addElement(Element parent, String name, Map<String, String> attributes) {
        Element element = addElement(parent, name);
        if (MapUtils.isNotEmpty(attributes)) {
            attributes.forEach(element::addAttribute);
        }
        return element;
    }

    /**
     * 删除指定的子元素
     */
    public static boolean removeElement(Element parent, String name) {
        if (parent == null || name == null || name.trim().isEmpty()) {
            return false;
        }
        Element element = parent.element(name);
        return element != null && parent.remove(element);
    }

    /**
     * XPath 查询 - 返回单个节点
     */
    public static Node selectSingleNode(Element element, String xpathExpression) {
        if (element == null || xpathExpression == null || xpathExpression.trim().isEmpty()) {
            return null;
        }
        return element.selectSingleNode(xpathExpression);
    }

    /**
     * XPath 查询 - 返回节点列表
     */
    public static List<Node> selectNodes(Element element, String xpathExpression) {
        if (element == null || xpathExpression == null || xpathExpression.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return element.selectNodes(xpathExpression);
    }

    /**
     * XPath 查询 - 返回单个元素
     */
    public static Element selectSingleElement(Element element, String xpathExpression) {
        Node node = selectSingleNode(element, xpathExpression);
        return node instanceof Element ? (Element) node : null;
    }

    /**
     * XPath 查询 - 返回元素列表
     */
    public static List<Element> selectElements(Element element, String xpathExpression) {
        List<Node> nodes = selectNodes(element, xpathExpression);
        return nodes.stream()
                .filter(node -> node instanceof Element)
                .map(node -> (Element) node)
                .collect(Collectors.toList());
    }

    /**
     * XML 转 Map(包含属性)
     */
    public static Map<String, Object> xml2Map(String xml) {
        if (xml == null || xml.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            Document document = parseText(xml);
            if (document == null) {
                return Collections.emptyMap();
            }
            return element2Map(getRootElement(document));
        } catch (DocumentException e) {
            log.error("DOMUtils -> XML 转 Map 失败：{}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    /**
     * XML Document 转 Map
     */
    public static Map<String, Object> xml2Map(Document document) {
        if (document == null) {
            return Collections.emptyMap();
        }
        return element2Map(getRootElement(document));
    }

    /**
     * XML Element 转 Map
     */
    
    private static Map<String, Object> element2Map(Element element) {
        if (element == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> map = new LinkedHashMap<>();

        // 处理属性
        List<Attribute> attributes = element.attributes();
        if (attributes != null && !attributes.isEmpty()) {
            Map<String, String> attrMap = new LinkedHashMap<>();
            for (Attribute attr : attributes) {
                attrMap.put(attr.getName(), attr.getValue());
            }
            if (!attrMap.isEmpty()) {
                map.put("@attributes", attrMap);
            }
        }

        // 处理子元素
        List<Element> elements = element.elements();
        if (elements != null && !elements.isEmpty()) {
            for (Element child : elements) {
                String key = child.getName();
                Object value = element2Map(child);
                
                if (map.containsKey(key)) {
                    Object existingValue = map.get(key);
                    if (existingValue instanceof List) {
                        ((List<Object>) existingValue).add(value);
                    } else {
                        List<Object> list = new ArrayList<>();
                        list.add(existingValue);
                        list.add(value);
                        map.put(key, list);
                    }
                } else {
                    map.put(key, value);
                }
            }
        }

        // 处理文本内容
        String text = element.getTextTrim();
        if (text != null && !text.trim().isEmpty() && map.isEmpty()) {
            map.put("#text", text);
        } else if (text != null && !text.trim().isEmpty()) {
            map.put("#text", text);
        }

        return map;
    }

    /**
     * Map 转 XML
     */
    public static String map2Xml(Map<String, Object> map, String rootName) {
        if (MapUtils.isEmpty(map) || rootName == null || rootName.trim().isEmpty()) {
            return null;
        }
        try {
            Document document = DocumentHelper.createDocument();
            Element root = document.addElement(rootName);
            map2Element(map, root);
            return toString(document);
        } catch (Exception e) {
            log.error("DOMUtils -> Map 转 XML 失败：{}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Map 转 Element
     */
    
    private static void map2Element(Map<String, Object> map, Element parent) {
        if (MapUtils.isEmpty(map) || parent == null) {
            return;
        }

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if ("@attributes".equals(key)) {
                if (value instanceof Map) {
                    Map<String, String> attrs = (Map<String, String>) value;
                    attrs.forEach(parent::addAttribute);
                }
            } else if ("#text".equals(key)) {
                parent.setText(value != null ? value.toString() : "");
            } else if (value instanceof Map) {
                Element child = parent.addElement(key);
                map2Element((Map<String, Object>) value, child);
            } else if (value instanceof List) {
                for (Object item : (List<Object>) value) {
                    if (item instanceof Map) {
                        Element child = parent.addElement(key);
                        map2Element((Map<String, Object>) item, child);
                    } else {
                        Element child = parent.addElement(key);
                        child.setText(item != null ? item.toString() : "");
                    }
                }
            } else {
                Element child = parent.addElement(key);
                child.setText(value != null ? value.toString() : "");
            }
        }
    }

    /**
     * XML 转 JSON
     */
    public static String xml2Json(String xml) {
        if (xml == null || xml.trim().isEmpty()) {
            return null;
        }
        Map<String, Object> map = xml2Map(xml);
        return YdszJson.toJson(map);
    }

    /**
     * JSON 转 XML
     */
    
    public static String json2Xml(String json, String rootName) {
        if (json == null || json.trim().isEmpty() || rootName == null || rootName.trim().isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> map = YdszJson.toObject(json, Map.class);
            return map2Xml(map, rootName);
        } catch (Exception e) {
            log.error("DOMUtils -> JSON 转 XML 失败：{}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * XML 转 JavaBean
     */
    public static <T> T xml2Bean(String xml, Class<T> clazz) {
        if (xml == null || xml.trim().isEmpty() || clazz == null) {
            return null;
        }
        try {
            Map<String, Object> map = xml2Map(xml);
            return YdszJson.toObject(YdszJson.toJson(map), clazz);
        } catch (Exception e) {
            log.error("DOMUtils -> XML 转 Bean 失败：{}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * JavaBean 转 XML
     */
    
    public static String bean2Xml(Object bean, String rootName) {
        if (bean == null || rootName == null || rootName.trim().isEmpty()) {
            return null;
        }
        try {
            String json = YdszJson.toJson(bean);
            Map<String, Object> map = YdszJson.toObject(json, Map.class);
            return map2Xml(map, rootName);
        } catch (Exception e) {
            log.error("DOMUtils -> Bean 转 XML 失败：{}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 创建新的 Document
     */
    public static Document createDocument() {
        return new DefaultDocument();
    }

    /**
     * 创建新的 Document 并添加根节点
     */
    public static Document createDocument(String rootName) {
        Document document = createDocument();
        addRoot(document, rootName);
        return document;
    }

    /**
     * 复制 Document
     */
    public static Document cloneDocument(Document document) {
        if (document == null) {
            return null;
        }
        try {
            return DocumentHelper.parseText(toString(document, false));
        } catch (DocumentException e) {
            log.error("DOMUtils -> 复制 Document 失败：{}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 合并多个 Document 到根节点
     */
    public static void mergeDocuments(Document target, List<Document> sources) {
        if (target == null || sources == null || sources.isEmpty()) {
            return;
        }
        Element targetRoot = getRootElement(target);
        if (targetRoot == null) {
            return;
        }

        for (Document source : sources) {
            Element sourceRoot = getRootElement(source);
            if (sourceRoot != null) {
                List<Element> children = new ArrayList<>(sourceRoot.elements());
                for (Element child : children) {
                    child.detach();
                    targetRoot.add(child);
                }
            }
        }
    }

    /**
     * 验证 XML 是否合法
     */
    public static boolean isValidXml(String xml) {
        if (xml == null || xml.trim().isEmpty()) {
            return false;
        }
        try {
            Document document = parseText(xml);
            return document != null && getRootElement(document) != null;
        } catch (DocumentException e) {
            log.debug("DOMUtils -> XML 验证失败：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取 XML 编码
     */
    public static String getXmlEncoding(String xml) {
        if (xml == null || xml.trim().isEmpty()) {
            return DEFAULT_ENCODING;
        }
        try {
            Document document = parseText(xml);
            return document != null ? document.getXMLEncoding() : DEFAULT_ENCODING;
        } catch (DocumentException e) {
            return DEFAULT_ENCODING;
        }
    }

    /**
     * 获取 XML 版本
     */
    public static String getXmlVersion(String xml) {
        if (xml == null || xml.trim().isEmpty()) {
            return "1.0";
        }
        try {
            Document document = parseText(xml);
            return document != null ? document.getXMLEncoding() != null ? "1.0" : "1.0" : "1.0";
        } catch (DocumentException e) {
            return "1.0";
        }
    }
}
