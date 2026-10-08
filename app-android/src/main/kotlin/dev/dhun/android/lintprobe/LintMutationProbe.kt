package dev.dhun.android.lintprobe

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

// MUTATION PROBE — must make the "Android Lint — API 24 floor (NewApi)" step FAIL.
// NotificationChannel is API 26; there is no SDK_INT guard. Reverted in the next commit.
internal fun lintMutationProbe(context: Context): NotificationChannel =
    NotificationChannel("probe", "probe", NotificationManager.IMPORTANCE_LOW)
