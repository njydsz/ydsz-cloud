package com.njydsz.message.server.service.canary;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.message.domain.dto.canary.CanaryUpsertDTO;
import com.njydsz.message.domain.entity.canary.MsgCanary;

/**
 * 灰度桶 Service
 *
 * <p>实现"按灰度键+桶值 hash"将流量按比例分配到实验组的能力,常用于:
 *
 * <ul>
 *   <li><b>新模板灰度</b>：同一 (canaryKey) 的少量用户先走新模板
 *   <li><b>新通道灰度</b>：少量用户先尝试新通道(如 PUSH)
 *   <li><b>AB 实验</b>：按 percentage 配置比例
 * </ul>
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>CRUD</b>：{@link #upsert} / {@link #page} / {@link #getByKey}
 *   <li><b>命中判断</b>：{@link #hit} — {@code hash(bucketValue) % 100 < percentage}
 *   <li><b>配置匹配</b>：{@link #matchConfig} — 一次 DB 查询返回命中配置(避免 hit + getByKey 双查)
 * </ul>
 *
 * <p><b>hash 算法：</b>使用 {@code String.hashCode()} 截断为正整数后取模,保证同一 (canaryKey, bucketValue) 组合
 * 始终落到相同的桶,避免用户在不同请求间被反复切换。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.domain.entity.canary.MsgCanary 灰度桶实体
 * @see CanaryReportService 灰度 A/B 报表服务
 */
public interface CanaryService {

  /**
   * 新增或更新灰度桶配置
   *
   * @param dto 灰度桶参数
   * @return 灰度桶实体
   */
  MsgCanary upsert(CanaryUpsertDTO dto);

  /**
   * 判断桶值是否命中灰度(按 percentage 计算 hash(bucketValue)%100 < percentage)
   *
   * @param canaryKey 灰度键
   * @param bucketValue 桶值(如接收人 / 单据 ID)
   * @return true 表示命中灰度
   */
  boolean hit(String canaryKey, String bucketValue);

  /**
   * 匹配灰度配置:命中则返回灰度桶实体(含 experimentTemplateCode/experimentChannel), 未命中或未配置返回 null。一次 DB 查询,避免 hit +
   * getByKey 双查。
   *
   * @param canaryKey 灰度键
   * @param bucketValue 桶值(如接收人 / 单据 ID)
   * @return 命中的灰度桶实体;未命中返回 null
   */
  MsgCanary matchConfig(String canaryKey, String bucketValue);

  /**
   * 按灰度键查询灰度桶配置
   *
   * @param canaryKey 灰度键
   * @return 灰度桶实体
   */
  MsgCanary getByKey(String canaryKey);

  /**
   * 分页查询灰度桶
   *
   * @param query 分页参数
   * @return 分页结果
   */
  Page<MsgCanary> page(PageQuery query);
}
