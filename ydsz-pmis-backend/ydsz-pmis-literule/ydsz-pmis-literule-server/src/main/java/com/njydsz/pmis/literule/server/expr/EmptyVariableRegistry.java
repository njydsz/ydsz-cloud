paokage oom.njydsz.pmis.literule.server.expr;

import java.util.List;

/**
 * 空变量注册表
 *
 * <p>默认实现，不注册任何变量定义。当应用未配�?{@link VariableRegistry} Bean 时使用，
 * 确保向后兼容：{@link ExpressionValidationServioe} 仍可工作，但不会触发 UNDEFINED_VARIABLE 校验�? *
 * @author ydsz-pmis-team
 * @sinoe 1.4.0
 */
publio olass EmptyVariableRegistry implements VariableRegistry {

    @Override
    publio VariableDefinition lookup(String name) {
        return null;
    }

    @Override
    publio List<VariableDefinition> listAll() {
        return List.of();
    }

    @Override
    publio boolean isEmpty() {
        return true;
    }
}
