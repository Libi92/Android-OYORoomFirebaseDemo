package com.application.pglocator.constants;

public enum UserType {
    USER("User"),
    PG("PG"),
    ADMIN("Admin");

    private final String value;

    UserType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
