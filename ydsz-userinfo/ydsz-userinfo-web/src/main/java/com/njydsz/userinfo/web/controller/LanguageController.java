package com.njydsz.userinfo.web.controller;

import java.util.List;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.domain.query.PageResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.userinfo.domain.query.LanguagePageQuery;
import com.njydsz.userinfo.domain.vo.LanguageVO;
import com.njydsz.userinfo.server.service.LanguageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.njydsz.userinfo.domain.dto.post.LanguagePostDTO;
import com.njydsz.userinfo.domain.dto.put.LanguagePutDTO;

/**
 * 语言 Controller
 *
 * <p>提供语言的完整管理能力（CRUD），含默认语言唯一性管理。
 * 用于前端 i18n 国际化与后端消息文案回退链。
 *
 * <p><b>接口路径：</b>{@code /api/v1/language}
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li>语言分页/列表查询（按 {@code sortOrder} 升序）</li>
 *   <li>语言 CRUD（含 {@code languageCode} 唯一性校验）</li>
 *   <li>默认语言唯一性管理（系统全局仅 1 个默认语言，事务内自动取消旧默认）</li>
 * </ul>
 *
 * <p><b>与其它模块的关联：</b>语言配置影响：
 * <ul>
 *   <li>前端 i18n 文案（前端通过 {@code /api/v1/language/list} 加载语言选项）</li>
 *   <li>后端消息文案（{@code LocaleContextHolder} 匹配 {@code ydsz_i18n_message} 表）</li>
 *   <li>浏览器语言探测（{@code Accept-Language} 头解析）</li>
 * </ul>
 *
 * <p><b>安全特性：</b>
 * <ul>
 *   <li>写接口启用 {@link Idempotent} 防重复提交（Redis SET NX EX）</li>
 *   <li>写接口启用 {@link RateLimit} 接口级限流（50 QPS）</li>
 *   <li>写接口启用 {@link Audit} 审计日志（异步持久化）</li>
 *   <li>读接口无防护，业务方可高频调用</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see LanguageService 语言业务逻辑
 * @see com.njydsz.userinfo.domain.entity.Language 语言实体
 */
@RestController
@RequestMapping("/api/v1/language")
@Tag(name = "语言管理", description = "语言 CRUD")
@RequiredArgsConstructor
public class LanguageController {

    private final LanguageService service;

    // ============================== CRUD 端点 ==============================

    /**
     * 分页查询语言
     *
     * <p>支持按 {@code languageCode / languageName} 模糊匹配 + {@code status} 精确匹配过滤，
     * 默认按 {@code sortOrder} 升序、{@code id} 升序排列。
     *
     * @param query 分页查询条件（pageNum / pageSize / languageCode / languageName / status）
     * @return 分页结果（总记录数、当前页、每页大小、数据列表）
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询")
    public PageResponse<List<LanguageVO>> page(LanguagePageQuery query) {
        PageResponse<LanguageVO> result = service.page(query);
        return PageResponse.success(
                result.getTotal(),
                (long) result.getPageNum(),
                (long) result.getPageSize(),
                result.getRecords());
    }

    /**
     * 按 ID 查询语言
     *
     * @param id 语言 ID
     * @return 语言详情；不存在或已删除时返回 null
     */
    @GetMapping("/{id}")
    @Operation(summary = "按 ID 查询")
    public BaseResponse<LanguageVO> getById(@PathVariable String id) {
        return BaseResponse.success(service.getById(id));
    }

    /**
     * 查询全部语言列表（不翻页）
     *
     * <p>前端 i18n 切换器数据源，按 {@code sortOrder} 升序排列。
     * <p>建议业务方客户端缓存（变更频率极低）。
     *
     * @return 全部未删除语言列表
     */
    @GetMapping("/list")
    @Operation(summary = "查询全部语言列表")
    public BaseResponse<List<LanguageVO>> list() {
        return BaseResponse.success(service.list());
    }

    /**
     * 创建语言
     *
     * <p>幂等保护 5 秒；限流 50 QPS；写审计日志。
     * <p>业务流程：languageCode 唯一性校验 → 写入 DB。
     * <p>若 {@code isDefault=true}，事务内自动取消其他语言的默认标识（保证系统全局仅 1 个默认语言）。
     *
     * @param dto 语言创建 DTO（languageCode / languageName / isDefault / sortOrder / status）
     * @return 新创建的语言 ID
     */
    @Audit(module = "语言管理", type = AuditType.OPERATION, action = AuditAction.CREATE,
            content = "'创建语言: ' + #dto.languageName")
    @Idempotent(key = "ydsz:userinfo:LanguageController:create:lock", ttlSeconds = 5)
    @RateLimit(resource = "userinfo.language.create", threshold = 50)
    @PostMapping
    @Operation(summary = "创建语言")
    public BaseResponse<String> create(@Valid @RequestBody LanguagePostDTO dto) {
        return BaseResponse.success(service.create(dto));
    }

    /**
     * 更新语言
     *
     * <p>幂等保护 5 秒；限流 50 QPS；写审计日志。
     * <p>业务流程：使用 {@code BeanUpdateUtil.copyNonNull} 动态复制非 null 字段。
     * <p>若将 {@code isDefault} 改为 {@code true}，事务内自动取消其他语言的默认标识。
     *
     * @param dto 语言更新 DTO（必须包含 ID）
     * @return 是否成功
     */
    @Audit(module = "语言管理", type = AuditType.OPERATION, action = AuditAction.UPDATE,
            content = "'更新语言: ' + #dto.id")
    @Idempotent(key = "ydsz:userinfo:LanguageController:update:lock", ttlSeconds = 5)
    @RateLimit(resource = "userinfo.language.update", threshold = 50)
    @PutMapping
    @Operation(summary = "更新语言")
    public BaseResponse<Boolean> update(@Valid @RequestBody LanguagePutDTO dto) {
        return BaseResponse.success(service.update(dto));
    }

    /**
     * 按 ID 删除语言
     *
     * <p>幂等保护 5 秒；限流 50 QPS；写审计日志。
     * <p>删除前置校验：默认语言（{@code isDefault=true}）<b>禁止删除</b>，避免后端文案回退链断裂。
     * 如需删除默认语言，<b>必须先</b>设置其他语言为默认。
     *
     * @param id 语言 ID
     * @return 是否成功
     */
    @Audit(module = "语言管理", type = AuditType.OPERATION, action = AuditAction.DELETE,
            content = "'删除语言: ' + #id")
    @RateLimit(resource = "userinfo.language.remove", threshold = 50)
    @Idempotent(key = "ydsz:userinfo:LanguageController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    @Operation(summary = "删除语言")
    public BaseResponse<Boolean> remove(@PathVariable String id) {
        return BaseResponse.success(service.removeById(id));
    }
}
