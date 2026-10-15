package dev.dhun.lyrics

import dev.dhun.core.LyricsLine
import dev.dhun.core.LyricsWord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SyncedLyricsCodecTest {
    @Test fun structuredPayloadPreservesLiteralMarkersQuotesBackslashesAndIndependentEnds() {
        val line = LyricsLine(1_000, "<00:02> \"مرحبا\" \\ 🎵", listOf(LyricsWord(0, 7, 1_000, 1_500)), 5_000)
        val encoded = SyncedLyricsCodec.encode(listOf(line))
        assertEquals(listOf(line), SyncedLyricsCodec.decode(encoded))
        assertEquals(listOf(line), SyncedLyricsCodec.decode(" \n" + encoded))
    }

    @Test fun oldPayloadsHaveNoInferredRolesAndMalformedMetadataIsRejected() {
        val old = """{"dhunSyncedLyrics":1,"lines":[{"start":1000,"text":"Old","words":[]}]}"""
        assertEquals(listOf(LyricsLine(1_000, "Old")), SyncedLyricsCodec.decode(old))
        val invalid = """{"dhunSyncedLyrics":2,"lines":[{"start":1000,"text":"A","words":[],"agent":"","background":true}]}"""
        assertTrue(SyncedLyricsCodec.decode(invalid).isEmpty())
        assertTrue(SyncedLyricsCodec.decode(invalid.replace("\"agent\":\"\"", "\"agent\":123")).isEmpty())
        assertTrue(SyncedLyricsCodec.decode(invalid.replace("\"agent\":\"\"", "\"agent\":\"v1\"").replace("true", "\"maybe\"")).isEmpty())
    }

    @Test fun corruptUnknownOversizeAndDeepPayloadsAreNotReinterpretedAsLyrics() {
        val bad = listOf("{broken", "{\"dhunSyncedLyrics\":99,\"lines\":[]}",
            "{\"dhunSyncedLyrics\":1,\"lines\":[{\"start\":1,\"text\":\"A\",\"end\":0,\"words\":[]}]}",
            "{\"dhunSyncedLyrics\":1,\"lines\":[{\"start\":1,\"text\":\"A\",\"words\":[{\"from\":0,\"to\":999999999999,\"start\":1}]}]}",
            "{\"dhunSyncedLyrics\":1,\"lines\":[{\"start\":1,\"text\":\"A\",\"words\":[{\"from\":0,\"to\":1,\"start\":0}]}]}",
            "{\"nested\":" + "[".repeat(10_000) + "0" + "]".repeat(10_000) + "}",
            "x".repeat(SyncedLyricsCodec.MAX_CHARS + 1))
        bad.forEach { assertTrue(SyncedLyricsCodec.decode(it).isEmpty()) }
        assertTrue(boundedLyricsJson("{\"literal\":\"[[[\\\"}}\"}"))
    }

    @Test fun invalidRangesOrBackwardsTimeCannotBeSavedAndLegacyEnhancedLrcStillDecodes() {
        assertFailsWith<IllegalArgumentException> {
            SyncedLyricsCodec.encode(listOf(LyricsLine(1_000, "Bad", listOf(LyricsWord(0, 99, 1_000)))))
        }
        assertFailsWith<IllegalArgumentException> {
            SyncedLyricsCodec.encode(listOf(LyricsLine(1_000, "Bad", listOf(LyricsWord(0, 2, 999)))))
        }
        assertFailsWith<IllegalArgumentException> {
            // Reject aggregate text before constructing a potentially huge JSON payload.
            SyncedLyricsCodec.encode(List(2_000) { LyricsLine(1_000, "x".repeat(1_000)) })
        }
        val lrc = "[00:01.123]<00:01.123>Hello <00:02.456>world<00:03.789>"
        assertEquals(LrcParser.parse(lrc, false), SyncedLyricsCodec.decode(lrc))
    }
}
