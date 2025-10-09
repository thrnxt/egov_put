package kz.egov.egovmobile_qr_sign_service.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Валидатор для БИН (Бизнес-идентификационный номер) Республики Казахстан
 * 
 * Выполняет формальную валидацию:
 * - Проверка формата: ровно 12 цифр
 * - Проверка на 000000000000 (недопустимое значение)
 * 
 * Примечание: Контрольная сумма БИН не проверяется (требует точного алгоритма РК)
 */
@Slf4j
public class BinValidator implements ConstraintValidator<BinValid, String> {

    private static final String BIN_PATTERN = "^\\d{12}$";
    private static final String INVALID_BIN = "000000000000";

    @Override
    public void initialize(BinValid constraintAnnotation) {
        // Инициализация не требуется
    }

    @Override
    public boolean isValid(String bin, ConstraintValidatorContext context) {
        // Null значения обрабатываются другими аннотациями (@NotNull, @NotBlank)
        if (bin == null) {
            return true;
        }

        // Пустая строка - невалидна
        if (bin.trim().isEmpty()) {
            log.debug("BIN validation failed: empty string");
            return false;
        }

        // Проверка формата: ровно 12 цифр
        if (!bin.matches(BIN_PATTERN)) {
            log.debug("BIN validation failed: invalid format '{}' (must be 12 digits)", bin);
            return false;
        }

        // Проверка на недопустимое значение 000000000000
        if (INVALID_BIN.equals(bin)) {
            log.debug("BIN validation failed: invalid value '{}'", bin);
            return false;
        }

        // Дополнительные проверки (опционально)
        if (bin.startsWith("0000")) {
            log.warn("BIN validation warning: BIN '{}' starts with 0000 (suspicious)", bin);
            // Не блокируем, но предупреждаем в логах
        }

        log.debug("BIN validation successful: '{}'", bin);
        return true;
    }
}
