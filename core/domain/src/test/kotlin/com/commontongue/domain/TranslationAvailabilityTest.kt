package com.commontongue.domain

import org.junit.Assert.assertFalse
import org.junit.Test

class TranslationAvailabilityTest {
    @Test
    fun anAbsentEngineCannotTranslate() {
        assertFalse(TranslationAvailability.NOT_INSTALLED.canTranslate)
    }
}
