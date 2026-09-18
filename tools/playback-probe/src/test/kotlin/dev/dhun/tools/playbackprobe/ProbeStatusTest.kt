package dev.dhun.tools.playbackprobe

import dev.dhun.core.DhunError
import kotlin.test.Test
import kotlin.test.assertEquals

class ProbeStatusTest {
    @Test
    fun loginRequiredBotChallengeIsEnvironmentBlocked() {
        val error = DhunError.AuthRequired(
            detail = "web_remix=LOGIN_REQUIRED(Sign in to confirm you're not a bot)",
        )

        assertEquals(ProbeStatus.ENVIRONMENT_BLOCKED, classifyResolverFailure(error))
    }

    @Test
    fun bareAuthRequiredIsUnavailableNotAParserFailure() {
        assertEquals(
            ProbeStatus.UNAVAILABLE,
            classifyResolverFailure(DhunError.AuthRequired()),
        )
    }

    @Test
    fun networkAndRateLimitAreUnavailable() {
        assertEquals(ProbeStatus.UNAVAILABLE, classifyResolverFailure(DhunError.Network()))
        assertEquals(ProbeStatus.UNAVAILABLE, classifyResolverFailure(DhunError.RateLimited()))
    }

    @Test
    fun parseFailureRemainsARealProbeFailure() {
        assertEquals(
            ProbeStatus.FAIL,
            classifyResolverFailure(DhunError.Parse("unexpected response shape")),
        )
    }
}
