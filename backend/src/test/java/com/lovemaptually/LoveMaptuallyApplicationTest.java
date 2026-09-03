package com.lovemaptually;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoveMaptuallyApplicationTest {

    @Test
    void applicationEntryPointExists() {
        assertThat(LoveMaptuallyApplication.class).isNotNull();
    }
}
