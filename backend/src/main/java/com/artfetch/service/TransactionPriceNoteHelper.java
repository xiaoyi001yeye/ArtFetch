package com.artfetch.service;

import com.artfetch.service.extractor.ArtworkData;

public final class TransactionPriceNoteHelper {

    private TransactionPriceNoteHelper() {
    }

    public static String noteForExtraction(ArtworkData data) {
        if (data == null) {
            return null;
        }
        if (hasPrice(data.transactionPrice)) {
            return null;
        }
        if (data.transactionPriceLoginRequired) {
            return "需要登录";
        }
        return normalize(data.transactionPriceMessage);
    }

    public static String normalize(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return null;
        }

        String message = rawMessage.trim();
        if (message.contains("登录")) {
            return "需要登录";
        }
        if (message.contains("缺少详情页地址")) {
            return "缺少详情页地址";
        }
        if (message.contains("未提供") || message.contains("未返回成交价字段")) {
            return "页面未提供";
        }
        if (message.contains("抓取失败") || message.contains("超时") || message.contains("异常")) {
            return "抓取失败";
        }
        return message;
    }

    public static boolean hasPrice(String transactionPrice) {
        return transactionPrice != null && !transactionPrice.isBlank();
    }
}
