package com.commontongue.prototype.platform

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.commontongue.local.CoreResourceRole
import com.commontongue.local.LockedCore
import com.commontongue.local.ValidatedCoreResources
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstalledResourceReuseTest {
    @Test
    fun productAndDebugSetupResolveTheSameExistingPackWithoutAcquiringOrMovingFiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val snapshot =
            ValidatedCoreResources("debug-core-v1", LockedCore.VERSION, LockedCore.identities)
        val product = InstalledCoreResources(context).source.resolve(snapshot)
        val setup = TestResources(context).source.resolve(snapshot)
        assertEquals(setup, product)
        CoreResourceRole.entries.forEach { role ->
            assertEquals(
                File(context.filesDir, "local-core/${role.name.lowercase()}"),
                product.getValue(role),
            )
        }
        // Resolution is metadata-only. Actual loading separately stream-validates locked hashes.
    }
}
