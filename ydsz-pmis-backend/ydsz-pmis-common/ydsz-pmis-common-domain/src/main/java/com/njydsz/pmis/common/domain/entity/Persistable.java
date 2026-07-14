ackage com.njydsz.pmis.common.domain.entity;

import java.io.Serializable;

/**
 * 可持久化实体标识接口（Spring Data 风格）
 *
 * <p>定义所有持久化实体的最小契约：具备主键 ID 并能判断新建/已存在状态。
 * 替代 {@code RootEntity} 中直接定义 getId/setId/isNew 的方式，
 * 使接口职责更单一、组合更灵活。
 *
 * <pre>
 * 使用方式：
 *   BaseIdEntity&lt;Long&gt;  →  implements Persistable&lt;Long&gt;
 *   自定义实体            →  implements Persistable&lt;String&gt;
 * </pre>
 *
 * @param <T> 主键 ID 类型
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public interface Persistable<T extends Serializable> extends Serializable {

    /**
     * 获取实体主键ID
     *
     * @return 主键ID，未持久化时可能为 null
     */
    T getId();

    /**
     * 设置实体主键ID
     *
     * @param id 主键ID
     */
    void setId(T id);

    /**
     * 判断实体是否为新建状态（尚未持久化）
     *
     * <p>判断逻辑：
     * <ul>
     *   <li>ID 为 null 时，视为新实体</li>
     *   <li>ID 为 Number 类型且值为 0 时，视为新实体（自增主键场景）</li>
     *   <li>ID 为 String 类型时，无法通过 isEmpty 判断新建状态（UUID 主键在构造时即赋值），
     *       默认返回 false，使用 UUID 主键的实体应覆写此方法</li>
     * </ul>
     *
     * <p><b>注意：</b>UUID 主键在构造时就已赋值，{@code isEmpty()} 永远返回 false，
     * 因此无法通过此默认实现正确判断 UUID 主键实体的新建状态。
     * 建议使用 UUID 主键的子类根据实际情况覆写此方法（例如结合 {@code @Version} 字段判断）。
     *
     * @return true 表示新实体（尚未持久化），false 表示已持久化
     */
    default boolean isNew() {
        T id = getId();
        if (id == null) {
            return true;
        }
        if (id instanceof Number) {
            return ((Number) id).longValue() == 0;
        }
        // UUID主键在构造时就已赋值，无法通过isEmpty判断
        // 对于String类型ID，如果是UUID格式则不应依赖isEmpty判断
        // 建议子类根据实际情况覆写此方法
        return false;
    }
}
