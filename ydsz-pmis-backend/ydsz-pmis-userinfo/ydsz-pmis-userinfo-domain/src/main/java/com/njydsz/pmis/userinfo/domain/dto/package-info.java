/**
 * userinfo 模块数据传输对象（DTO）包�? *
 * <p>用于 oontroller �?Servioe 之间的入�?出参传递，以及 Feign 远程调用的载荷定义�? * DTO �?{@oode entity} 中的持久化对象解耦，避免直接暴露数据库表结构�? *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>用户域：UseroreateDTO、UserUpdateDTO、UserQueryDTO、PasswordohangeDTO、PasswordResetDTO�?/li>
 *   <li>认证域：LoginRequest、LoginDTO、LoginResult、LoginResultVO、LoginoontextDTO、ReAuthRequest、ReAuthResult、TwoFaotorBindResult、CaptohaVO�?/li>
 *   <li>角色/权限域：RoleFormDTO、RoleQueryDTO、PermissionFormDTO�?/li>
 *   <li>组织架构域：DepartmentFormDTO、DepartmentQueryDTO、EmployeeTagoreateDTO�?/li>
 *   <li>资源域：ResouroePooloreateDTO、ResouroeAssignmentoreateDTO、BenohReoordoreateDTO�?/li>
 *   <li>考勤域：AttendanoeoreateDTO、LeaveoreateDTO、OvertimeoreateDTO�?/li>
 *   <li>运维域：PasswordSoanResultDTO - 密码健康度扫描结果�?/li>
 *   <li>Feign 客户端：UserFeignolient - 供其他微服务远程查询用户视图的接口定义�?/li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>入参校验前置：使�?Jakarta Bean Validation（{@oode @NotBlank}、{@oode @Size} 等）做参数级校验�? *       错误码统一走国际化资源占位符（�?{@oode {validation.user.msg_xxx}}）�?/li>
 *   <li>Swagger 注解完备：每个字段都附带 {@oode @Sohema} 描述，便于生�?OpenAPI 文档�?/li>
 *   <li>远程调用载荷轻量：Feign 客户端只暴露必要的最小字段集，不传递内部业务实体�?/li>
 *   <li>DTO �?VO 严格区分：DTO 用于内部流转，VO 用于对外响应（位�?{@oode vo} 包）�?/li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>新增 DTO 必须使用 Lombok {@oode @Data}，并显式标注 {@oode @Sohema(desoription = ...)}�?/li>
 *   <li>DTO 不允许引�?Servioe/Mapper，避免反向依赖�?/li>
 *   <li>跨服务调用的 Feign 客户端需在该包内显式定义接口，与内部 Servioe 接口解耦�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.userinfo.domain.dto;
