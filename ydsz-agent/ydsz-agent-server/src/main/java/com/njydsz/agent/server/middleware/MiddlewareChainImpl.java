package com.njydsz.agent.server.middleware;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.middleware.AgentMiddleware;
import com.njydsz.agent.domain.middleware.MiddlewareChain;
import com.njydsz.agent.domain.middleware.MiddlewareContext;
import com.njydsz.agent.domain.middleware.MiddlewareException;
import com.njydsz.agent.domain.model.ChatResponse;

/**
 * 中间件链默认实现 — 按优先级排序中间件并串行调度各钩子。
 *
 * <p>执行模型（洋葱模型）：
 * <ul>
 *   <li>前置钩子（onAgentStart / onSystemPrompt / onReasoning）：优先级升序（数字小的先执行）</li>
 *   <li>核心钩子（onModelCall）：洋葱包裹——外层中间件放行后触发内层，最后执行实际 LLM 调用</li>
 *   <li>后置钩子（onObservation / onAgentEnd）：优先级升序（数字小的先执行）</li>
 * </ul>
 *
 * <p>链实例在构建时对中间件列表做防御性拷贝并排序，构建完成后不可变、线程安全。
 *
 * @author ydsz-team
 * @since 26.09.13
 */
@Slf4j
public class MiddlewareChainImpl implements MiddlewareChain {

  /** 按优先级排序的中间件列表（不可变） */
  private final List<AgentMiddleware> middlewares;

  /**
   * 构造中间件链。
   *
   * @param middlewares 中间件集合（允许为空，此时链无中间件生效）
   */
  public MiddlewareChainImpl(List<AgentMiddleware> middlewares) {
    if (middlewares == null || middlewares.isEmpty()) {
      this.middlewares = List.of();
    } else {
      List<AgentMiddleware> sorted = new ArrayList<>(middlewares);
      sorted.sort(Comparator.comparingInt(AgentMiddleware::getPriority));
      this.middlewares = List.copyOf(sorted);
    }
    if (!this.middlewares.isEmpty()) {
      log.info("[MiddlewareChain] 已注册 {} 个中间件: {}",
          this.middlewares.size(),
          this.middlewares.stream().map(AgentMiddleware::getName).toList());
    }
  }

  @Override
  public void executeAgentStart(MiddlewareContext context) {
    for (AgentMiddleware middleware : middlewares) {
      try {
        middleware.onAgentStart(context);
      } catch (MiddlewareException e) {
        log.warn("[Middleware] {} 中断 Agent 启动: {}", middleware.getName(), e.getUserMessage());
        throw e;
      } catch (Exception e) {
        log.error("[Middleware] {} onAgentStart 异常未拦截", middleware.getName(), e);
      }
    }
  }

  @Override
  public void executeSystemPrompt(MiddlewareContext context) {
    for (AgentMiddleware middleware : middlewares) {
      try {
        middleware.onSystemPrompt(context);
      } catch (MiddlewareException e) {
        log.warn("[Middleware] {} 中断系统Prompt构建: {}", middleware.getName(), e.getUserMessage());
        throw e;
      } catch (Exception e) {
        log.error("[Middleware] {} onSystemPrompt 异常未拦截", middleware.getName(), e);
      }
    }
  }

  @Override
  public void executeReasoning(MiddlewareContext context) {
    for (AgentMiddleware middleware : middlewares) {
      try {
        middleware.onReasoning(context);
      } catch (MiddlewareException e) {
        log.warn("[Middleware] {} 中断推理: {}", middleware.getName(), e.getUserMessage());
        throw e;
      } catch (Exception e) {
        log.error("[Middleware] {} onReasoning 异常未拦截", middleware.getName(), e);
      }
    }
  }

  @Override
  public ChatResponse executeModelCall(
      MiddlewareContext context, AgentMiddleware.ModelCallProceed finalCall) {
    return new OnionInvoker(middlewares, context, finalCall).invoke(0);
  }

  @Override
  public void executeObservation(MiddlewareContext context) {
    for (AgentMiddleware middleware : middlewares) {
      try {
        middleware.onObservation(context);
      } catch (MiddlewareException e) {
        log.warn("[Middleware] {} 中断观察阶段: {}", middleware.getName(), e.getUserMessage());
        throw e;
      } catch (Exception e) {
        log.error("[Middleware] {} onObservation 异常未拦截", middleware.getName(), e);
      }
    }
  }

  @Override
  public void executeAgentEnd(MiddlewareContext context) {
    for (AgentMiddleware middleware : middlewares) {
      try {
        middleware.onAgentEnd(context);
      } catch (Exception e) {
        // 结束阶段不抛出异常，避免影响返回结果
        log.error("[Middleware] {} onAgentEnd 异常", middleware.getName(), e);
      }
    }
  }

  /**
   * 洋葱模型递归调用器 — 按优先级升序排列中间件，链式触发 onModelCall。
   *
   * <p>第 i 个中间件的放行函数（proceed.execute()）会触发第 i+1 个中间件的 onModelCall，
   * 最内层调用 finalCall.execute() 执行实际 LLM 调用。
   */
  private static class OnionInvoker {

    private final List<AgentMiddleware> middlewares;
    private final MiddlewareContext context;
    private final AgentMiddleware.ModelCallProceed finalCall;

    OnionInvoker(
        List<AgentMiddleware> middlewares,
        MiddlewareContext context,
        AgentMiddleware.ModelCallProceed finalCall) {
      this.middlewares = middlewares;
      this.context = context;
      this.finalCall = finalCall;
    }

    /**
     * 递归执行第 index 个中间件的 onModelCall。
     *
     * @param index 当前中间件索引
     * @return LLM 响应
     */
    ChatResponse invoke(int index) {
      if (index >= middlewares.size()) {
        // 所有中间件已放行，执行实际调用
        ChatResponse result = finalCall.execute();
        // 将结果设置到上下文（如果外部需要读取）
        if (context.getLlmResponse() == null) {
          context.setLlmResponse(result);
        }
        return result;
      }
      AgentMiddleware current = middlewares.get(index);
      try {
        // 调用中间件钩子：如果放行 proceed.execute() 会触发 invoke(index+1)
        // 如果不放行（如缓存命中/限流拦截），中间件通过 context.setLlmResponse() 设置结果并抛出 MiddlewareException
        current.onModelCall(context, () -> invoke(index + 1));
        // 中间件未放行但通过 context 设置了响应（如缓存命中）
        return context.getLlmResponse();
      } catch (MiddlewareException e) {
        log.warn("[Middleware] {} 中断模型调用: {}", current.getName(), e.getUserMessage());
        throw e;
      }
    }
  }
}
