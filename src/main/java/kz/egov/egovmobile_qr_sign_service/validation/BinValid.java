package kz.egov.egovmobile_qr_sign_service.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Аннотация для валидации БИН (Бизнес-идентификационный номер) Республики Казахстан
 * 
 * Проверяет:
 * - Формат: ровно 12 цифр
 * - Не может быть 000000000000
 * - Формальная валидация (без проверки контрольной суммы)
 */
@Documented
@Constraint(validatedBy = BinValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface BinValid {
    
    String message() default "Invalid BIN format. Must be 12 digits and not 000000000000";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
}
