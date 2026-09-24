package com.njydsz.agent.domain.config.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 混合检索配置属性。
 *
 * <p>绑定配置前缀 {@code ydzs.agent.hybrid-search}，控制是否启用网络搜索增强的知识检索，
 * 以及网络结果在最终结果集中的混合比例。默认不开启网络增强（webEnabled=false），
 * 网络结果占比（webResultRatio）默认 0.3，表示 30% 网络结果 + 70% 知识库向量检索结果。
 *
 * @author ydsz
 * @since 26.09.24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HybridSearchProperties {
  private static final double DEFAULT_WEB_RESULT_RATIO = 0.3;

  private boolean isWebEnabled = false;
  private double webResultRatio = DEFAULT_WEB_RESULT_RATIO;
}
