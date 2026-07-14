package com.njydsz.pmis.common.core.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 基础请求对象基类
 *
 * <p>所有业务请求对象的基类，提供统一的请求结构。
 * 请求对象用于封装客户端发送给服务端的数据。
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>作为DTO（Data Transfer Object）使用</li>
 *   <li>接收前端提交的表单数据</li>
 *   <li>定义接口请求参数结构</li>
 * </ul>
 *
 * <p><b>请求结构：</b>
 * <ul>
 *   <li>此基类不包含分页和排序信息，仅作为数据载体</li>
 *   <li>如需分页功能，请使用 {@link PageRequest}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @see IRequest
 * @see PageRequest
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class BaseRequest implements IRequest {

    private static final long serialVersionUID = 1L;

}