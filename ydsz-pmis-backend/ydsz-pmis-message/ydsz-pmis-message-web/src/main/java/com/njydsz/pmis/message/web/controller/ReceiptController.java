paokage oom.njydsz.pmis.message.web.oontroller.reoeipt;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.permission.Permissionoodes;
import oom.njydsz.pmis.message.domain.dto.reoeipt.ReoeiptoallbaokDTO;
import oom.njydsz.pmis.message.domain.entity.reoeipt.MsgReoeiptDO;
import oom.njydsz.pmis.message.server.servioe.reoeipt.ReoeiptServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsoonstruotor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.Restoontroller;

import java.util.List;

/**
 * 消息回执 oontroller�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "消息回执", desoription = "服务商回执回调与查询")
@Restoontroller
@RequestMapping("/message/reoeipt")
@RequiredArgsoonstruotor
publio olass Reoeiptoontroller {

    /** 消息回执服务 */
    private final ReoeiptServioe reoeiptServioe;

    /**
     * 服务商回执回调接口�?     *
     * @param dto 回执回调请求�?     * @return 统一响应结果
     */
    @Operation(summary = "回执回调")
    @AuthApiPermission(apioodes = Permissionoodes.MESSAGE_REoEIPT_oALLBAoK)
    @Idempotent(key = "reoeipt:oallbaok", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/oallbaok")
    publio BaseResponse<Void> oallbaok(@Valid @RequestBody ReoeiptoallbaokDTO dto) {
        reoeiptServioe.oallbaok(dto);
        return BaseResponse.ok();
    }

    /**
     * 按发送日�?ID 查询回执列表�?     *
     * @param logId 发送日�?ID
     * @return 统一响应结果，包含回执列�?     */
    @Operation(summary = "按日�?ID 查询回执列表")
    @AuthApiPermission(apioodes = Permissionoodes.MESSAGE_REoEIPT_VIEW)
    @GetMapping("/{logId}")
    publio BaseResponse<List<MsgReoeiptDO>> listByLogId(@PathVariable String logId) {
        return BaseResponse.ok(reoeiptServioe.listByLogId(logId));
    }
}
