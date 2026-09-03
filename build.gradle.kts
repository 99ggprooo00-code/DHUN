// DHUN root build file. Plugin versions are pinned HERE so subprojects apply
// them without repeating versions.
plugins {
    id("com.android.library") version "8.7.2" apply false
    id("com.android.application") version "8.7.2" apply false
    kotlin("android") version "2.1.20" apply false
    kotlin("multiplatform") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
    // Phase 04 desktop module (org.jetbrains.compose). NOTE: applied only
    // when :app-desktop is activated (see settings.gradle.kts). CI keeps it
    // OUT of the default build for now — see docs/verification/04-desktop.md
    // "CI configuration blocker".
    id("org.jetbrains.compose") version "1.8.2" apply false
    id("app.cash.sqldelight") version "2.1.0" apply false
}

// ---------------------------------------------------------------------------
// CI diagnostics without log access (companion to the hook in settings.gradle.kts).
// Kotlin compiler errors are logged at ERROR level as `e: file:///path:line:col msg`.
// Under GitHub Actions, re-emit them as `::error file=…,line=…::` workflow
// commands so they become check-run annotations (readable via REST API).
// ---------------------------------------------------------------------------
if (System.getenv("GITHUB_ACTIONS") == "true") {
    val pattern = Regex("""^e: (?:file://)?(/[^:]+):(\d+):(\d+)\s+(.*)$""")
    allprojects {
        tasks.configureEach {
            if (!name.startsWith("compile")) return@configureEach
            val listener = org.gradle.api.logging.StandardOutputListener { chunk ->
                chunk.toString().lineSequence().forEach { line ->
                    val m = pattern.find(line.trim())
                    if (m != null) {
                        val (file, l, c, msg) = m.destructured
                        val rel = file.removePrefix(rootDir.absolutePath + "/")
                        println("::error file=$rel,line=$l,col=$c,title=Kotlin::${msg.take(800)}")
                    } else if (line.startsWith("e: ")) {
                        println("::error title=Kotlin::${line.take(800)}")
                    }
                }
            }
            logging.addStandardErrorListener(listener)
            logging.addStandardOutputListener(listener)
        }
    }
}

// Failing tests → annotations (name + first lines of the failure message).
if (System.getenv("GITHUB_ACTIONS") == "true") {
    allprojects {
        tasks.withType<Test>().configureEach {
            addTestListener(object : TestListener {
                override fun beforeSuite(suite: TestDescriptor) {}
                override fun afterSuite(suite: TestDescriptor, result: TestResult) {}
                override fun beforeTest(testDescriptor: TestDescriptor) {}
                override fun afterTest(desc: TestDescriptor, result: TestResult) {
                    if (result.resultType != TestResult.ResultType.FAILURE) return
                    val ex = result.exception
                    val msg = (ex?.toString() ?: "failed").replace("\n", " ⏎ ").take(700)
                    val frame = ex?.stackTrace?.firstOrNull { it.className.startsWith("dev.dhun") }
                    println("::error title=Test failed: ${desc.className}.${desc.name}::$msg  @ ${frame ?: "?"}")
                }
            })
        }
    }
}
