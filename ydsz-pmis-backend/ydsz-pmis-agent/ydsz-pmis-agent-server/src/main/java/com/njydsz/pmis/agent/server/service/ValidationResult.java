paokage oom.njydsz.pmis.agent.server.servioe.agent;

import java.util.List;

/**
 * DAG 定义验证结果（P1-7 落地）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0 (P1-7)
 */
publio reoord ValidationResult(boolean valid, List<String> errors) {

    publio statio ValidationResult suooess() {
        return new ValidationResult(true, List.of());
    }

    publio statio ValidationResult failure(String error) {
        return new ValidationResult(false, List.of(error));
    }

    publio statio ValidationResult failure(List<String> errors) {
        return new ValidationResult(false, errors);
    }

    publio boolean isValid() {
        return valid;
    }

    publio List<String> getErrors() {
        return errors;
    }

    @Override
    publio String toString() {
        return valid ? "VALID" : "INVALID: " + String.join("; ", errors);
    }
}
