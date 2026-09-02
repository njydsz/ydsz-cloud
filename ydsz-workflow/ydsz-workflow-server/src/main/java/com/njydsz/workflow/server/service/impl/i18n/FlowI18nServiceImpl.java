package com.njydsz.workflow.server.service.impl.i18n.FlowI18nServiceImpl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.workflow.server.service.FlowI18nService;

/**
 * 工作流国际化（i18n）服务实现
 *
 * <p>对 {@link FlowI18nService} 接口的完整实现，是工作流引擎的<b>国际化</b>能力。 为工作流各类枚举（任务状态 / 节点类型 / 审批结果 /
 * 流程结果等）提供中英文翻译， 支撑大厂 B 端工作流「多语言办公」场景（如跨国企业 / 海外子公司）。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>枚举翻译（{@link #getMessage}）</b>：根据 {@code enumType + enumName + locale} 返回本地化的枚举描述（如
 *       {@code FlowTaskStatus.PENDING} 在 {@code zh_CN} 下返回「待办」， 在 {@code en_US} 下返回「Pending」）
 *   <li><b>语言支持</b>：当前支持 {@code zh_CN}（简体中文）/ {@code en_US}（美国英语）， 后续可扩展到 {@code zh_TW} / {@code
 *       ja_JP} / {@code ko_KR}
 *   <li><b>批量翻译（{@link #getBatchMessages}）</b>：批量获取多个枚举的翻译，避免 N+1 查询
 *   <li><b>动态注册</b>：支持运行时通过 {@link #register} 注册新的翻译项， 无需重启即可生效
 * </ul>
 *
 * <p><b>设计要点（采用内存 Map 存储）：</b>
 *
 * <ul>
 *   <li><b>避免引入额外复杂度</b>：采用静态 {@code LinkedHashMap} 存储消息资源， 无需引入 {@code messages.properties} 文件管理 /
 *       资源加载器
 *   <li><b>启动加载</b>：所有翻译项在类加载时（{@code static} 块）一次性注册， 后续无锁查询
 *   <li><b>热加载</b>：通过 {@code @NacosValue}（{@code +}）可实现热加载， 但当前版本仅支持类加载时初始化
 *   <li><b>回退策略</b>：翻译项不存在时回退到 {@code enumName} 本身， 避免前端展示为空
 *   <li><b>大小写不敏感</b>：{@code locale} 解析时自动转小写，避免 {@code zh_CN} vs {@code zh_cn} 不匹配
 * </ul>
 *
 * <p><b>存储结构：</b>
 *
 * <pre>
 *   MESSAGE_RESOURCE: Map&lt;enumType, Map&lt;enumName, Map&lt;locale, description&gt;&gt;&gt;
 *   例如:
 *     "FlowTaskStatus" → {
 *       "PENDING"  → { "zh_CN" → "待办", "en_US" → "Pending" },
 *       "COMPLETED"→ { "zh_CN" → "已通过", "en_US" → "Completed" },
 *       ...
 *     }
 * </pre>
 *
 * <p><b>典型使用：</b>
 *
 * <pre>{@code
 * // 单个翻译
 * String text = i18nService.getMessage("FlowTaskStatus", "PENDING", "en_US");
 * // "Pending"
 *
 * // 批量翻译（前端一次性获取所有枚举）
 * Map&lt;String, String&gt; messages = i18nService.getBatchMessages("FlowTaskStatus", "en_US");
 * }</pre>
 *
 * <p><b>未来扩展：</b>如需支持更多语言 / 从 Nacos 加载翻译项 / 支持占位符（如 {@code "你有 {0} 个待办"}）， 可在本类基础上扩展，无需修改接口契约。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see FlowI18nService 接口定义
 */
@Slf4j
@Service
public class FlowI18nServiceImpl implements FlowI18nService {

  /** i18n 消息资源：enumType -> enumName -> locale -> description */
  private static final Map<String, Map<String, Map<String, String>>> MESSAGE_RESOURCE =
      new LinkedHashMap<>(16);