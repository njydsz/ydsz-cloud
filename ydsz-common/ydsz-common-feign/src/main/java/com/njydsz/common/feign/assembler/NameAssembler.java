package com.njydsz.common.feign.assembler;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 跨服务名称解析门面（Cross-Service Name Resolution Facade）。
 *
 * <p><b>背景</b>：业务模块的 VO 普遍包含外键 ID（如 created_by / assignee_id / dept_id），
 * 但展示给前端时需要富化为可读名称（如 createdByName / assigneeName / deptName）。
 * 若每个业务模块自己写 Feign 调用 + 缓存 + 降级，会产生大量重复代码且行为不一致。
 *
 * <p><b>职责</b>：本接口为所有业务模块提供统一的"ID → 名称"富化入口，由具体实现
 * （如 {@code UserInfoNameAssembler}）封装 Feign 调用、本地缓存、降级策略，
 * 业务方仅需声明"从哪个字段取 ID、写到哪个字段、属于哪种实体类型"。
 *
 * <p><b>使用示例</b>：
 * <pre>{@code
 * &#64;RequiredArgsConstructor
 * public class FlowInstanceServiceImpl {
 *     private final NameAssembler nameAssembler;
 *
 *     public FlowInstanceVO getById(String id) {
 *         FlowInstanceVO vo = ...;
 *         // 富化发起人姓名：从 initiatorId 取 ID，写回 initiatorName
 *         nameAssembler.enrichOne(vo, FlowInstanceVO::getInitiatorId,
 *                 FlowInstanceVO::setInitiatorName, NameType.USER);
 *         return vo;
 *     }
 *
 *     public Page<FlowInstanceVO> page(...) {
 *         Page<FlowInstanceVO> p = ...;
 *         // 批量富化：一次 Feign 调用解决整页数据
 *         nameAssembler.enrich(p.getRecords(),
 *                 FlowInstanceVO::getInitiatorId,
 *                 FlowInstanceVO::setInitiatorName, NameType.USER);
 *         return p;
 *     }
 * }
 * }</pre>
 *
 * <p><b>降级语义</b>：
 * <ul>
 *   <li>当 Feign 调用失败或返回空 Map 时，{@link #enrich} / {@link #enrichOne}
 *       会用 ID 字符串本身顶替 name 字段（避免前端显示空白），并记录 WARN 日志。</li>
 *   <li>当 ID 为 null / 空白时，对应字段保持原值不变。</li>
 * </ul>
 *
 * <p><b>性能语义</b>：
 * <ul>
 *   <li>{@link #batchResolveNames} 单次 Feign 往返拿全量映射。</li>
 *   <li>{@link #resolveName} 优先走本地缓存（默认 5 分钟 TTL），缓存未命中时聚合为批量调用。</li>
 *   <li>{@link #enrich} 内部自动收集所有 ID 后一次批量解析，避免 N+1 调用。</li>
 * </ul>
 *
 * <p><b>实现约束</b>：
 * <ul>
 *   <li>实现类必须线程安全（ConcurrentHashMap + TTL）。</li>
 *   <li>实现类必须 try-catch 包裹 Feign 调用，禁止抛异常阻断业务主流程。</li>
 *   <li>实现类应通过 {@code @ConditionalOnMissingBean(NameAssembler.class)} 注册，
 *       允许业务方覆盖默认实现。</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface NameAssembler {

    /**
     * 批量解析 ID → 名称映射（一次 Feign 往返）。
     *
     * <p>调用方应优先使用此方法聚合多个 ID 后一次性解析，避免 N+1 Feign 调用。
     * 结果不入本地缓存（避免大批量数据污染缓存）。
     *
     * @param type 名称类型
     * @param ids  ID 集合（允许 null / 空，返回空 Map）
     * @return id → name 映射；未命中的 id 不出现在 Map 中；调用失败返回空 Map
     */
    Map<String, String> batchResolveNames(NameType type, Collection<String> ids);

    /**
     * 解析单个 ID 的名称（走本地缓存）。
     *
     * <p>缓存命中时直接返回；未命中时发起一次 Feign 调用（仅查询单个 ID），
     * 并将结果回填缓存。调用失败返回 null。
     *
     * @param type 名称类型
     * @param id   ID（null / 空白返回 null）
     * @return 名称；未命中或调用失败返回 null
     */
    String resolveName(NameType type, String id);

    /**
     * 批量富化集合中对象的外键 name 字段。
     *
     * <p>内部自动收集所有非空 ID，一次 Feign 调用拿全量映射，再回写到每个对象。
     * 当 Feign 调用失败或 ID 未命中时，使用 ID 字符串本身顶替 name 字段（兜底）。
     *
     * @param objects    待富化的对象集合（允许 null / 空，直接返回）
     * @param idGetter   从对象中提取 ID 的函数（返回 null / 空白时跳过该对象）
     * @param nameSetter 将名称写回对象的函数
     * @param type       名称类型
     * @param <T>        对象类型
     */
    <T> void enrich(Collection<T> objects,
                    Function<T, String> idGetter,
                    BiConsumer<T, String> nameSetter,
                    NameType type);

    /**
     * 富化单个对象的外键 name 字段（走本地缓存）。
     *
     * <p>当 ID 为 null / 空白时跳过（保持原值）。
     * 当 Feign 调用失败或 ID 未命中时，使用 ID 字符串本身顶替 name 字段（兜底）。
     *
     * @param obj        待富化的对象（null 时直接返回）
     * @param idGetter   从对象中提取 ID 的函数
     * @param nameSetter 将名称写回对象的函数
     * @param type       名称类型
     * @param <T>        对象类型
     */
    <T> void enrichOne(T obj,
                       Function<T, String> idGetter,
                       BiConsumer<T, String> nameSetter,
                       NameType type);
}
