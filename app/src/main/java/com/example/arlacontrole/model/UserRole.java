package com.example.arlacontrole.model;

public final class UserRole {
    public static final String MOTORISTA = "MOTORISTA";
    public static final String OPERACIONAL = "OPERACIONAL";
    public static final String ADMIN = "ADMIN";

    private UserRole() {
    }

    public static boolean isDriver(String role) {
        return MOTORISTA.equals(role);
    }

    public static boolean canAccessManagement(String role) {
        return OPERACIONAL.equals(role) || ADMIN.equals(role);
    }

    public static boolean canExportReports(String role) {
        return OPERACIONAL.equals(role) || ADMIN.equals(role);
    }

    public static boolean canAccessSettings(String role) {
        return ADMIN.equals(role);
    }

    public static boolean canImportSafetyOccurrences(String role) {
        return ADMIN.equals(role);
    }
}
