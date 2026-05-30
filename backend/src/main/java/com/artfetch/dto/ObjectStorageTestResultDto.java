package com.artfetch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ObjectStorageTestResultDto {
    private boolean success;
    private String message;
}
