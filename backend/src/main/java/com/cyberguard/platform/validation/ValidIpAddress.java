package com.cyberguard.platform.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a String is a literal IPv4 or IPv6 address. Purely textual
 * (regex-based) - never performs a DNS lookup, so it can't hang or behave
 * differently depending on network/DNS availability.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = IpAddressValidator.class)
public @interface ValidIpAddress {
    String message() default "Must be a valid IPv4 or IPv6 address";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
