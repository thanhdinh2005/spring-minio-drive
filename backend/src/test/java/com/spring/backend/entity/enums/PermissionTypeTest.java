package com.spring.backend.entity.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PermissionTypeTest {

    @ParameterizedTest
    @CsvSource({
            "NONE, NONE, NONE",
            "NONE, VIEW, VIEW",
            "NONE, EDIT, EDIT",
            "NONE, OWNER, OWNER",

            "VIEW, NONE, VIEW",
            "VIEW, VIEW, VIEW",
            "VIEW, EDIT, EDIT",
            "VIEW, OWNER, OWNER",

            "EDIT, NONE, EDIT",
            "EDIT, VIEW, EDIT",
            "EDIT, EDIT, EDIT",
            "EDIT, OWNER, OWNER",

            "OWNER, NONE, OWNER",
            "OWNER, VIEW, OWNER",
            "OWNER, EDIT, OWNER",
            "OWNER, OWNER, OWNER"
    })
    void max_returnsStrongerPermission(
            PermissionType first,
            PermissionType second,
            PermissionType expected
    ) {
        assertEquals(
                expected,
                PermissionType.max(first, second)
        );
    }
}