package com.njydsz.cronjob.domain.job;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 租户统计 MapReduce 参考实现（模板示例）。
 *
 * <p>展示 {@link MapReduceProcessor} 的标准用法：Root 任务按租户拆分子任务， 子任务独立统计，reduce 汇总所有子任务结果。
 *
 * <p><b>使用方式：</b>在任务配置中注册本类为 MapReduce 类型任务， 由 {@code MapTaskExecutor} 按分片调度执行：
 *
 * <ul>
 *   <li>Root 上下文：{@link #process(MapContext)} 中 {@code ctx.isRoot()==true}， 通过 {@code
 *       addSubTask(taskName, taskParams)} 声明子任务
 *   <li>子任务：{@code ctx.isRoot()==false}，各自执行统计并写入 {@code ctx.getResults()}
 *   <li>汇总：{@link #reduce(List, MapContext)} 聚合所有子任务结果
 * </ul>
 *
 * <p><b>业务场景：</b>按租户维度统计各租户任务执行量，供报表/配额分析使用。 实际业务中可按需改造（如按城市、按部门、按日期分片）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class TenantStatMapReduceJob implements MapReduceProcessor {

  /** 任务参数中租户列表的 Key（JSON 数组，如 ["t1","t2","t3"]） */
  public static final String PARAM_TENANT_IDS = "tenantIds";

  @Override
  public ProcessResult process(MapContext ctx) {
    if (ctx.isRoot()) {
      // Root 任务：按租户拆分子任务
      String[] tenantIds = parseTenantIds(ctx.getTaskParams());
      for (String tenantId : tenantIds) {
        ctx.addSubTask("statByTenant", tenantId);
      }
      return ProcessResult.success();
    }
    // 子任务：统计指定租户的执行量（示例：此处以 taskParams 作为结果占位）
    // 实际实现应调用 Mapper 统计，如：long count = mapper.countByTenant(ctx.getTaskParams());
    long count = 0L;
    ctx.getResults().put(ctx.getTaskParams(), count);
    return ProcessResult.success(String.valueOf(count));
  }

  @Override
  public ProcessResult reduce(List<MapContext> subContexts, MapContext rootContext) {
    // 汇总所有子任务结果
    Map<String, Object> stat = new HashMap<>(subContexts.size() * 2);
    for (MapContext sub : subContexts) {
      sub.getResults().forEach(stat::put);
    }
    return ProcessResult.success(stat.toString());
  }

  /**
   * 解析任务参数中的租户 ID 列表（容忍 null/空/非法 JSON，保守返回空数组）。
   *
   * @param paramsJson 任务参数 JSON（如 {@code {"tenantIds":["t1","t2"]}}）
   * @return 租户 ID 数组
   */
  private String[] parseTenantIds(String paramsJson) {
    if (paramsJson == null || paramsJson.isBlank()) {
      return new String[0];
    }
    // 简化解析：实际项目可注入 YdszJson 解析；此处仅做演示兜底
    String trimmed = paramsJson.trim();
    if (!trimmed.startsWith("[")) {
      return new String[0];
    }
    return trimmed.replaceAll("[\\[\\]\"\\s]", "").split(",");
  }
}
