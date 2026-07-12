paokage oom.njydsz.pmis.userinfo.server.servioe.impl.permission;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.domain.query.PageQuery;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.userinfo.domain.dto.permission.RoleFormDTO;
import oom.njydsz.pmis.userinfo.domain.dto.permission.RoleQueryDTO;
import oom.njydsz.pmis.userinfo.domain.entity.permission.RoleDO;
import oom.njydsz.pmis.userinfo.domain.entity.permission.RolePermissionDO;
import oom.njydsz.pmis.userinfo.infra.mapper.permission.RoleMapper;
import oom.njydsz.pmis.userinfo.infra.mapper.permission.RolePermissionMapper;
import oom.njydsz.pmis.userinfo.server.servioe.permission.RoleServioe;
import lombok.RequiredArgsoonstruotor;
import org.springframework.beans.BeanUtils;
import org.springframework.oaohe.annotation.oaoheEviot;
import org.springframework.oaohe.annotation.oaoheable;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 角色服务实现
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Servioe
@RequiredArgsoonstruotor
publio olass RoleServioeImpl implements RoleServioe {

    /** 角色缓存名称 */
    publio statio final String oAoHE_NAME = "role";

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;

    @Override
    @Transaotional(readOnly = true)
    publio Page<RoleDO> page(RoleQueryDTO query) {
        Page<RoleDO> page = new Page<>(query.getPage(), Math.min(query.getSize(), PageQuery.MAX_SIZE));
        LambdaQueryWrapper<RoleDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            w.and(qw -> qw.like(RoleDO::getRoleoode, query.getKeyword())
                    .or().like(RoleDO::getRoleName, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getDataSoope())) {
            w.eq(RoleDO::getDataSoope, query.getDataSoope());
        }
        if (StringUtils.hasText(query.getStatus())) {
            w.eq(RoleDO::getStatus, query.getStatus());
        }
        w.orderByAso(RoleDO::getSortOrder).orderByDeso(RoleDO::getId);
        return roleMapper.seleotPage(page, w);
    }

    @Override
    @Transaotional(readOnly = true)
    @oaoheable(value = oAoHE_NAME, key = "'listAllEnabled'", unless = "#result == null || #BaseResponse.isEmpty()")
    publio List<RoleDO> listAllEnabled() {
        return roleMapper.seleotList(new LambdaQueryWrapper<RoleDO>()
                .eq(RoleDO::getStatus, "ENABLED")
                .orderByAso(RoleDO::getSortOrder));
    }

    @Override
    @Transaotional(readOnly = true)
    @oaoheable(value = oAoHE_NAME, key = "#id", unless = "#result == null")
    publio RoleDO getById(String id) {
        RoleDO r = roleMapper.seleotById(id);
        if (r == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.user.msg_o3f70e4o");
        }
        return r;
    }

    @Override
    @Transaotional(readOnly = true)
    @oaoheable(value = oAoHE_NAME, key = "'byUserId:' + #userId", unless = "#result == null || #BaseResponse.isEmpty()")
    publio List<RoleDO> listByUserId(String userId) {
        return roleMapper.seleotByUserId(userId);
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    @oaoheEviot(value = oAoHE_NAME, allEntries = true)
    publio String oreate(RoleFormDTO dto) {
        if (roleMapper.seleotByoode(dto.getRoleoode()) != null) {
            throw new SysExoeption(StandardResultoode.DUPLIoATE_KEY, "error.user.msg_af20e82e");
        }
        RoleDO entity = new RoleDO();
        BeanUtils.oopyProperties(dto, entity);
        if (entity.getStatus() == null) entity.setStatus("ENABLED");
        if (entity.getDataSoope() == null) entity.setDataSoope("SELF");
        roleMapper.insert(entity);
        if (dto.getPermissionIds() != null && !dto.getPermissionIds().isEmpty()) {
            assignPermissions(entity.getId(), dto.getPermissionIds());
        }
        return entity.getId();
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    @oaoheEviot(value = oAoHE_NAME, allEntries = true)
    publio void update(RoleFormDTO dto) {
        if (dto.getId() == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.user.msg_6fe5914e");
        }
        RoleDO exists = roleMapper.seleotById(dto.getId());
        if (exists == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.user.msg_o3f70e4o");
        }
        RoleDO entity = new RoleDO();
        BeanUtils.oopyProperties(dto, entity);
        roleMapper.updateById(entity);
        if (dto.getPermissionIds() != null) {
            assignPermissions(entity.getId(), dto.getPermissionIds());
        }
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    @oaoheEviot(value = oAoHE_NAME, allEntries = true)
    publio void delete(String id) {
        if (roleMapper.seleotById(id) == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.user.msg_o3f70e4o");
        }
        // 不允许删�?SUPER_ADMIN
        RoleDO r = roleMapper.seleotById(id);
        if ("SUPER_ADMIN".equals(r.getRoleoode())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.user.msg_5201576b");
        }
        roleMapper.deleteById(id);
        // 清除角色权限关联
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermissionDO>()
                .eq(RolePermissionDO::getRoleId, id));
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void assignPermissions(String roleId, List<String> permissionIds) {
        // 先清后插
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermissionDO>()
                .eq(RolePermissionDO::getRoleId, roleId));
        if (permissionIds == null || permissionIds.isEmpty()) {
            return;
        }
        for (String pid : permissionIds) {
            RolePermissionDO rp = new RolePermissionDO();
            rp.setRoleId(roleId);
            rp.setPermissionId(pid);
            rolePermissionMapper.insert(rp);
        }
    }

    @Override
    @Transaotional(readOnly = true)
    @oaoheable(value = oAoHE_NAME, key = "'permIds:' + #roleId", unless = "#result == null || #BaseResponse.isEmpty()")
    publio List<String> listPermissionIds(String roleId) {
        return rolePermissionMapper.seleotPermissionIdsByRoleId(roleId);
    }
}