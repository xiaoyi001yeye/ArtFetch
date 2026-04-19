package com.artfetch.service;

import lombok.Getter;

@Getter
public class DetailFetchResult {

    private final boolean success;
    private final long durationMs;

    private DetailFetchResult(boolean success, long durationMs) {
        this.success = success;
        this.durationMs = durationMs;
    }

    public static DetailFetchResult success(long durationMs) {
        return new DetailFetchResult(true, durationMs);
    }

    public static DetailFetchResult failure(long durationMs) {
        return new DetailFetchResult(false, durationMs);
    }
}
