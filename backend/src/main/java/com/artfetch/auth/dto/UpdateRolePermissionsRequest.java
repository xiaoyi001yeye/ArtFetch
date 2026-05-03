package com.artfetch.auth.dto;

import java.util.Set;

public record UpdateRolePermissionsRequest(Set<String> permissions) {
}
