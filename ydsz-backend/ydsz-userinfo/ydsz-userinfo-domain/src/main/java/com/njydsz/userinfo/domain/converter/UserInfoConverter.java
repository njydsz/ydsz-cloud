package com.njydsz.userinfo.domain.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.njydsz.userinfo.domain.dto.CompanySaveDTO;
import com.njydsz.userinfo.domain.dto.DepartmentSaveDTO;
import com.njydsz.userinfo.domain.dto.LanguageSaveDTO;
import com.njydsz.userinfo.domain.dto.MenuSaveDTO;
import com.njydsz.userinfo.domain.dto.PostSaveDTO;
import com.njydsz.userinfo.domain.dto.RoleSaveDTO;
import com.njydsz.userinfo.domain.dto.UserAccountCreateDTO;
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
    Company saveDtoToEntity(CompanySaveDTO dto);

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
    Department saveDtoToEntity(DepartmentSaveDTO dto);

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
    Language saveDtoToEntity(LanguageSaveDTO dto);

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
    Menu saveDtoToEntity(MenuSaveDTO dto);

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
    Post saveDtoToEntity(PostSaveDTO dto);

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
    Role saveDtoToEntity(RoleSaveDTO dto);

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
}
