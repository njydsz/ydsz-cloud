/**
 * userinfo 模块对外响应视图对象（VO）包。
 *
 * <p>作为 Controller 响应体的统一载体，VO 在 DO 基础上剥离敏感字段（password、salt 等）并
 * 嵌入展示型字段（部门名称、岗位名称、职级名称、角色/权限编码集合）。所有 VO 字段均经
 * {@code @Sensitive} 注解做脱敏处理，序列化时使用 {@code @JsonInclude(NON_NULL)} 抑制空值，
 * 与前端约定 {@code null} 字段不出现。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>UserVO - 用户信息视图：包含用户基本信息、所属部门/岗位/职级、角色与权限编码集合、
 *       邮箱/手机号/登录 IP 脱敏。</li>
 *   <li>MenuTreeVO - 菜单树节点：与前端 vue-router 兼容的最小菜单结构，承载子节点 {@code children} 引用。</li>
 *   <li>DepartmentTreeVO - 部门树节点：内嵌 {@code DepartmentDO}，并以 {@code children} 承载子部门列表。</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>对外字段最小化：仅暴露前端需要展示的字段，DO 中的运维字段（创建人/修改人、删除标记等）禁止出现。</li>
 *   <li>脱敏注解必须显式标注：手机号、邮箱、IP 等敏感字段必须使用 {@code @Sensitive(strategy = ...)}。</li>
 *   <li>树形 VO 自描述：树形 VO 应内嵌 {@code List<XXXVO> children}，初始化为空集合避免空指针。</li>
 *   <li>实现 Serializable：所有 VO 实现 {@code java.io.Serializable}，便于跨 JVM 传递与缓存。</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>VO 与 DTO 严格区分：DTO 用于入参/远程调用，VO 仅用于响应（参见 H13.1/H13.2 修复）。</li>
 *   <li>新增 VO 字段时请同步更新前端 TypeScript 类型定义与 OpenAPI 文档。</li>
 *   <li>树形 VO 建议提供 {@code of(...)} 静态工厂方法，便于从 DO/实体快速构建。</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.userinfo.domain.vo;
