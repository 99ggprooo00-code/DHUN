package dev.dhun

/**
 * MUTATION PROBE — session `arena/8be68e2c-dhun`. DELETE AFTER THE CI VERDICT.
 *
 * `NotificationChannel` is API 26. `minSdk` is 24. This call is deliberately
 * **unguarded**, so the `NewApi` rule in `:shared:lintDebug` must fail the
 * `Android Lint — shared androidMain API 24 floor (NewApi)` CI step.
 *
 * Why this file exists: a lint run that analyses nothing is also green, so the
 * step added in this session proves nothing until a violation in
 * `shared/src/androidMain` turns it red. Precedent: PR #134's `74341d0`, which
 * proved the `:app-android` half the same way. Revert this commit once the red
 * run is recorded in the ROADMAP ledger.
 */
internal object LintMutationProbe {
    fun unguardedApi26Channel(): android.app.NotificationChannel =
        android.app.NotificationChannel(
            "dev.dhun.probe",
            "DHUN lint mutation probe",
            android.app.NotificationManager.IMPORTANCE_LOW,
        )
}
