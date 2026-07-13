package com.njydsz.pmis.common.notify.template;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML 邮件模板注册中心（P2-7）
 *
 * <p>提供 HTML 邮件模板的注册、查找和渲染能力。
 * 支持通过代码动态注册模板，也支持从配置文件加载。
 *
 * <p>模板内容使用 {@code #{variableName}} 格式的 SpEL 占位符，
 * 渲染时自动替换为参数值。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * HtmlTemplateRegistry.HtmlEmailTemplate template = new HtmlTemplateRegistry.HtmlEmailTemplate(
 *     "project-approval",
 *     "【ydsz项目管理】项目立项审批通知",
 *     "<h2>#{projectName} 立项审批</h2><p>申请人：#{applicant}</p>"
 * );
 * registry.register(template);
 * String html = registry.renderHtml("project-approval", Map.of("projectName", "PMIS", "applicant", "张三"));
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
public class HtmlTemplateRegistry {

	private static final Logger log = LoggerFactory.getLogger(HtmlTemplateRegistry.class);

	/** SpEL 占位符匹配模式 */
	private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("#\\{([^}]+)}");

	private final Map<String, HtmlEmailTemplate> templates = new ConcurrentHashMap<>();

	public HtmlTemplateRegistry() {
	}

	/**
	 * 注册 HTML 邮件模板
	 *
	 * @param template 模板定义
	 */
	public void register(HtmlEmailTemplate template) {
		if (template == null || !StringUtils.hasText(template.getCode())) {
			throw new IllegalArgumentException("模板编码不能为空");
		}
		templates.put(template.getCode(), template);
		log.info("[HtmlTemplateRegistry] 模板已注册: code={}", template.getCode());
	}

	/**
	 * 批量注册模板
	 *
	 * @param templateMap 模板映射
	 */
	public void registerAll(Map<String, HtmlEmailTemplate> templateMap) {
		if (templateMap != null) {
			templateMap.values().forEach(this::register);
		}
	}

	/**
	 * 获取模板
	 *
	 * @param code 模板编码
	 * @return 模板定义，不存在返回 null
	 */
	public HtmlEmailTemplate get(String code) {
		return templates.get(code);
	}

	/**
	 * 渲染 HTML 模板
	 *
	 * @param code   模板编码
	 * @param params 渲染参数
	 * @return 渲染后的 HTML 内容
	 */
	public String renderHtml(String code, Map<String, Object> params) {
		HtmlEmailTemplate template = templates.get(code);
		if (template == null) {
			throw new IllegalArgumentException("模板不存在: " + code);
		}
		if (!template.isEnabled()) {
			throw new IllegalStateException("模板已禁用: " + code);
		}
		return renderPlaceholders(template.getHtmlContent(), params);
	}

	/**
	 * 渲染纯文本模板
	 *
	 * @param code   模板编码
	 * @param params 渲染参数
	 * @return 渲染后的纯文本内容
	 */
	public String renderText(String code, Map<String, Object> params) {
		HtmlEmailTemplate template = templates.get(code);
		if (template == null) {
			throw new IllegalArgumentException("模板不存在: " + code);
		}
		if (!StringUtils.hasText(template.getTextContent())) {
			return "";
		}
		return renderPlaceholders(template.getTextContent(), params);
	}

	/**
	 * 渲染模板主题
	 *
	 * @param code   模板编码
	 * @param params 渲染参数
	 * @return 渲染后的主题
	 */
	public String renderSubject(String code, Map<String, Object> params) {
		HtmlEmailTemplate template = templates.get(code);
		if (template == null || !StringUtils.hasText(template.getSubject())) {
			return code;
		}
		return renderPlaceholders(template.getSubject(), params);
	}

	/**
	 * 替换模板中的 #{placeholder} 占位符
	 *
	 * @param template 模板字符串
	 * @param params   参数映射
	 * @return 渲染后的字符串
	 */
	private String renderPlaceholders(String template, Map<String, Object> params) {
		if (!StringUtils.hasText(template) || params == null || params.isEmpty()) {
			return template;
		}
		Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			String key = matcher.group(1);
			Object value = params.get(key);
			String replacement = value != null ? Matcher.quoteReplacement(String.valueOf(value)) : "";
			matcher.appendReplacement(sb, replacement);
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	/**
	 * 移除模板
	 *
	 * @param code 模板编码
	 */
	public void remove(String code) {
		templates.remove(code);
	}

	/**
	 * 获取已注册模板数量
	 *
	 * @return 模板数量
	 */
	public int size() {
		return templates.size();
	}

	/**
	 * 判断模板是否存在
	 *
	 * @param code 模板编码
	 * @return true 表示模板已注册
	 */
	public boolean contains(String code) {
		return templates.containsKey(code);
	}

	/**
	 * HTML 邮件模板定义
	 */
	public static class HtmlEmailTemplate {

		private String code;
		private String subject;
		private String htmlContent;
		private String textContent;
		private boolean enabled = true;

		public HtmlEmailTemplate() {
		}

		public HtmlEmailTemplate(String code, String subject, String htmlContent) {
			this.code = code;
			this.subject = subject;
			this.htmlContent = htmlContent;
		}

		public String getCode() { return code; }
		public void setCode(String code) { this.code = code; }
		public String getSubject() { return subject; }
		public void setSubject(String subject) { this.subject = subject; }
		public String getHtmlContent() { return htmlContent; }
		public void setHtmlContent(String htmlContent) { this.htmlContent = htmlContent; }
		public String getTextContent() { return textContent; }
		public void setTextContent(String textContent) { this.textContent = textContent; }
		public boolean isEnabled() { return enabled; }
		public void setEnabled(boolean enabled) { this.enabled = enabled; }
	}
}
