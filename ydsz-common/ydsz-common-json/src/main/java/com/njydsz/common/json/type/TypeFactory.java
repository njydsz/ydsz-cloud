package com.njydsz.common.json.type;

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Objects;

import com.njydsz.common.json.util.BoundedLruCache;

/**
 * 类型工厂（参考 Jackson 的 TypeFactory）
 *
 * <p>用于构造和管理复杂的 Java 类型。
 *
 * <p><b>主要功能：</b>
 *
 * <ul>
 *   <li>构造泛型类型
 *   <li>缓存已构造的类型
 *   <li>类型解析和转换
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>
 * // 构造 List&lt;User&gt; 类型
 * Type listType = TypeFactory.getInstance()
 *     .constructCollectionType(List.class, User.class);
 *
 * // 构造 Map&lt;String, User&gt; 类型
 * Type mapType = TypeFactory.getInstance()
 *     .constructMapType(Map.class, String.class, User.class);
 *
 * // 反序列化
 * List&lt;User&gt; users = YdszJson.fromJson(json, listType);
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class TypeFactory {

  /** 单例实例 */
  private static final TypeFactory INSTANCE = new TypeFactory();

  /**
   * 类型缓存。
   *
   * <p>P0-4：改为有界 LRU（容量 1024）。键为类型名字符串组合，在动态代理、 脚本引擎等运行期生成类型场景下可能无界增长，与模块"全部缓存有界"的
   * 内存安全策略保持一致。类型对象不可变，淘汰后重建无正确性影响。
   */
  private final BoundedLruCache<String, Type> typeCache = new BoundedLruCache<>(1024);

  private TypeFactory() {}

  /**
   * 获取 TypeFactory 实例
   *
   * @return TypeFactory 实例
   */
  public static TypeFactory getInstance() {
    return INSTANCE;
  }

  /**
   * 构造集合类型
   *
   * @param collectionClass 集合类（List、Set 等）
   * @param elementClass 元素类型
   * @return 参数化类型
   */
  public Type constructCollectionType(Class<?> collectionClass, Class<?> elementClass) {
    String key = collectionClass.getName() + "<" + elementClass.getName() + ">";
    return typeCache.computeIfAbsent(
        key,
        k -> {
          return new ParameterizedTypeImpl(null, collectionClass, elementClass);
        });
  }

  /**
   * 构造 Map 类型
   *
   * @param mapClass Map 类
   * @param keyClass 键类型
   * @param valueClass 值类型
   * @return 参数化类型
   */
  public Type constructMapType(Class<?> mapClass, Class<?> keyClass, Class<?> valueClass) {
    String key = mapClass.getName() + "<" + keyClass.getName() + "," + valueClass.getName() + ">";
    return typeCache.computeIfAbsent(
        key,
        k -> {
          return new ParameterizedTypeImpl(null, mapClass, keyClass, valueClass);
        });
  }

  /**
   * 构造数组类型
   *
   * @param elementClass 元素类型
   * @return 数组类型
   */
  public Type constructArrayType(Class<?> elementClass) {
    return Array.newInstance(elementClass, 0).getClass();
  }

  /** 清除类型缓存 */
  public void clearCache() {
    typeCache.clear();
  }

  /**
   * 获取缓存大小
   *
   * @return 缓存的类型数量
   */
  public int getCacheSize() {
    return typeCache.size();
  }

  /** 参数化类型实现 */
  private static class ParameterizedTypeImpl implements ParameterizedType {
    private final Type[] ownerType;
    private final Type rawType;
    private final Type[] actualTypeArguments;

    ParameterizedTypeImpl(Type ownerType, Type rawType, Type... actualTypeArguments) {
      this.ownerType = ownerType != null ? new Type[] {ownerType} : null;
      this.rawType = rawType;
      this.actualTypeArguments = actualTypeArguments;
    }

    @Override
    public Type[] getActualTypeArguments() {
      return actualTypeArguments.clone();
    }

    @Override
    public Type getRawType() {
      return rawType;
    }

    @Override
    public Type getOwnerType() {
      return ownerType != null && ownerType.length > 0 ? ownerType[0] : null;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ParameterizedType)) {
        return false;
      }
      ParameterizedType that = (ParameterizedType) o;
      return Objects.equals(rawType, that.getRawType())
          && Arrays.equals(actualTypeArguments, that.getActualTypeArguments());
    }

    @Override
    public int hashCode() {
      return Objects.hash(rawType, Arrays.hashCode(actualTypeArguments));
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      if (rawType instanceof Class) {
        sb.append(((Class<?>) rawType).getName());
      } else {
        sb.append(rawType.getTypeName());
      }
      if (actualTypeArguments.length > 0) {
        sb.append("<");
        for (int i = 0; i < actualTypeArguments.length; i++) {
          if (i > 0) {
            sb.append(",");
          }
          sb.append(actualTypeArguments[i].getTypeName());
        }
        sb.append(">");
      }
      return sb.toString();
    }
  }
}
