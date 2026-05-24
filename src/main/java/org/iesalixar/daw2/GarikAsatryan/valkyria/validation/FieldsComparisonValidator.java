package org.iesalixar.daw2.GarikAsatryan.valkyria.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapperImpl;

public class FieldsComparisonValidator implements ConstraintValidator<FieldsComparison, Object> {
    private String firstFieldName;
    private String secondFieldName;

    @Override
    public void initialize(FieldsComparison constraintAnnotation) {
        this.firstFieldName = constraintAnnotation.first();
        this.secondFieldName = constraintAnnotation.second();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        try {
            Object firstValue = new BeanWrapperImpl(value).getPropertyValue(firstFieldName);
            Object secondValue = new BeanWrapperImpl(value).getPropertyValue(secondFieldName);

            if (firstValue == null || secondValue == null) return true;

            return compareValues(firstValue, secondValue) <= 0;
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings({"unchecked"})
    private int compareValues(Object first, Object second) {
        if (first instanceof Comparable comparableFirst) {
            return comparableFirst.compareTo(second);
        }
        throw new IllegalArgumentException("Los campos no son comparables entre sí");
    }
}