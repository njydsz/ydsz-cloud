package com.njydsz.common.netty.codec;

import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;

import com.njydsz.common.json.YdszJson;

/**
 * JSON 编解码工具类 — 提供静态方法在 Pipeline 之外进行 JSON 序列化/反序列化。
 *
 * <p>适用于：
 *
 * <ul>
 *   <li>在非 Handler 线程中序列化/反序列化消息</li>
 *   <li>单元测试</li>
 *   <li>日志/调试输出</li>
 * </ul>
 *
 * <p>Pipeline 内编码请用 {@link JsonMessageEncoder}，解码请用 {@link JsonMessageDecoder}。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class JsonCodecUtil {

  private JsonCodecUtil() {
    throw new UnsupportedOperationException("工具类不可实例化");
  }

  /**
   * 将业务对象序列化为 JSON 字节数组。
   *
   * @param msg 业务对象
   * @return UTF-8 编码的 JSON 字节数组
   */
  public static byte[] encodeToBytes(Object msg) {
    String json = YdszJson.toJson(msg);
    return json.getBytes(StandardCharsets.UTF_8);
  }

  /**
   * 将业务对象序列化为 JSON 字符串。
   *
   * @param msg 业务对象
   * @return JSON 字符串
   */
  public static String encodeToString(Object msg) {
    return YdszJson.toJson(msg);
  }

  /**
   * 将 ByteBuf 反序列化为业务对象。
   *
   * @param buf ByteBuf（UTF-8 编码的 JSON 数据）
   * @param clazz 业务对象类型
   * @param <T> 业务对象类型
   * @return 反序列化后的对象
   */
  public static <T> T decode(ByteBuf buf, Class<T> clazz) {
    String json = buf.toString(StandardCharsets.UTF_8);
    return YdszJson.fromJson(json, clazz);
  }

  /**
   * 将字节数组反序列化为业务对象。
   *
   * @param bytes UTF-8 编码的 JSON 字节数组
   * @param clazz 业务对象类型
   * @param <T> 业务对象类型
   * @return 反序列化后的对象
   */
  public static <T> T decode(byte[] bytes, Class<T> clazz) {
    return YdszJson.fromJsonBytes(bytes, clazz);
  }

  /**
   * 将 JSON 字符串反序列化为业务对象。
   *
   * @param json JSON 字符串
   * @param clazz 业务对象类型
   * @param <T> 业务对象类型
   * @return 反序列化后的对象
   */
  public static <T> T decode(String json, Class<T> clazz) {
    return YdszJson.fromJson(json, clazz);
  }
}
