package com.artfetch.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextSanitizerTest {

    @Test
    void removesNullBytesAndIllegalControlCharacters() {
        TextSanitizer.SanitizedText sanitized = TextSanitizer.sanitize("齐白石\u0000作品\u001F");

        assertThat(sanitized.value()).isEqualTo("齐白石作品");
        assertThat(sanitized.changed()).isTrue();
        assertThat(sanitized.removedIllegalChars()).isEqualTo(2);
    }

    @Test
    void keepsAllowedWhitespaceCharacters() {
        TextSanitizer.SanitizedText sanitized = TextSanitizer.sanitize("  尺寸\t\n68cm  ");

        assertThat(sanitized.value()).isEqualTo("尺寸\t\n68cm");
        assertThat(sanitized.removedIllegalChars()).isZero();
    }

    @Test
    void returnsNullWhenOnlyIllegalOrBlankCharactersRemain() {
        TextSanitizer.SanitizedText sanitized = TextSanitizer.sanitize("\u0000 \u0007 ");

        assertThat(sanitized.value()).isNull();
        assertThat(sanitized.changed()).isTrue();
        assertThat(sanitized.removedIllegalChars()).isEqualTo(2);
    }
}
