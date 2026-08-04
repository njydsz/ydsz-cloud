package com.njydsz.common.domain.contract;

import java.io.Serializable;

/**
 * 命令对象标记接口（CQRS 写操作入参）。
 *
 * <p>用于标识 CQRS 架构中的 Command 对象，表示一次写操作的入参。
 * 零方法开销，仅做编译期类型约束和文档化作用。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * public class CreateUserCommand implements Command {
 *     private String name;
 *     // ...
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.5.0
 * @see Query
 * @see DTO
 * @see VO
 */
public interface Command extends Serializable {
}
