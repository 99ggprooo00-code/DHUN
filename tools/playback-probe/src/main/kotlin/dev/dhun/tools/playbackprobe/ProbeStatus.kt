package dev.dhun.tools.playbackprobe

import dev.dhun.core.DhunError
import dev.dhun.core.detailString

/**
 * Health status emitted by the live probe.
 *
 * These statuses describe what the probe can establish from the runner; they
 * do not turn an unavailable external service into a passing extraction test.
 * The workflow keeps non-PASS statuses non-zero, while using the distinction to
 * avoid filing a DHUN parser/resolver bug for a known upstream runner gate.
 */
enum class ProbeStatus {
    PASS,
    FAIL,
    ENVIRONMENT_BLOCKED,
    UNAVAILABLE,
}

/**
 * Classifies a resolver failure without changing the shared production error
 * taxonomy. In particular, an AuthRequired response carrying YouTube's
 * LOGIN_REQUIRED/bot challenge evidence is an environment signal, not proof
 * that the resolver implementation is broken.
 */
internal fun classifyResolverFailure(error: DhunError): ProbeStatus = when {
    isRunnerBotGate(error) -> ProbeStatus.ENVIRONMENT_BLOCKED
    error is DhunError.Network || error is DhunError.RateLimited -> ProbeStatus.UNAVAILABLE
    error is DhunError.AuthRequired || error is DhunError.Unavailable -> ProbeStatus.UNAVAILABLE
    else -> ProbeStatus.FAIL
}

/**
 * Only classify explicit YouTube gate evidence as environment-blocked.
 * A bare AuthRequired remains UNAVAILABLE because it may describe content
 * requiring a signed-in session rather than a datacenter IP gate.
 */
internal fun isRunnerBotGate(error: DhunError): Boolean {
    if (error !is DhunError.AuthRequired) return false
    val evidence = buildString {
        append(error.toString())
        append(' ')
        append(error.detailString().orEmpty())
    }.uppercase()
    return BOT_GATE_MARKERS.any { marker -> marker in evidence }
}

private val BOT_GATE_MARKERS = listOf(
    "LOGIN_REQUIRED",
    "SIGN IN TO CONFIRM",
    "NOT A BOT",
    "BOT DETECTED",
    "BOT-GATING",
)
