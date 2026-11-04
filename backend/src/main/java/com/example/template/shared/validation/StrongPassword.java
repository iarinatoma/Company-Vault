package com.example.template.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The one password policy of the application. Every request that sets a password (admin
 * create/update, self-registration, password reset) puts this on its password field, so the
 * rules live in exactly one place: {@link StrongPasswordValidator}.
 *
 * <p>{@code null} is valid — combine with {@code @NotBlank} where the password is required.
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "must be 12-128 characters long, contain at least one letter and one digit, "
            + "and must not be a common password";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}