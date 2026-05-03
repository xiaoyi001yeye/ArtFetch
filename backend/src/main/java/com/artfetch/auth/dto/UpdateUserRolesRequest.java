package com.artfetch.auth.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record UpdateUserRolesRequest(@NotEmpty Set<String> roles) {
}
