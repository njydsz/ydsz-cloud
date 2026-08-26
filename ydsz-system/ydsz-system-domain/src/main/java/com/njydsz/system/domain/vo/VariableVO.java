package com.njydsz.system.domain.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 系统变量 VO（视图对象）
 *
 * <p>对应 {@code ydsz_sys_variable} 表的展示视图，是「系统变量中心」列表 / 详情接口的响应载体。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code variableKey} — 变量键，租户内唯一，业务存储 / 接口传输主键
 *   <li>{@code variableValue} — 变量值，序列化时按 {@code valueType} 转换： {@code STRING} 原样输出；{@code NUMBER /
 *       BOOLEAN / JSON} 解析为对应类型
 * </ul>
 *
 * <p><b>与 Config 的区别：</b>{@code Variable} 是<b>全局无分组</b>的扁平结构， 适合「跨模块共享的环境变量」场景；{@code ConfigVO}
 * 是<b>按分组管理</b>的结构， 适合「按业务域配置」场景。
 *
 * <p><b>使用场景：</b>
 *
 * <ul>
 *   <li>系统变量中心列表 / 详情 / 编辑回显
 *   <li>运行时动态配置（如外部 API endpoint、限流阈值）
 *   <li>Spring 占位符解析：{@code @Value("${ydsz.variable.xxx}")}
 * </ul>
 *
 * <p><b>缓存策略：</b>读取通过 {@code ydsz:variable:{key}} 缓存至 Redis； 写入时 {@code @CacheEvict} 主动失效。
 *
 * <p><b>注意：</b>本类为视图对象，不包含输入校验逻辑。输入校验由 {@link
 * com.njydsz.system.domain.dto.VariableDTO} 负责。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.infra.entity.Variable 系统变量实体
 * @see ConfigVO 系统配置 VO（按分组的同类结构）
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class VariableVO {

  private String id;

  private String variableKey;

  private String variableValue;

  private String valueType;

  private String description;

  private String status;
}
