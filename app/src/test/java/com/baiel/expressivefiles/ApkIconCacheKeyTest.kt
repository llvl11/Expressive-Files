package com.baiel.expressivefiles

import com.baiel.expressivefiles.util.apkIconCacheKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The APK icon PNG is downscaled to the first requester's size before it is
 * cached, so the size bucket MUST be part of the key - otherwise a later
 * larger view (expressive grid vs list row) is served the small bitmap forever.
 */
class ApkIconCacheKeyTest {

    @Test fun sameFileAndSameSizeShareOneKey() {
        assertEquals(
            apkIconCacheKey("/sdcard/a.apk", 42L, 64, 64),
            apkIconCacheKey("/sdcard/a.apk", 42L, 64, 64)
        )
    }

    @Test fun aLargerRequestGetsItsOwnCacheFile() {
        assertNotEquals(
            apkIconCacheKey("/sdcard/a.apk", 42L, 64, 64),
            apkIconCacheKey("/sdcard/a.apk", 42L, 230, 230)
        )
        // One side undefined (-1) is its own bucket too.
        assertNotEquals(
            apkIconCacheKey("/sdcard/a.apk", 42L, -1, -1),
            apkIconCacheKey("/sdcard/a.apk", 42L, 64, -1)
        )
    }

    @Test fun mtimeAndPathStillInvalidateTheKey() {
        assertNotEquals(
            apkIconCacheKey("/sdcard/a.apk", 42L, 64, 64),
            apkIconCacheKey("/sdcard/a.apk", 43L, 64, 64)
        )
        assertNotEquals(
            apkIconCacheKey("/sdcard/a.apk", 42L, 64, 64),
            apkIconCacheKey("/sdcard/b.apk", 42L, 64, 64)
        )
    }
}
