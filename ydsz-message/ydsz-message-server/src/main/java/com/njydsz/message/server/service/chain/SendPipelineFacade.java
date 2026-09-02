package com.njydsz.message.server.service.chain;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;

/**
 * 管线模板门面：根据请求特征自动选择 {@link PipelineTemplate} 并执行对应的 Handler 链。
 *
 * <p>核心能力：
 *
 * <ul>
 *   <li>自动识别场景：根据请求字段（templateCode / cascadeTo / parentMsgId）推断模板
 *   <li>模板缓存：相同模板的 Handler 链缓存复用，避免每次请求重新过滤
 *   <li>降级开关：配置 {@code ydsz.message.pipeline.template.enabled=false} 时回退到全量管线
 * </ul>
 *
 * <p>使用方式：
 *
 * <pre>{@code
 *   // 自动识别模板
 *   SendContext ctx = facade.execute(request, new SendContext());
 *
 *   // 显式指定模板
 *   SendContext ctx = facade.execute(request, new SendContext(), PipelineTemplate.SIMPLE_SEND);
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SendPipelineFacade {

  private final SendPipeline sendPipeline;

  /** 管线模板开关：false 时回退到全量管线（兼容旧行为） */
  @Value("${ydsz.message.pipeline.template.enabled:true}")
  private boolean templateEnabled;

  /** 模板 -> 过滤后的 Handler 链缓存 */
  private final Map<PipelineTemplate, List<SendHandler>> templateCache = new ConcurrentHashMap<>();

  /**
   * 执行管线（自动识别模板）。
   *
   * <p>根据请求特征自动选择模板，执行对应的 Handler 链。
   *
   * @param request 消息请求
   * @param ctx 管线上下文
   * @return 执行后的上下文（ctx.errorResult != null 表示失败）
   */
  public SendContext execute(MessageRequest request, SendContext ctx) {
    PipelineTemplate template = resolveTemplate(request);
    return execute(request, ctx, template);
  }

  /**
   * 执行管线（显式指定模板）。
   *
   * <p>调用方明确知道场景时使用，跳过自动识别逻辑。
   *
   * @param request 消息请求
   * @param ctx 管线上下文
   * @param template 指定的管线模板
   * @return 执行后的上下文
   */
  public SendContext execute(MessageRequest request, SendContext ctx, PipelineTemplate template) {
    if (!templateEnabled) {
      log.debug("[PipelineFacade] 模板功能已关闭,回退全量管线");
      return sendPipeline.execute(request, ctx);
    }

    List<SendHandler> handlers = resolveHandlers(template);
    if (handlers.isEmpty()) {
      log.warn("[PipelineFacade] 模板 {} 无可用 Handler,回退全量管线", template.getCode());
      return sendPipeline.execute(request, ctx);
    }

    log.debug("[PipelineFacade] 执行管线模板: template={} handlerCount={}", template.getCode(), handlers.size());

    for (SendHandler handler : handlers) {
      try {
        boolean passed = handler.handle(request, ctx);
        if (!passed) {
          log.debug(
              "[PipelineFacade] Handler 短路: template={} handler={}",
              template.getCode(),
              handler.name());
          return ctx;
        }
      } catch (Exception e) {
        log.error(
            "[PipelineFacade] Handler 执行异常: template={} handler={} err={}",
            template.getCode(),
            handler.name(),
            e.getMessage(),
            e);
        ctx.setErrorResult(
            MessageResult.fail(
                ctx.getChannel(),
                null,
                "管线处理异常 [" + handler.name() + "]: " + e.getMessage(),
                "管线处理异常 [" + handler.name() + "]: " + e.getMessage(),
                null));
        return ctx;
      }
    }
    return ctx;
  }

  /**
   * 根据请求特征自动识别管线模板。
   *
   * <p>识别规则（优先级从高到低）：
   *
   * <ol>
   *   <li>显式指定：scenario 字段不为空 → 直接使用
   *   <li>内部回调：parentMsgId 不为空 → INTERNAL_CALLBACK
   *   <li>批量/级联：cascadeTo 不为空 → BATCH_SEND
   *   <li>模板发送：templateCode 不为空 → TEMPLATE_SEND
   *   <li>默认：SIMPLE_SEND
   * </ol>
   *
   * @param request 消息请求
   * @return 识别出的管线模板
   */
  public PipelineTemplate resolveTemplate(MessageRequest request) {
    if (request == null) {
      return PipelineTemplate.FULL_PROCESS;
    }

    // 0. 显式指定场景时优先使用
    if (StringUtils.hasText(request.getScenario())) {
      return PipelineTemplate.fromCode(request.getScenario());
    }

    // 1. 内部回调场景：有 parentMsgId 表示是级联子消息
    if (StringUtils.hasText(request.getParentMsgId())) {
      return PipelineTemplate.INTERNAL_CALLBACK;
    }

    // 2. 批量/级联场景：有 cascadeTo 子消息列表
    if (request.getCascadeTo() != null && !request.getCascadeTo().isEmpty()) {
      return PipelineTemplate.BATCH_SEND;
    }

    // 3. 模板发送场景：有 templateCode
    if (StringUtils.hasText(request.getTemplateCode())) {
      return PipelineTemplate.TEMPLATE_SEND;
    }

    // 4. 默认简单发送
    return PipelineTemplate.SIMPLE_SEND;
  }

  /**
   * 根据模板解析 Handler 链（带缓存）。
   *
   * <p>从全量 Handler 列表中过滤出模板指定的 Handler，并按 order 排序。 结果缓存避免重复过滤。
   *
   * @param template 管线模板
   * @return 过滤并排序后的 Handler 列表
   */
  private List<SendHandler> resolveHandlers(PipelineTemplate template) {
    return templateCache.computeIfAbsent(
        template,
        t -> {
          List<SendHandler> allHandlers = sendPipeline.getHandlers();
          List<Class<? extends SendHandler>> targetClasses = t.getHandlerClasses();

          List<SendHandler> filtered =
              allHandlers.stream()
                  .filter(h -> targetClasses.contains(h.getClass()))
                  .sorted(Comparator.comparingInt(SendHandler::order))
                  .collect(Collectors.toList());

          log.info(
              "[PipelineFacade] 模板 Handler 链构建: template={} handlers={}",
              t.getCode(),
              filtered.stream()
                  .map(h -> h.name() + "(" + h.order() + ")")
                  .collect(Collectors.joining(" -> ")));
          return filtered;
        });
  }

  /**
   * 清除模板缓存（用于动态刷新场景）。
   *
   * <p>当 Handler 列表发生变化时（如动态注册），调用此方法清除缓存。
   */
  public void clearCache() {
    templateCache.clear();
    log.info("[PipelineFacade] 模板缓存已清除");
  }
}
