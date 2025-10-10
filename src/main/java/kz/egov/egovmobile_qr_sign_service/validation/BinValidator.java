package kz.egov.egovmobile_qr_sign_service.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BinValidator implements ConstraintValidator<BinValid, String> {

    private static final String BIN_PATTERN = "^\\d{12}$";
    private static final String INVALID_BIN = "000000000000";

    @Override
    public void initialize(BinValid constraintAnnotation) {
    }

    @Override
    public boolean isValid(String bin, ConstraintValidatorContext context) {
        if (bin == null) {
            return true;
        }

        if (bin.trim().isEmpty()) {
            log.debug("BIN validation failed: empty string");
            return false;
        }
        if (!bin.matches(BIN_PATTERN)) {
            log.debug("BIN validation failed: invalid format '{}' (must be 12 digits)", bin);
            return false;
        }
        if (INVALID_BIN.equals(bin)) {
            log.debug("BIN validation failed: invalid value '{}'", bin);
            return false;
        }
        if (bin.startsWith("0000")) {
            log.warn("BIN validation warning: BIN '{}' starts with 0000 (suspicious)", bin);
        }

        log.debug("BIN validation successful: '{}'", bin);
        return true;
    }
}
