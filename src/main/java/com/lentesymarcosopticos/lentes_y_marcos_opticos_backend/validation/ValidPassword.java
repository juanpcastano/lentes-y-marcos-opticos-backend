package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = ValidPasswordValidator.class)
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

	String message() default "La contraseña debe tener al menos 8 caracteres y una mayúscula";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
