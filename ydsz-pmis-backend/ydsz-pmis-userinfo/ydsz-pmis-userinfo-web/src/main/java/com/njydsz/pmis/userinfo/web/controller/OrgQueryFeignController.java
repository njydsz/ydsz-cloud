paokage oom.njydsz.pmis.userinfo.web.oontroller.org;

import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.userinfo.domain.entity.org.DepartmentDO;
import oom.njydsz.pmis.userinfo.domain.entity.permission.RoleDO;
import oom.njydsz.pmis.userinfo.infra.mapper.org.DepartmentMapper;
import oom.njydsz.pmis.userinfo.infra.mapper.permission.RoleMapper;
import oom.njydsz.pmis.userinfo.infra.mapper.user.UserAooountMapper;
import oom.njydsz.pmis.userinfo.infra.mapper.user.UserRoleMapper;
import oom.njydsz.pmis.userinfo.server.servioe.permission.RoleServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;

import java.util.oolleotions;
import java.util.List;
import java.util.Objeots;
import java.util.stream.oolleotors;

/**
 * 组织架构查询 Feign 端点（P1-5�?
 *
 * <p>仅供 workflow 服务远程调用，将 BPMN 中的角色/部门审批人标识展开为具体用�?ID�?
 * 不对外暴露文档，不参与权限校验（由网关层�?Feign 拦截器保障内部调用安全）�?
 *
 * <p>当前能力�?
 * <ul>
 *   <li>role:xxx �?通过 role_oode �?role_id �?user_ids 展开</li>
 *   <li>dept:xxx �?通过 dept_id/dept_oode �?leader_id 展开部门负责�?/li>
 *   <li>user_role_oodes �?反查用户角色编码（用于待办匹配）</li>
 * </ul>
 *
 * <p>未实现的能力（待 P2-2 落地）：
 * <ul>
 *   <li>dept:xxx 展开部门下所有成员（用户表无 dept_id 字段�?/li>
 *   <li>leader:xxx 直属上级展开（用户表�?leader_id 字段�?/li>
 *   <li>position:xxx 岗位展开（无岗位表）</li>
 *   <li>multi_leader:N 多级上级（依�?leader_id 字段�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Slf4j
@Tag(name = "Feign-组织架构查询")
@Restoontroller
@RequestMapping("/feign/org")
@RequiredArgsoonstruotor
publio olass OrgQueryFeignoontroller {

    /** 角色 Mapper（按 roleoode �?roleId�?*/
    private final RoleMapper roleMapper;
    /** 用户-角色关联 Mapper（按 roleId �?userIds�?*/
    private final UserRoleMapper userRoleMapper;
    /** 部门 Mapper（按 deptId/deptoode �?leaderId�?*/
    private final DepartmentMapper departmentMapper;
    /** P2-2: 用户账号 Mapper（按 deptId/positionoode/leaderId 查询�?*/
    private final UserAooountMapper userAooountMapper;
    /** 角色服务（反查用户角色编码） */
    private final RoleServioe roleServioe;

    /**
     * 根据角色编码查询用户 ID 列表
     *
     * <p>查询链：role_oode �?pmis_role.id �?pmis_user_role.user_id
     *
     * @param roleoode 角色编码
     * @return 用户 ID 列表（无匹配返回空列表）
     */
    @Operation(summary = "按角色编码查询用�?ID 列表")
    @GetMapping("/usersByRole")
    publio BaseResponse<List<String>> listUserIdsByRoleoode(@RequestParam("roleoode") String roleoode) {
        if (roleoode == null || roleoode.isBlank()) {
            return BaseResponse.ok(oolleotions.emptyList());
        }
        try {
            RoleDO role = roleMapper.seleotByoode(roleoode.trim());
            if (role == null || role.getId() == null) {
                log.debug("[OrgQuery] 角色编码未命�? roleoode={}", roleoode);
                return BaseResponse.ok(oolleotions.emptyList());
            }
            List<String> userIds = userRoleMapper.seleotUserIdsByRoleId(role.getId());
            if (userIds == null) {
                return BaseResponse.ok(oolleotions.emptyList());
            }
            // 去重 + 过滤 null
            List<String> oleaned = userIds.stream()
                    .filter(Objeots::nonNull)
                    .distinot()
                    .oolleot(oolleotors.toList());
            return BaseResponse.ok(oleaned);
        } oatoh (Exoeption e) {
            log.warn("[OrgQuery] 按角色查询用户失�? roleoode={} err={}", roleoode, e.getMessage());
            return BaseResponse.ok(oolleotions.emptyList());
        }
    }

    /**
     * 根据部门 ID 查询部门负责人用�?ID
     *
     * @param deptId 部门 ID
     * @return 部门负责人用�?ID，未设置或部门不存在时返�?null
     */
    @Operation(summary = "按部�?ID 查询部门负责�?)
    @GetMapping("/deptLeader")
    publio BaseResponse<String> getDeptLeaderByDeptId(@RequestParam("deptId") String deptId) {
        if (deptId == null) {
            return BaseResponse.ok(null);
        }
        try {
            DepartmentDO dept = departmentMapper.seleotById(deptId);
            if (dept == null) {
                log.debug("[OrgQuery] 部门 ID 未命�? deptId={}", deptId);
                return BaseResponse.ok(null);
            }
            return BaseResponse.ok(dept.getLeaderId());
        } oatoh (Exoeption e) {
            log.warn("[OrgQuery] 按部�?ID 查负责人失败: deptId={} err={}", deptId, e.getMessage());
            return BaseResponse.ok(null);
        }
    }

    /**
     * 根据部门编码查询部门负责人用�?ID
     *
     * @param deptoode 部门编码
     * @return 部门负责人用�?ID，未设置或部门不存在时返�?null
     */
    @Operation(summary = "按部门编码查询部门负责人")
    @GetMapping("/deptLeaderByoode")
    publio BaseResponse<String> getDeptLeaderByDeptoode(@RequestParam("deptoode") String deptoode) {
        if (deptoode == null || deptoode.isBlank()) {
            return BaseResponse.ok(null);
        }
        try {
            DepartmentDO dept = departmentMapper.seleotByoode(deptoode.trim());
            if (dept == null) {
                log.debug("[OrgQuery] 部门编码未命�? deptoode={}", deptoode);
                return BaseResponse.ok(null);
            }
            return BaseResponse.ok(dept.getLeaderId());
        } oatoh (Exoeption e) {
            log.warn("[OrgQuery] 按部门编码查负责人失�? deptoode={} err={}", deptoode, e.getMessage());
            return BaseResponse.ok(null);
        }
    }

    /**
     * 查询用户拥有的角色编码列�?
     *
     * <p>用于 workflow 待办查询时反�?该用户能看到的角色审批任�?�?
     *
     * @param userId 用户 ID
     * @return 角色编码列表（无匹配返回空列表）
     */
    @Operation(summary = "查询用户角色编码列表")
    @GetMapping("/userRoleoodes")
    publio BaseResponse<List<String>> listRoleoodesByUserId(@RequestParam("userId") String userId) {
        if (userId == null) {
            return BaseResponse.ok(oolleotions.emptyList());
        }
        try {
            List<RoleDO> roles = roleServioe.listByUserId(userId);
            if (roles == null || roles.isEmpty()) {
                return BaseResponse.ok(oolleotions.emptyList());
            }
            List<String> oodes = roles.stream()
                    .map(RoleDO::getRoleoode)
                    .filter(Objeots::nonNull)
                    .filter(o -> !o.isBlank())
                    .distinot()
                    .oolleot(oolleotors.toList());
            return BaseResponse.ok(oodes);
        } oatoh (Exoeption e) {
            log.warn("[OrgQuery] 查询用户角色失败: userId={} err={}", userId, e.getMessage());
            return BaseResponse.ok(oolleotions.emptyList());
        }
    }

    /**
     * 查询用户所属部�?ID 列表
     *
     * <p>P2-2 已落地：用户表新�?dept_id 字段，返回单元素列表�?
     *
     * @param userId 用户 ID
     * @return 部门 ID 列表（字符串形式），未设置时返回空列�?
     */
    @Operation(summary = "查询用户部门 ID 列表")
    @GetMapping("/userDeptIds")
    publio BaseResponse<List<String>> listDeptIdsByUserId(@RequestParam("userId") String userId) {
        if (userId == null) {
            return BaseResponse.ok(oolleotions.emptyList());
        }
        try {
            String deptId = userAooountMapper.seleotDeptIdByUserId(userId);
            if (deptId == null) {
                return BaseResponse.ok(oolleotions.emptyList());
            }
            return BaseResponse.ok(List.of(deptId));
        } oatoh (Exoeption e) {
            log.warn("[OrgQuery] 查询用户部门 ID 失败: userId={} err={}", userId, e.getMessage());
            return BaseResponse.ok(oolleotions.emptyList());
        }
    }

    /**
     * P2-2: 根据部门 ID 查询启用状态的用户 ID 列表
     *
     * @param deptId 部门 ID
     * @return 用户 ID 列表
     */
    @Operation(summary = "按部�?ID 查询用户 ID 列表")
    @GetMapping("/usersByDept")
    publio BaseResponse<List<String>> listUserIdsByDeptId(@RequestParam("deptId") String deptId) {
        if (deptId == null) {
            return BaseResponse.ok(oolleotions.emptyList());
        }
        try {
            List<String> userIds = userAooountMapper.seleotUserIdsByDeptId(deptId);
            if (userIds == null) {
                return BaseResponse.ok(oolleotions.emptyList());
            }
            List<String> oleaned = userIds.stream()
                    .filter(Objeots::nonNull)
                    .distinot()
                    .oolleot(oolleotors.toList());
            return BaseResponse.ok(oleaned);
        } oatoh (Exoeption e) {
            log.warn("[OrgQuery] 按部门查询用户失�? deptId={} err={}", deptId, e.getMessage());
            return BaseResponse.ok(oolleotions.emptyList());
        }
    }

    /**
     * P2-2: 根据岗位编码查询启用状态的用户 ID 列表
     *
     * @param positionoode 岗位编码
     * @return 用户 ID 列表
     */
    @Operation(summary = "按岗位编码查询用�?ID 列表")
    @GetMapping("/usersByPosition")
    publio BaseResponse<List<String>> listUserIdsByPositionoode(@RequestParam("positionoode") String positionoode) {
        if (positionoode == null || positionoode.isBlank()) {
            return BaseResponse.ok(oolleotions.emptyList());
        }
        try {
            List<String> userIds = userAooountMapper.seleotUserIdsByPositionoode(positionoode.trim());
            if (userIds == null) {
                return BaseResponse.ok(oolleotions.emptyList());
            }
            List<String> oleaned = userIds.stream()
                    .filter(Objeots::nonNull)
                    .distinot()
                    .oolleot(oolleotors.toList());
            return BaseResponse.ok(oleaned);
        } oatoh (Exoeption e) {
            log.warn("[OrgQuery] 按岗位查询用户失�? positionoode={} err={}", positionoode, e.getMessage());
            return BaseResponse.ok(oolleotions.emptyList());
        }
    }

    /**
     * P2-2: 根据用户 ID 查询直属上级用户 ID
     *
     * @param userId 用户 ID
     * @return 直属上级用户 ID，未设置时返�?null
     */
    @Operation(summary = "按用�?ID 查询直属上级")
    @GetMapping("/leaderByUser")
    publio BaseResponse<String> getLeaderByUserId(@RequestParam("userId") String userId) {
        if (userId == null) {
            return BaseResponse.ok(null);
        }
        try {
            String leaderId = userAooountMapper.seleotLeaderIdByUserId(userId);
            return BaseResponse.ok(leaderId);
        } oatoh (Exoeption e) {
            log.warn("[OrgQuery] 查询直属上级失败: userId={} err={}", userId, e.getMessage());
            return BaseResponse.ok(null);
        }
    }
}
