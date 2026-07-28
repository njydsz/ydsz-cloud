package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.common.domain.query.PageResult;
import com.njydsz.userinfo.domain.dto.post.LanguagePostDTO;
import com.njydsz.userinfo.domain.dto.put.LanguagePutDTO;
import com.njydsz.userinfo.domain.query.LanguagePageQuery;
import com.njydsz.userinfo.domain.vo.LanguageVO;

/**
 * 语言 Service 接口
 *
 * <p>封装语言的完整业务逻辑：CRUD、默认语言唯一性管理。
 * 用于前端 i18n 国际化与后端消息文案回退链。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li>语言 CRUD（含 {@code languageCode} 唯一性校验）</li>
 *   <li>语言分页与全量列表查询</li>
 *   <li>默认语言唯一性管理（系统全局仅 1 个默认语言，由 Service 层事务保证）</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <ul>
 *   <li>前端 i18n 加载：从 {@code /api/v1/language/list} 获取所有启用语言</li>
 *   <li>后端消息文案：通过 {@code LocaleContextHolder} 匹配 {@code ydsz_i18n_message} 表</li>
 *   <li>浏览器语言探测：根据 {@code Accept-Language} 头选择最匹配语言</li>
 * </ul>
 *
 * <p><b>事务：</b>所有写操作（{@code save/updateById/removeById}）开启
 * {@code @Transactional(rollbackFor = Exception.class)}，确保任一异常触发完整回滚。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.userinfo.domain.entity.Language 语言实体
 * @see com.njydsz.userinfo.web.controller.LanguageController 语言 Controller
 */
public interface LanguageService {

    /**
     * 分页查询语言列表。
     *
     * <p>支持按 {@code languageCode} / {@code languageName} 模糊匹配、{@code status} 精确匹配过滤。
     *
     * @param query 分页查询参数
     * @return 分页结果（{@link PageResult} 包含 total/records）
     */
    PageResult<LanguageVO> page(LanguagePageQuery query);

    /**
     * 按 ID 查询语言详情。
     *
     * @param id 主键 ID
     * @return 语言 VO，不存在时返回 null
     */
    LanguageVO getById(String id);

    /**
     * 查询全部未删除语言列表（按 {@code sortOrder} 升序）。
     *
     * <p>适用于前端语言切换器选项加载。
     *
     * @return 语言视图对象列表
     */
    List<LanguageVO> list();

    /**
     * 创建语言。
     *
     * <p>校验：{@code languageCode} 唯一性。设为默认语言时自动取消旧默认（事务内）。
     *
     * @param dto 语言保存 DTO
     * @return 新语言主键 ID
     */
    String save(LanguageSaveDTO dto);

    /**
     * 更新语言。
     *
     * <p>设为默认语言时自动取消旧默认（事务内）。
     *
     * @param dto 语言保存 DTO
     * @return true=成功
     */
    boolean updateById(LanguageSaveDTO dto);

    /**
     * 删除语言（逻辑删除）。
     *
     * <p>校验：默认语言不可删除。
     *
     * @param id 主键 ID
     * @return true=成功
     */
    boolean removeById(String id);
}
