package dev.dhun.extraction

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalAudioFileUriTest {
    @Test
    fun windowsDriveAndSpacesAreNotAnAuthority() {
        val uri = URI(localAudioFileUri("C:\\Program Files\\DHUN\\audio\\song.webm"))
        assertEquals("file:///C:/Program%20Files/DHUN/audio/song.webm", uri.toASCIIString())
        assertNull(uri.authority)
        assertEquals("/C:/Program Files/DHUN/audio/song.webm", uri.path)
    }

    @Test
    fun reservedCharactersAndUnicodeRoundTripAsPathNotQueryOrFragment() {
        val path = "C:/Users/नाम/Music/100% #1? +.m4a"
        val uri = URI(localAudioFileUri(path))
        assertEquals("/$path", uri.path)
        assertNull(uri.query)
        assertNull(uri.fragment)
        assertEquals(uri.toASCIIString(), localAudioFileUri(path))
    }

    @Test
    fun uncShareKeepsServerAndShare() {
        val uri = URI(localAudioFileUri("\\\\server\\Music Share\\song.webm"))
        assertEquals("server", uri.authority)
        assertEquals("/Music Share/song.webm", uri.path)
    }

    @Test
    fun androidAndUnixPathsAreStillLocalAndEncodedOnce() {
        val path = "/data/user/0/dev.dhun/files/100%20 #song.webm"
        val uri = URI(localAudioFileUri(path))
        assertNull(uri.authority)
        assertEquals(path, uri.path)
        assertNull(uri.fragment)
    }

    @Test
    fun unixBackslashRemainsALiteralFilenameCharacter() {
        val path = "/tmp/a\\b.webm"
        assertEquals(path, URI(localAudioFileUri(path)).path)
    }
}
