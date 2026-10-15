package dev.dhun.android.lyrics

import dev.dhun.lyrics.RomanizationEngine
import dev.dhun.lyrics.RomanizationMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Same transform engine at the minimum API floor and current Android target.
 * Real-device glyph shaping/performance remains a separate acceptance check.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35])
class RomanizationApiTest {
    @Test fun bundledTransformsDoNotRequireNewAndroidIcuApis() = runBlocking {
        val engine = RomanizationEngine()
        assertEquals("kana", engine.romanize("カナ", RomanizationMode.JAPANESE_KANA))
        assertEquals("i", engine.romanize("и", RomanizationMode.RUSSIAN))
        assertEquals("y", engine.romanize("и", RomanizationMode.UKRAINIAN))
        assertEquals("你好", engine.romanize("你好", RomanizationMode.AUTO))
    }
}
