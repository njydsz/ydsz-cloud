package com.njydsz.common.web.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.domain.query.PageResult;
import com.njydsz.common.domain.service.BaseCrudService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

/**
 * 通用 CRUD 控制器基类。
 *
 * <p>提供标准的分页查询、按 ID 查询、新增、修改、删除五个端点，
 * 子类只需实现 {@link #getService()} 即可获得完整 CRUD 能力。
 *
 * <p><b>泛型参数：</b>
 * <ul>
 *   <li>{@code T}  - 实体类型</li>
 *   <li>{@code DTO} - 数据传输对象（新增/修改入参）</li>
 *   <li>{@code VO}  - 视图对象（出参）</li>
 *   <li>{@code PQ}  - 分页查询参数类型，须继承 {@link com.njydsz.common.domain.query.PageQuery}</li>
 * </ul>
 *
 * <p><b>审计与幂等：</b>
 * 基类方法不附带 {@code @Audit} / {@code @Idempotent} 注解，子类覆写 save/update/remove 方法时
 * 按需添加注解，以指定模块名称、审计内容和幂等键。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * &#64;Tag(name = "用户管理", description = "用户 CRUD")
 * &#64;RestController
 * &#64;RequestMapping("/api/v1/user")
 * public class UserController extends BaseCrudController<User, UserDTO, UserVO, UserPageQuery> {
 *
 *     private final UserService userService;
 *
 *     public UserController(UserService userService) {
 *         this.userService = userService;
 *     }
 *
 *     &#64;Override
 *     protected BaseCrudService<User, UserDTO, UserVO, UserPageQuery> getService() {
 *         return userService;
 *     }
 *
 *     &#64;Audit(module = "用户管理", type = AuditType.OPERATION, action = AuditAction.CREATE,
 *             content = "'创建用户: ' + #dto.username")
 *     &#64;Idempotent(key = "user:save", ttlSeconds = 5, message = "请勿重复提交")
 *     &#64;Override
 *     &#64;PostMapping
 *     public BaseResponse<String> save(&#64;Valid &#64;RequestBody UserDTO dto) {
 *         return super.save(dto);
 *     }
 * }
 * }</pre>
 *
 * @param <T>  实体类型
 * @param <DTO> 数据传输对象
 * @param <VO>  视图对象
 * @param <PQ>  分页查询参数类型
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public abstract class BaseCrudController<T, DTO, VO, PQ extends com.njydsz.common.domain.query.PageQuery> {

    /**
     * 获取业务 Service 实例。
     *
     * @return CRUD Service
     */
    protected abstract BaseCrudService<T, DTO, VO, PQ> getService();

    // ============================== 分页查询 ==============================

    /**
     * 分页查询。
     *
     * @param query 分页查询参数
     * @return 分页结果
     */
    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public PageResponse<List<VO>> page(@Parameter(description = "分页查询参数") PQ query) {
        PageResult<VO> result = getService().page(query);
        return PageResponse.success(
                result.getTotal(),
                (long) result.getPageNum(),
                (long) result.getPageSize(),
                result.getRecords());
    }

    // ============================== 按 ID 查询 ==============================

    /**
     * 按 ID 查询。
     *
     * @param id 主键 ID
     * @return 视图对象
     */
    @Operation(summary = "按 ID 查询")
    @GetMapping("/{id}")
    public BaseResponse<VO> getById(@Parameter(description = "主键 ID") @PathVariable String id) {
        return BaseResponse.success(getService().getById(id));
    }

    // ============================== 新增 ==============================

    /**
     * 新增。
     *
     * <p>子类可覆写此方法并添加 {@code @Audit} / {@code @Idempotent} 注解。
     *
     * @param dto 数据传输对象
     * @return 主键 ID
     */
    @Operation(summary = "新增")
    @PostMapping
    public BaseResponse<String> save(@Valid @RequestBody DTO dto) {
        return BaseResponse.success(getService().save(dto));
    }

    // ============================== 修改 ==============================

    /**
     * 修改。
     *
     * <p>子类可覆写此方法并添加 {@code @Audit} 注解。
     *
     * @param dto 数据传输对象
     * @return 是否成功
     */
    @Operation(summary = "修改")
    @PutMapping
    public BaseResponse<Boolean> update(@Valid @RequestBody DTO dto) {
        return BaseResponse.success(getService().updateById(dto));
    }

    // ============================== 删除 ==============================

    /**
     * 删除。
     *
     * <p>子类可覆写此方法并添加 {@code @Audit} / {@code @Idempotent} 注解。
     *
     * @param id 主键 ID
     * @return 是否成功
     */
    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> remove(@Parameter(description = "主键 ID") @PathVariable String id) {
        return BaseResponse.success(getService().removeById(id));
    }
}
