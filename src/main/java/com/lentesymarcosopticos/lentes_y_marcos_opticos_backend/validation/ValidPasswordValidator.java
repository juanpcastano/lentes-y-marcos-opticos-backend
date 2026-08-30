package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidPasswordValidator implements ConstraintValidator<ValidPassword, String> {

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null || value.isBlank()) {
			return false;
		}
		if (value.length() < 8) {
			return false;
		}
		if (!value.chars().anyMatch(c -> Character.isUpperCase(c))) {
			return false;
		}
		return true;
	}
}
