package com.njydsz.common.util.collection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.njydsz.common.util.bean.BeanMapper;

/**
 * Map 工具类
 *
 * <p>聚焦于 JSON Map 解析场景下的类型安全读取与归一化，提供 null 安全的取值方法。 典型用途：JSON 反序列化后得到 {@code Map<String, Object>} 或
 * {@code Map<?, ?>}， 调用本类方法按 key 安全取出 String / Integer / Long / Boolean / Map / List 值。
 *
 * <p><b>主要功能：</b>
 *
 * <ul>
 *   <li>判空检查：isEmpty / isNotEmpty（null 安全）
 *   <li>类型安全取值：getString / getInteger / getLong / getBoolean / getMap / getList
 *   <li>JSON Map 归一化：toStringObjectMap / safeCastMap / safeCastList
 *   <li>嵌套 JSON 解析：getListOfMaps / getMapFromList
 *   <li>Map 转 Bean：toBean / toBeanOrRecord（委托 {@link BeanMapper}）
 *   <li>命名转换：snakeToCamel / camelToSnake
 * </ul>
 *
 * <p><b>不提供的能力（直接使用 JDK / Stream API）：</b>
 *
 * <ul>
 *   <li>Map 创建 → {@code new HashMap<>(16)} / {@code new LinkedHashMap<>(16)}