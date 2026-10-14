package com.chainflow.gateway.filter;

import org.springframework.http.HttpMethod;

import java.util.Set;

/**
 * The role boundaries for public business write operations exposed by the
 * gateway. Service-to-service requests remain on the private service network.
 */
final class BusinessWriteAuthorization {
    private static final Set<HttpMethod> WRITE_METHODS = Set.of(
            HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);
    private static final Set<String> ALL_BUSINESS_ROLES = Set.of("ADMIN", "SUPPLY_CHAIN_OPERATOR");

    private BusinessWriteAuthorization() {}

    static boolean isAllowed(HttpMethod method, String path, String role) {
        if (!WRITE_METHODS.contains(method)) {
            return true;
        }
        String requiredRole = requiredRole(path);
        if (requiredRole == null) {
            return true;
        }
        return role != null && (ALL_BUSINESS_ROLES.contains(role) || requiredRole.equals(role));
    }

    private static String requiredRole(String path) {
        if (belongsTo(path, "/api/v1/purchases")) {
            return path.matches("/api/v1/purchases/[^/]+/receive/?") ? "WAREHOUSE" : "PROCUREMENT";
        }
        if (belongsTo(path, "/api/v1/inventory")) {
            return "WAREHOUSE";
        }
        if (belongsTo(path, "/api/v1/orders")) {
            return "SALES";
        }
        if (belongsTo(path, "/api/v1/fulfillments")) {
            return "WAREHOUSE";
        }
        return null;
    }

    private static boolean belongsTo(String path, String basePath) {
        return path.equals(basePath) || path.startsWith(basePath + "/");
    }
}
