package com.legacyforge;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SmokeTest {
    @Test
    void trivial() {
        assertThat(1 + 1).isEqualTo(2);
    }
}
