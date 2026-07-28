package com.njydsz.userinfo.domain.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.njydsz.userinfo.domain.dto.UserAccountCreateDTO;
import com.njydsz.userinfo.domain.dto.post.CompanyPostDTO;
import com.njydsz.userinfo.domain.dto.post.DepartmentPostDTO;
import com.njydsz.userinfo.domain.dto.post.LanguagePostDTO;
import com.njydsz.userinfo.domain.dto.post.MenuPostDTO;
import com.njydsz.userinfo.domain.dto.post.PostPostDTO;
import com.njydsz.userinfo.domain.dto.post.RolePostDTO;
import com.njydsz.userinfo.domain.dto.put.CompanyPutDTO;
import com.njydsz.userinfo.domain.dto.put.DepartmentPutDTO;
import com.njydsz.userinfo.domain.dto.put.LanguagePutDTO;
import com.njydsz.userinfo.domain.dto.put.MenuPutDTO;
import com.njydsz.userinfo.domain.dto.put.PostPutDTO;
import com.njydsz.userinfo.domain.dto.put.RolePutDTO;
import com.njydsz.userinfo.domain.entity.Company;
import com.njydsz.userinfo.domain.entity.Department;
import com.njydsz.userinfo.domain.entity.Language;
import com.njydsz.userinfo.domain.entity.Menu;
import com.njydsz.userinfo.domain.entity.Post;
import com.njydsz.userinfo.domain.entity.Role;
import com.njydsz.userinfo.domain.entity.UserAccount;
import com.njydsz.userinfo.domain.vo.CompanyVO;
import com.njydsz.userinfo.domain.vo.DepartmentTreeVO;
import com.njydsz.userinfo.domain.vo.DepartmentVO;
import com.njydsz.userinfo.domain.vo.LanguageVO;
import com.njydsz.userinfo.domain.vo.LoginVO;
import com.njydsz.userinfo.domain.vo.MenuTreeVO;
import com.njydsz.userinfo.domain.vo.MenuVO;
import com.njydsz.userinfo.domain.vo.PostVO;
import com.njydsz.userinfo.domain.vo.RoleVO;
import com.njydsz.userinfo.domain.vo.UserAccountVO;

/**
 * 用户中心模块统一 MapStruct 转换器。
 *
 * <p>提供 Entity ↔ VO / DTO → Entity 的转换方法，替代 BeanUtils.copyProperties 反射方式。
 * MpBaseEntity 的自动填充字段在 DTO→Entity 方向通过 @Mapping(ignore = true) 忽略。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface UserInfoConverter {

    UserInfoConverter INSTANT = Mappers.getMapper(UserInfoConverter.class);

    // ===== Company =====
    CompanyVO entityToVO(Company entity);
    List<CompanyVO> companyListToVO(List<Company> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Company postDtoToEntity(CompanyPostDTO dto);

    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Company putDtoToEntity(CompanyPutDTO dto);

    // ===== Department =====
    DepartmentVO entityToVO(Department entity);
    List<DepartmentVO> departmentListToVO(List<Department> entities);
    DepartmentTreeVO entityToTreeVO(Department entity);
    List<DepartmentTreeVO> departmentTreeListToVO(List<Department> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Department postDtoToEntity(DepartmentPostDTO dto);

    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Department putDtoToEntity(DepartmentPutDTO dto);

    // ===== Language =====
    LanguageVO entityToVO(Language entity);
    List<LanguageVO> languageListToVO(List<Language> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Language postDtoToEntity(LanguagePostDTO dto);

    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Language putDtoToEntity(LanguagePutDTO dto);

    // ===== Menu =====
    MenuVO entityToVO(Menu entity);
    List<MenuVO> menuListToVO(List<Menu> entities);
    MenuTreeVO entityToMenuTreeVO(Menu entity);
    List<MenuTreeVO> menuTreeListToVO(List<Menu> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Menu postDtoToEntity(MenuPostDTO dto);

    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Menu putDtoToEntity(MenuPutDTO dto);

    // ===== Post =====
    PostVO entityToVO(Post entity);
    List<PostVO> postListToVO(List<Post> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Post postDtoToEntity(PostPostDTO dto);

    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Post putDtoToEntity(PostPutDTO dto);

    // ===== Role =====
    RoleVO entityToVO(Role entity);
    List<RoleVO> roleListToVO(List<Role> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Role postDtoToEntity(RolePostDTO dto);

    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Role putDtoToEntity(RolePutDTO dto);

    // ===== UserAccount =====
    UserAccountVO entityToVO(UserAccount entity);
    List<UserAccountVO> userAccountListToVO(List<UserAccount> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    UserAccount createDtoToEntity(UserAccountCreateDTO dto);

    // ===== UserAccount → LoginVO.UserInfoVO =====
    /**
     * 用户实体 → 登录响应中的用户基本信息 VO。
     *
     * <p>仅映射实体上可直接对应的字段：
     * <ul>
     *   <li>{@code id} → {@code userId}</li>
     *   <li>{@code username} / {@code realName} / {@code tenantId} / {@code avatar} 同名映射</li>
     * </ul>
     * 派生字段 {@code roleCode} / {@code roleName} 由调用方从角色列表拼接后设置，
     * 此处通过 {@code @Mapping(ignore = true)} 隔离，避免 MapStruct 报未映射属性告警。
     *
     * @param entity 用户账号实体
     * @return 登录响应中的用户基本信息 VO（roleCode/roleName 为 null，需调用方填充）
     */
    @Mapping(target = "userId", source = "id")
    @Mapping(target = "roleCode", ignore = true)
    @Mapping(target = "roleName", ignore = true)
    LoginVO.UserInfoVO entityToUserInfoVO(UserAccount entity);
}
