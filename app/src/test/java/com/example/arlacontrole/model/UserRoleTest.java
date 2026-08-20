package com.example.arlacontrole.model;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UserRoleTest {

    @Test
    public void adminCanImportSafetyOccurrences() {
        assertTrue(UserRole.canImportSafetyOccurrences(UserRole.ADMIN));
    }

    @Test
    public void nonAdminCannotImportSafetyOccurrences() {
        assertFalse(UserRole.canImportSafetyOccurrences(UserRole.OPERACIONAL));
        assertFalse(UserRole.canImportSafetyOccurrences(UserRole.MOTORISTA));
        assertFalse(UserRole.canImportSafetyOccurrences(null));
    }
}
