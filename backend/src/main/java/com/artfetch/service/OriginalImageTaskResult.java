package com.artfetch.service;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OriginalImageTaskResult {
    private int totalCount;
    private int processedCount;
    private int downloadedCount;
    private int skippedCount;
    private int failedCount;
}
