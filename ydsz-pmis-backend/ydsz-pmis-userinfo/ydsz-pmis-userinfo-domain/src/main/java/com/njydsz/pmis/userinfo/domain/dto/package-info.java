/**
 * userinfo 模块数据传输对象（DTO）包。
 *
 * <p>用于 Controller 与 Service 之间的入参/出参传递，以及 Feign 远程调用的载荷定义。
 * DTO 与 {@code entity} 中的持久化对象解耦，避免直接暴露数据库表结构。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>用户域：UserCreateDTO、UserUpdateDTO、UserQueryDTO、PasswordChangeDTO、PasswordResetDTO。</li>
 *   <li>认证域：LoginRequest、LoginDTO、LoginResult、LoginResultVO、LoginContextDTO、TwoFactorBindResult、CaptchaVO。</li>
 *   <li>角色/权限域：RoleFormDTO、RoleQueryDTO、PermissionFormDTO。</li>
 *   <li>组织架构域：DepartmentFormDTO、DepartmentQueryDTO、EmployeeTagCreateDTO。</li>
 *   <li>资源域：ResourcePoolCreateDTO、ResourceAssignmentCreateDTO、BenchRecordCreateDTO。</li>
 *   <li>考勤域：AttendanceCreateDTO、LeaveCreateDTO、OvertimeCreateDTO。</li>
 *   <li>运维域：PasswordScanResultDTO - 密码健康度扫描结果。</li>
 *   <li>Feign 客户端：UserFeignClient - 供其他微服务远程查询用户视图的接口定义。</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>入参校验前置：使用 Jakarta Bean Validation（{@code @NotBlank}、{@code @Size} 等）做参数级校验，
 *       错误码统一走国际化资源占位符（如 {@code {validation.user.msg_xxx}}）。</li>
 *   <li>Swagger 注解完备：每个字段都附带 {@code @Schema} 描述，便于生成 OpenAPI 文档。</li>
 *   <li>远程调用载荷轻量：Feign 客户端只暴露必要的最小字段集，不传递内部业务实体。</li>
 *   <li>DTO 与 VO 严格区分：DTO 用于内部流转，VO 用于对外响应（位于 {@code vo} 包）。</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>新增 DTO 必须使用 Lombok {@code @Data}，并显式标注 {@code @Schema(description = ...)}。</li>
 *   <li>DTO 不允许引用 Service/Mapper，避免反向依赖。</li>
 *   <li>跨服务调用的 Feign 客户端需在该包内显式定义接口，与内部 Service 接口解耦。</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.userinfo.domain.dto;
