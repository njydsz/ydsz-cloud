package com.njydsz.common.feign.assembler;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * ID → 名称富化组件接口。
 *
 * <p>跨服务解析业务对象 ID 为用户可读名称，用于 VO 场景中的 createdBy/assignee 等字段富化。 该接口由具体业务域实现（如 userinfo 模块通过
 * OrgQueryClient 实现）， common-feign 提供 {@link NoOpNameAssembler} 兜底。
 *
 * <p>使用方通过 {@code @Autowired} 注入后调用：
 *
 * <pre>{@code
 * nameAssembler.enrich(voList,
 *         MyVO::getUserId,
 *         MyVO::setUserName,
 *         NameType.USER);
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface NameAssembler {

  /**
   * 批量解析 ID → 名称映射（不经过缓存，适用于批量更新场景）。
   *
   * @param type 名称类型（用户/部门/角色等）
   * @param ids ID 集合
   * @return ID → 名称映射（未解析到的 ID 不包含在结果中）
   */
  Map<String, String> batchResolveNames(NameType type, Collection<String> ids);

  /**
   * 解析单个 ID → 名称（带缓存）。
   *
   * @param type 名称类型
   * @param id 目标 ID
   * @return 名称（未解析到时返回 null）
   */
  String resolveName(NameType type, String id);

  /**
   * 批量富化集合中对象的名称字段。
   *
   * <p>自动收集 ID → 批量调用 → 回写名称，内置 N+1 防护和降级逻辑。
   *
   * @param objects 待富化对象集合
   * @param idGetter ID 提取函数
   * @param nameSetter 名称设置函数
   * @param type 实体类型
   * @param <T> 对象类型
   */
  <T> void enrich(
      Collection<T> objects,
      Function<T, String> idGetter,
      BiConsumer<T, String> nameSetter,
      NameType type);

  /**
   * 富化单个对象的名称字段。
   *
   * @param obj 待富化对象
   * @param idGetter ID 提取函数
   * @param nameSetter 名称设置函数
   * @param type 实体类型
   * @param <T> 对象类型
   */
  <T> void enrichOne(
      T obj, Function<T, String> idGetter, BiConsumer<T, String> nameSetter, NameType type);
}
