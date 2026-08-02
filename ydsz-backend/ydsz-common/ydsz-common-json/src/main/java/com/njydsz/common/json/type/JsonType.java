package com.njydsz.common.json.type;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * JSON 类型引用（用于泛型反序列化）
 *
 * <p>提供类型安全的泛型反序列化支持，解决 Java 泛型类型擦除问题。</p>
 *
 * <p><b>问题背景：</b></p>
 * <ul>
 *   <li>Java 泛型使用类型擦除，运行时无法获取泛型具体类型</li>
 *   <li>例如：List&lt;User&gt; 在运行时只能获取到 List</li>
 *   <li>传统方式需要强制类型转换，不安全</li>
 * </ul>
 *
 * <p><b>解决方案：</b></p>
 * <ul>
 *   <li>通过匿名内部类 + 父类类型参数获取泛型类型</li>
 *   <li>反射获取子类的泛型父类声明</li>
 *   <li>实现 Comparable 接口支持排序</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * // 传统方式（不安全）
 * List&lt;User&gt; users = (List&lt;User&gt;) YdszJson.toObject(json, List.class);
 *
 * // 使用 JsonType（类型安全）
 * List&lt;User&gt; users = YdszJson.toObject(json, new JsonType&lt;List&lt;User&gt;&gt;() {});
 *
 * // Map 泛型
 * Map&lt;String, User&gt; map = YdszJson.toObject(json, new JsonType&lt;Map&lt;String, User&gt;&gt;() {});
 *
 * // 嵌套泛型
 * List&lt;Map&lt;String, List&lt;User&gt;&gt;&gt; complex = YdszJson.toObject(json, new JsonType&lt;List&lt;Map&lt;String, List&lt;User&gt;&gt;&gt;&gt;() {});
 * </pre>
 *
 * <p><b>实现原理：</b></p>
 * <ol>
 *   <li>创建匿名内部类继承 JsonType</li>
 *   <li>获取子类的 genericSuperclass（即 ParameterizedType）</li>
 *   <li>提取 actualTypeArguments[0] 获取泛型类型</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see Type
 * @see ParameterizedType
 */
public abstract class JsonType<T> implements Comparable<JsonType<T>> {

    /** 泛型类型 */
    protected final Type type;

    /**
     * 构造函数
     *
     * <p>通过反射获取泛型父类的类型参数。</p>
     *
     * <p>示例：new JsonType&lt;List&lt;User&gt;&gt;() {}</p>
     * <p>则 type = List&lt;User&gt;</p>
     */
    protected JsonType() {
        Class<?> superClass = getClass();
        Type type = getClass().getGenericSuperclass();

        while (superClass != null && superClass != Object.class) {
            if (type instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) type;
                Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();

                if (actualTypeArguments.length > 0) {
                    Type firstArg = actualTypeArguments[0];
                    if (firstArg instanceof Class) {
                        if (JsonType.class.equals(firstArg)) {
                            superClass = superClass.getSuperclass();
                            type = superClass.getGenericSuperclass();
                            continue;
                        }
                    }
                    this.type = firstArg;
                    return;
                }
            }
            superClass = superClass.getSuperclass();
            type = superClass.getGenericSuperclass();
        }

        throw new IllegalArgumentException(
            "JsonType must be created with an actual type parameter. " +
            "Example: new JsonType<List<User>>() {}"
        );
    }

    /**
     * 获取泛型类型
     *
     * @return 泛型类型
     */
    public Type getType() {
        return type;
    }

    /**
     * 获取此类型引用的原始类型（raw class）。
     *
     * <p>对于 {@code JsonType<List<User>>}，返回 {@code List.class}。
     * 对于 {@code JsonType<String>}，返回 {@code String.class}。</p>
     *
     * @return 原始类型，无法确定时返回 {@code Object.class}
     * @since 1.0.0
     */
    public Class<?> getRawClass() {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> clazz) {
            return clazz;
        }
        return Object.class;
    }

    /**
     * 将反序列化结果安全转换为目标类型。
     *
     * <p>使用 {@link Class#cast(Object)} 执行运行时类型检查（checked cast），
     * 而非编译期 unchecked cast。原始类型（raw type）的匹配由运行时检查保证，
     * 泛型参数的精确匹配由反序列化过程使用正确的 {@link Type} 信息保证。</p>
     *
     * @param value 反序列化结果
     * @return 类型转换后的值，{@code null} 返回 {@code null}
     * @throws ClassCastException 如果值的运行时类型与声明的原始类型不匹配
     * @since 1.0.0
     */
    public T cast(Object value) {
        if (value == null) {
            return null;
        }
        Class<?> rawClass = getRawClass();
        if (!rawClass.isInstance(value)) {
            throw new ClassCastException(
                "Expected " + rawClass.getName() + " but got " + value.getClass().getName());
        }
        return castViaClass(value, rawClass);
    }

    /**
     * 通过 {@link Class#cast(Object)} 执行 checked cast。
     *
     * <p>泛型方法签名确保 {@code Class.cast()} 的返回类型与调用者的类型参数匹配。
     * 当传入 {@code Class<?>} 时，编译器将 {@code U} 推断为通配符的捕获（capture），
     * 捕获类型可安全赋值给外层类型参数 {@code T}，无需 unchecked cast。</p>
     *
     * @param value 要转换的值
     * @param clazz 目标类型
     * @param <U> 目标类型参数
     * @return 转换后的值
     */
    private <U> U castViaClass(Object value, Class<U> clazz) {
        return clazz.cast(value);
    }

    @Override
    public int compareTo(JsonType<T> other) {
        if (this == other) {
            return 0;
        }
        if (other == null) {
            return 1;
        }
        return this.getType().toString().compareTo(other.getType().toString());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof JsonType)) {
            return false;
        }
        JsonType<?> other = (JsonType<?>) obj;
        return this.getType().equals(other.getType());
    }

    @Override
    public int hashCode() {
        return getType().hashCode();
    }

    @Override
    public String toString() {
        return "JsonType<" + type.getTypeName() + ">";
    }
}
