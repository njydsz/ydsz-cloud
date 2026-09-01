package com.njydsz.message.server.service.core;

import java.util.HashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.message.domain.dto.RichMediaContentDTO;
import com.njydsz.message.domain.vo.MsgTemplateVO;
import com.njydsz.message.server.filter.SensitiveWordFilter;
import com.njydsz.message.server.service.TemplateService;
import com.njydsz.message.server.service.VariableSourceResolver;
import com.njydsz.message.server.service.chain.SendContext;
import com.njydsz.message.server.template.RichMediaRenderer;
import com.njydsz.message.server.template.TemplateVariableValidator;
import com.njydsz.message.server.template.cache.CachedTemplateEngine;

/**
 * 消息内容渲染服务。
 *
 * <p>负责消息发送前的内容渲染阶段，从 {@link MessageServiceImpl}（原 God Class）中提取， 与发送编排 / 通道分发 / 查询职责解耦。
 *
 * <p><b>职责边界：</b>
 *
 * <ul>
 *   <li>模板加载与渲染 —— 按 templateCode + channel + locale 加载模板并渲染变量
 *   <li>变量数据源自动拉取 —— 解析变量数据源并自动填充缺失变量
 *   <li>模板变量类型校验 —— 校验变量类型与必填项
 *   <li>敏感词过滤 —— 对渲染后内容执行敏感词替换
 *   <li>富媒体消息渲染 —— 按通道类型渲染 HTML / Markdown / 纯文本
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageRenderService {

  /** 带 AST 缓存的模板引擎（YdszCache 实现，变量占位符渲染） */
  private final CachedTemplateEngine cachedTemplateEngine;

  /** 模板管理服务（加载/校验模板） */
  private final TemplateService templateService;

  /** 敏感词过滤器 */
  private final SensitiveWordFilter sensitiveWordFilter;

  /** 富媒体内容渲染器（HTML / Markdown / 纯文本） */
  private final RichMediaRenderer richMediaRenderer;

  /** 模板变量校验器 */
  private final TemplateVariableValidator templateVariableValidator;

  /** 变量数据源解析器 */
  private final VariableSourceResolver variableSourceResolver;

  /**
   * 渲染消息内容：模板加载 → 变量填充 → 渲染 → 敏感词 → 富媒体。
   *
   * @param request 消息请求
   * @param ctx 管线上下文（含 templateCode / channel / preference 等）
   * @return 渲染后的 content/subject
   */
  public RenderedContent renderContent(MessageRequest request, SendContext ctx) {
    String content = request.getContent();
    String subject = request.getSubject();
    String prefLocale = ctx.getPreference() != null ? ctx.getPreference().getLocale() : null;

    if (StringUtils.hasText(ctx.getTemplateCode())) {
      MsgTemplateVO template =
          templateService.loadByCodeAndChannel(
              ctx.getTemplateCode(), ctx.getChannel(), prefLocale, TenantContextHolder.getTenantId());
      if (template == null) {
        return new RenderedContent(content, subject, true);
      }
      // 模板变量类型校验
      if (StringUtils.hasText(template.getVariableDefs())) {
        var varDefs = templateVariableValidator.parse(template.getVariableDefs());
        if (!varDefs.isEmpty() && request.getParams() != null) {
          templateVariableValidator.validateAndFill(request.getParams(), varDefs, ctx.getTemplateCode());
        }
      }
      // 变量数据源自动拉取
      if (request.getParams() != null) {
        Map<String, Object> varCtx = new HashMap<>();
        if (StringUtils.hasText(request.getBizId())) {
          varCtx.put("bizId", request.getBizId());
        }
        if (StringUtils.hasText(ctx.getBizType())) {
          varCtx.put("bizType", ctx.getBizType());
        }
        varCtx.put("receiver", ctx.getReceiver());
        variableSourceResolver.resolveVariables(ctx.getTemplateCode(), request.getParams(), varCtx);
      }
      if (StringUtils.hasText(template.getContent())) {
        content = cachedTemplateEngine.render(template.getContent(), request.getParams());
      }
      if (!StringUtils.hasText(subject) && StringUtils.hasText(template.getSubject())) {
        subject = cachedTemplateEngine.render(template.getSubject(), request.getParams());
      }
    }

    // 敏感词过滤
    if (StringUtils.hasText(content)) {
      content = sensitiveWordFilter.filter(content);
    }

    // 富媒体消息渲染
    RichMediaContentDTO richMedia = richMediaRenderer.extractFromParams(request.getParams());
    if (richMedia != null) {
      String renderedContent =
          switch (ctx.getChannel() == null ? "" : ctx.getChannel().toUpperCase()) {
            case "EMAIL" -> richMediaRenderer.renderHtml(richMedia);
            case "INAPP", "DINGTALK", "WECOM", "FEISHU" -> richMediaRenderer.renderMarkdown(richMedia);
            case "SMS" -> richMediaRenderer.renderPlainText(richMedia);
            default -> richMediaRenderer.renderPlainText(richMedia);
          };
      if (StringUtils.hasText(renderedContent)) {
        content = renderedContent;
      }
      if (!StringUtils.hasText(subject) && StringUtils.hasText(richMedia.getTitle())) {
        subject = richMedia.getTitle();
      }
    }
    return new RenderedContent(content, subject, false);
  }

  /**
   * 渲染阶段产出。
   *
   * @param content 渲染后内容
   * @param subject 渲染后标题
   * @param templateMissing 模板缺失标志（渲染阶段无法返回 fail，由调用方检查）
   */
  public record RenderedContent(String content, String subject, boolean templateMissing) {}
}
