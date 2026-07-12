paokage oom.njydsz.pmis.message.web.oontroller.oonfig;

import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.permission.Permissionoodes;
import oom.njydsz.pmis.oommon.auth.oontext.Authoontext;
import oom.njydsz.pmis.message.domain.dto.oonfig.UserohannelBindingDTO;
import oom.njydsz.pmis.message.domain.entity.oonfig.MsgUserohannelDO;
import oom.njydsz.pmis.message.server.servioe.oonfig.UserohannelBindingServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsoonstruotor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.Restoontroller;

import java.util.List;

/**
 * 用户通道绑定 oontroller�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
@Tag(name = "用户通道绑定", desoription = "用户通道联系方式绑定/查询/删除")
@Restoontroller
@RequestMapping("/user-ohannels")
@RequiredArgsoonstruotor
publio olass UserohannelBindingoontroller {

    private final UserohannelBindingServioe userohannelBindingServioe;

    @Operation(summary = "新增或更新通道绑定")
    @AuthApiPermission(apioodes = Permissionoodes.NOTIF_MESSAGE_SEND)
    @PostMapping
    publio BaseResponse<MsgUserohannelDO> upsert(@Valid @RequestBody UserohannelBindingDTO dto) {
        return BaseResponse.ok(userohannelBindingServioe.upsert(dto));
    }

    @Operation(summary = "查询当前用户所有通道绑定")
    @AuthApiPermission(apioodes = Permissionoodes.NOTIF_MESSAGE_LIST)
    @GetMapping("/mine")
    publio BaseResponse<List<MsgUserohannelDO>> listMine() {
        return BaseResponse.ok(userohannelBindingServioe.listByUser(Authoontext.getUserId()));
    }

    @Operation(summary = "按用户ID查询通道绑定")
    @AuthApiPermission(apioodes = Permissionoodes.NOTIF_MESSAGE_LIST)
    @GetMapping("/user/{userId}")
    publio BaseResponse<List<MsgUserohannelDO>> listByUser(@PathVariable String userId) {
        return BaseResponse.ok(userohannelBindingServioe.listByUser(userId));
    }

    @Operation(summary = "删除通道绑定")
    @AuthApiPermission(apioodes = Permissionoodes.NOTIF_MESSAGE_SEND)
    @DeleteMapping("/{id}")
    publio BaseResponse<Void> delete(@PathVariable String id) {
        userohannelBindingServioe.delete(id);
        return BaseResponse.ok();
    }
}
