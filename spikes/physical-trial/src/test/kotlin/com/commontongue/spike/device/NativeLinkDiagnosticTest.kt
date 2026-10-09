package com.commontongue.spike.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeLinkDiagnosticTest {
    @Test
    fun buildHostAndPrivatePathsExportOnlyTheKnownLibraryBasename() {
        for (path in
            listOf(
                "D:/private/account/native/libtrial_t5.so",
                "/data/app/private-id/libtrial_t5.so",
                "D:\\private\\libtrial_t5.so",
            )) {
            val details =
                NativeLinkDiagnostic.fromError(
                    UnsatisfiedLinkError(
                        "dlopen failed: library \"$path\" not found: needed by /data/private/libdevice_trial.so"
                    )
                )
            assertEquals(mapOf("native_missing_library" to "libtrial_t5.so"), details)
            assertFalse(details.toString().contains("private"))
        }
    }

    @Test
    fun missingSymbolExportsOnlyTheElfToken() {
        val details =
            NativeLinkDiagnostic.fromMessage(
                "dlopen failed: cannot locate symbol \"trial_t5_load\" referenced by \"/data/private/libdevice_trial.so\""
            )
        assertEquals(mapOf("native_missing_symbol" to "trial_t5_load"), details)
    }

    @Test
    fun wrappedInitializerFailureRetainsSafeMissingLibrary() {
        val error =
            ExceptionInInitializerError(
                UnsatisfiedLinkError("library \"libtrial_t5.so\" not found")
            )
        assertEquals(
            "libtrial_t5.so",
            NativeLinkDiagnostic.fromError(error)["native_missing_library"],
        )
    }

    @Test
    fun arbitraryMessagesUnknownLibrariesAndPathSymbolsRemainRedacted() {
        for (message in
            listOf(
                "private account name",
                "library \"private-phone.so\" not found",
                "cannot locate symbol \"/data/private/account\"",
                "cannot locate symbol \"private\\nidentifier\"",
            )) {
            assertTrue(NativeLinkDiagnostic.fromMessage(message).isEmpty())
        }
        assertTrue(
            NativeLinkDiagnostic.validate("/data/private/libtrial_t5.so", "/private/symbol")
                .isEmpty()
        )
    }

    @Test
    fun symbolLengthAndLibraryAllowlistApplyAgainAtExport() {
        assertTrue(NativeLinkDiagnostic.validate("libprivate.so", "x".repeat(129)).isEmpty())
        assertEquals(
            mapOf(
                "native_missing_library" to "libc++_shared.so",
                "native_missing_symbol" to "__emutls_get_address",
            ),
            NativeLinkDiagnostic.validate("libc++_shared.so", "__emutls_get_address"),
        )
    }
}
