ackage com.njydsz.pmis.common.domain.tree;

import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Data;

/**
 * 树懒加载配置
 *
 * <p>通过 Spring 配置属性管理懒加载树的相关配置项。
 *
 * <p><b>配置项（application.yml）：</b>
 * <pre>{@code
 * ydsz:
 *   tree:
 *     lazy:
 *       max-lazy-depth: 10
 *       batch-size: 100
 *       enabled: false
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ydsz.tree.lazy")
public class TreeLazyConfig {

    /** 最大懒加载深度 */
    @Min(1)
    private int maxLazyDepth = 10;

    /** 懒加载批次大小 */
    @Min(1)
    private int batchSize = 100;

    /** 是否启用懒加载 */
    private boolean enabled = false;
}
