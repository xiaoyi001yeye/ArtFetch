package com.artfetch.service;

import lombok.Getter;

@Getter
public class FetchRunResult {

    private int totalNewCount;
    private int failedListPages;
    private int failedDetailItems;
    private boolean completedAllPages;
    private String fatalErrorMessage;

    public static FetchRunResult completed(int totalNewCount, int failedListPages, int failedDetailItems) {
        FetchRunResult result = new FetchRunResult();
        result.totalNewCount = totalNewCount;
        result.failedListPages = failedListPages;
        result.failedDetailItems = failedDetailItems;
        result.completedAllPages = true;
        return result;
    }

    public static FetchRunResult incomplete(int totalNewCount,
                                            int failedListPages,
                                            int failedDetailItems,
                                            String fatalErrorMessage) {
        FetchRunResult result = new FetchRunResult();
        result.totalNewCount = totalNewCount;
        result.failedListPages = failedListPages;
        result.failedDetailItems = failedDetailItems;
        result.completedAllPages = false;
        result.fatalErrorMessage = fatalErrorMessage;
        return result;
    }
}
