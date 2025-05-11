package com.siemens.internship.validator;

/**
 * I added this Validator<T> interface to set a common rule for validation,
 * making it easy to add new validators for different models.
 */

public interface Validator<T> {
    public void validate(T t);
}
