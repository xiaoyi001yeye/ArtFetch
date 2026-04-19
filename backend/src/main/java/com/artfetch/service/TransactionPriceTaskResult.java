package com.artfetch.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class TransactionPriceTaskResult {

    private final int totalCount;
    private final int processedCount;
    private final int updatedCount;
    private final int loginRequiredCount;
    private final int missingCount;
    private final int failedCount;
}
