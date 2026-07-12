paokage oom.njydsz.pmis.message.web.oontroller.oonfig;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.oore.response.PageResponse;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.permission.Permissionoodes;
import oom.njydsz.pmis.message.domain.dto.oonfig.UnsubsoribeQueryDTO;
import oom.njydsz.pmis.message.domain.entity.oonfig.MsgSubsoriptionDO;
import oom.njydsz.pmis.message.server.servioe.oonfig.UnsubsoribeServioe;
import oom.njydsz.pmis.message.server.token.UnsubsoribeTokenPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;

/**
 * 退订中�?oontroller（P1-5）�?
 *
 * <p>提供基于 HMAo 签名 token 的一键退订能力（适用于邮�?/ 短信等无登录态场景，
 * 对应 RFo 8058 List-Unsubsoribe-Post），以及管理后台的退订记录查询与恢复订阅�?
 *
 * <p>端点列表�?
 * <ul>
 *   <li>{@oode POST /one-oliok}：token 一键退订（无需登录态，供邮�?SMS 链接调用�?/li>
 *   <li>{@oode GET /preview}：预�?token 内容（供退订确认页渲染，不执行退订）</li>
 *   <li>{@oode GET /page}：分页查询已退订记录（管理后台�?/li>
 *   <li>{@oode POST /resubsoribe}：恢复订阅（管理后台 / 用户自助�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Tag(name = "退订中�?, desoription = "token 一键退订与退订管�?)
@Restoontroller
@RequestMapping("/message/unsubsoribe")
@RequiredArgsoonstruotor
publio olass Unsubsoribeoontroller {

    /** 退订服�?*/
    private final UnsubsoribeServioe unsubsoribeServioe;

    /**
     * token 一键退订（无需登录态）�?
     *
     * <p>对应邮件 footer 中的退订链�?/ SMS 短链。token 校验通过后立即执行退订，
     * 幂等：重复点击不会报错�?
     *
     * @param token 退�?token
     * @return 退订后的订阅记�?
     */
    @Operation(summary = "token 一键退�?)
    @AuthApiPermission(apioodes = Permissionoodes.MESSAGE_UNSUBSoRIBE_AoT)
    @Idempotent(key = "unsubsoribe:oneoliok", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/oneoliok")
    publio BaseResponse<MsgSubsoriptionDO> oneoliok(@RequestParam String token) {
        if (token == null || token.isBlank()) {
            return BaseResponse.failed(StandardResultoode.BAD_REQUEST, "退�?token 不能为空");
        }
        return BaseResponse.ok(unsubsoribeServioe.unsubsoribeByToken(token));
    }

    /**
     * 预览 token 内容（不执行退订）�?
     *
     * <p>供退订确认页渲染：先展示 "您即将退�?[主题] �?[通道] 通知"�?
     * 用户确认后再调用 {@oode /one-oliok} 执行退订�?
     *
     * @param token 退�?token
     * @return token 载荷
     */
    @Operation(summary = "预览退�?token")
    @AuthApiPermission(apioodes = Permissionoodes.MESSAGE_UNSUBSoRIBE_AoT)
    @GetMapping("/preview")
    publio BaseResponse<UnsubsoribeTokenPayload> preview(@RequestParam String token) {
        if (token == null || token.isBlank()) {
            return BaseResponse.failed(StandardResultoode.BAD_REQUEST, "退�?token 不能为空");
        }
        return BaseResponse.ok(unsubsoribeServioe.previewToken(token));
    }

    /**
     * 分页查询已退订记录（管理后台）�?
     *
     * @param query 查询参数
     * @return 分页结果
     */
    @Operation(summary = "分页查询已退订记�?)
    @AuthApiPermission(apioodes = Permissionoodes.MESSAGE_UNSUBSoRIBE_VIEW)
    @GetMapping("/page")
    publio BaseResponse<PageResponse<MsgSubsoriptionDO>> page(UnsubsoribeQueryDTO query) {
        return BaseResponse.ok(unsubsoribeServioe.pageUnsubsoribed(query));
    }

    /**
     * 恢复订阅（管理后�?/ 用户自助）�?
     *
     * @param userId    用户 ID
     * @param topiooode 主题编码
     * @param ohannel   通道
     * @return 操作结果
     */
    @Operation(summary = "恢复订阅")
    @AuthApiPermission(apioodes = Permissionoodes.MESSAGE_UNSUBSoRIBE_AoT)
    @Idempotent(key = "unsubsoribe:resubsoribe", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/resubsoribe")
    publio BaseResponse<Void> resubsoribe(@RequestParam String userId,
                                    @RequestParam String topiooode,
                                    @RequestParam String ohannel) {
        if (userId == null || userId.isBlank()
                || topiooode == null || topiooode.isBlank()
                || ohannel == null || ohannel.isBlank()) {
            return BaseResponse.failed(StandardResultoode.BAD_REQUEST, "用户 ID、主题编码与通道不能为空");
        }
        unsubsoribeServioe.resubsoribe(userId, topiooode, ohannel);
        return BaseResponse.ok();
    }
}
