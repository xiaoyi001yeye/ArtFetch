package com.artfetch.util;

public final class TextSanitizer {

    private TextSanitizer() {
    }

    public static SanitizedText sanitize(String value) {
        if (value == null) {
            return new SanitizedText(null, false, 0);
        }

        StringBuilder cleaned = new StringBuilder(value.length());
        int removedIllegalChars = 0;

        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\u0000' || (Character.isISOControl(ch) && ch != '\n' && ch != '\r' && ch != '\t')) {
                removedIllegalChars++;
                continue;
            }
            cleaned.append(ch);
        }

        String sanitized = cleaned.toString().trim();
        if (sanitized.isBlank()) {
            sanitized = null;
        }

        boolean changed = removedIllegalChars > 0
                || !cleaned.toString().equals(value)
                || (sanitized == null ? !value.isBlank() : !sanitized.equals(value));

        return new SanitizedText(sanitized, changed, removedIllegalChars);
    }

    public record SanitizedText(String value, boolean changed, int removedIllegalChars) {
    }
}
