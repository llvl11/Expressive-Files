package com.baiel.expressivefiles

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.baiel.expressivefiles.viewmodel.localizedArchiveError
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Lives outside FileViewModelRegressionTest on purpose: that class installs a
 * Main test dispatcher, and a pure string-mapping test finishes before the
 * ViewModel's init jobs go idle - racing tearDown's resetMain.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LocalizedArchiveErrorTest {

    @Test fun ourFixedFallbackErrorsAreLocalizedButExceptionTextPassesThrough() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        // The engine's hardcoded English fallbacks must not reach a Russian
        // toast/notification as-is.
        assertEquals(
            app.getString(R.string.archive_error_unknown),
            localizedArchiveError(app, "Unknown error while extracting")
        )
        assertEquals(
            app.getString(R.string.archive_error_unknown),
            localizedArchiveError(app, "Unknown compression error")
        )
        // Third-party text stays as-is: exception messages are not ours.
        assertEquals(
            "ENOSPC: no space left on device",
            localizedArchiveError(app, "ENOSPC: no space left on device")
        )
    }
}
