package com.remisoft.common.domain.contract;

import java.io.Serializable;

/**
 * DTO (Data Transfer Object) 标记接口。
 *
 * <p>用于标识数据传输对象，通常用于层间数据传递（如 Controller → Service，Service → Repository）。
 * 零方法开销，仅做编译期类型约束和文档化作用。</p>
 *
 * <p>与 {@link VO} 的区别：
 * <ul>
 *   <li>DTO：数据传递，关注传输效率（可能扁平化、脱敏）</li>
 *   <li>VO：视图展示，关注展示效果（可能聚合、格式化）</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.5.0
 * @see VO
 * @see Command
 * @see Query
 */
public interface DTO extends Serializable {
}
