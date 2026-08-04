package com.njydsz.common.domain.contract;

import java.io.Serializable;

/**
 * VO (View Object) 标记接口。
 *
 * <p>用于标识视图对象，通常用于 API 响应数据封装。
 * 零方法开销，仅做编译期类型约束和文档化作用。</p>
 *
 * <p>与 {@link DTO} 的区别：
 * <ul>
 *   <li>DTO：数据传递，关注传输效率（可能扁平化、脱敏）</li>
 *   <li>VO：视图展示，关注展示效果（可能聚合、格式化）</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * public class UserVO implements VO {
 *     private String id;
 *     private String name;
 *     // ...
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.5.0
 * @see DTO
 * @see Command
 * @see Query
 */
public interface VO extends Serializable {
}
