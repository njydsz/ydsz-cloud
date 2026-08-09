package com.njydsz.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 系统变量创建/更新 DTO
 *
 * <p>对应 {@code ydsz_variable} 表的写入参数，承担「系统级键值对参数」的 CRUD 入参。
 * 与 {@link ConfigDTO} 的区别：{@code Config} 强绑定分组（{@code configGroup}），
 * {@code Variable} 是<b>全局</b>无分组的扁平结构，更适合「跨模块共享的环境变量」场景。
 *
 * <p><b>字段约束：</b>
 * <ul>
 *   <li>{@code variableKey} — 变量键，租户内唯一，最长 128 字符</li>
 *   <li>{@code variableValue} — 变量值（按 {@code valueType} 解析）</li>
 *   <li>{@code valueType} — 值类型：{@code STRING / NUMBER / BOOLEAN / JSON}</li>
 *   <li>{@code status} — 启用状态：{@code ENABLED / DISABLED}</li>
 * </ul>
 *
 * <p><b>唯一约束：</b>（{@code tenantId}, {@code variableKey}）二联唯一。
 *
 * <p><b>缓存策略：</b>读取时通过 {@code ydsz:variable:{key}} 缓存至 Redis；
 * 写入时 {@code @CacheEvict} 主动失效。常用于业务代码中
 * {@code @Value("${ydsz.variable.xxx}")} 占位符解析。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.system.domain.entity.Variable 系统变量实体
 */
@Data
@SuperBuilder
@NoArgsConstructor
@Schema(description = "系统变量创建/更新 DTO")
public class VariableDTO {

    @Schema(description = "主键 ID（更新时必填）")
    private String id;

    @NotBlank(message = "变量键不能为空")
    @Size(max = 128, message = "变量键长度不能超过128")
    @Schema(description = "变量键")
    private String variableKey;

    @Schema(description = "变量值")
    private String variableValue;

    @NotBlank(message = "值类型不能为空")
    @Schema(description = "值类型: STRING/NUMBER/BOOLEAN/JSON")
    private String valueType;

    @Schema(description = "变量说明")
    private String description;

    @Schema(description = "启用状态: ENABLED/DISABLED")
    private String status;
}
