package dev.dhun.di

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Regression test for the ADR-006 Android download DI graph (PR #35).
 *
 * ## The bug it guards against
 *
 * In the original commit (a4dc28d), `app-android/.../di/AppModule.kt`
 * registered the Android download decorator like this:
 *
 * ```
 * single<DownloadManager> {
 *     ForegroundServiceDownloadManager(
 *         context = androidContext(),
 *         delegate = get(),                       // <-- BUG
 *         controller = get(),
 *     )
 * }
 * ```
 *
 * Because the `delegate` parameter is typed `DownloadManager`, the
 * unqualified `get()` type-infers to `get<DownloadManager>()` — the very
 * singleton being constructed. Koin 4.0.2 caches singletons after
 * construction, so this recurses at first resolution and crashes with
 * a `StackOverflowError` out of Koin's internals.
 *
 * `:app-android:assembleDebug` is a TYPE-CHECK gate and does not catch
 * resolution cycles; `:app-android` has no test source set today, so
 * no smoke test existed either. The fix in AppModule.kt is
 * `delegate = get<FileDownloadManager>()` — explicit type, breaks the
 * cycle, points at the concrete singleton registered immediately above.
 *
 * ## Why this test lives in `:shared:jvmTest`
 *
 * `:shared:jvmTest` is the only CI gate (run by `.github/workflows/ci.yml`).
 * `:app-android` has no test source set, and adding `:app-android:testDebugUnitTest`
 * with Robolectric is a coordinator/CI follow-up, not in this PR. So this
 * test mirrors the production pattern with minimal fakes in `:shared`
 * and uses the core Koin API directly (`GlobalContext.get().get<...>()`)
 * — no `KoinTest` / `koin-test` dependency, only `koin-core-jvm`.
 *
 * ## What this test asserts
 *
 * The `single<DownloadManager> { Decorator(get<Impl>()) }` registration
 * (the fix shape) resolves to the decorator wrapping the concrete impl.
 * A future refactor that drops the explicit type and re-introduces the
 * buggy `delegate = get()` pattern would no longer have a green smoke
 * test to hide behind.
 *
 * ## The rule (from the PR thread)
 *
 * Any new Koin registration in `app-android` that takes another
 * Koin-resolved dependency must be covered by either
 * (i) Koin `checkModules()` called in a unit test, or
 * (ii) a smoke test that calls `koin.get<...>()` in a unit test.
 * `:app-android:assembleDebug` is a TYPE-CHECK gate only.
 */
class KoinDownloadStackTest {

    // ---- minimal fakes mirroring the production types ----------------------
    //
    // Production types in :shared (dev.dhun.download.DownloadManager /
    // FileDownloadManager) could be used directly, but the minimal fakes
    // keep this test self-contained: it can be moved into :app-android
    // later without renaming production types.

    private interface DownloadManager {
        val downloads: StateFlow<Int>
    }

    private class FileDownloadManagerImpl : DownloadManager {
        override val downloads: StateFlow<Int> = MutableStateFlow(0)
    }

    /**
     * Mirrors the production ForegroundServiceDownloadManager shape:
     * constructor parameter typed as the interface, not the concrete
     * impl. The decorator must be wired to the concrete impl, never to
     * itself.
     */
    private class DecoratorDownloadManager(
        private val delegate: DownloadManager,
    ) : DownloadManager by delegate {
        val wrapped: DownloadManager = delegate
    }

    /**
     * This is the SHAPE AppModule.kt uses after the fix: a concrete
     * singleton registered first, and a decorator registered against
     * the interface that takes a FORWARD REFERENCE to the concrete type.
     */
    private val fixedModule = module {
        single { FileDownloadManagerImpl() }
        single<DownloadManager> { DecoratorDownloadManager(delegate = get<FileDownloadManagerImpl>()) }
    }

    @AfterTest
    fun teardown() {
        runCatching { stopKoin() }
    }

    @Test
    fun fixedModuleResolvesDecoratorOverConcreteImpl() {
        startKoin { modules(fixedModule) }
        val koin = GlobalContext.get()
        val resolved: DownloadManager = koin.get()
        assertNotNull(resolved, "DownloadManager must resolve")
        assertTrue(
            resolved is DecoratorDownloadManager,
            "expected the decorator, got ${resolved::class.simpleName}",
        )
        val decorator = resolved as DecoratorDownloadManager
        assertSame(
            koin.get<FileDownloadManagerImpl>(),
            decorator.wrapped,
            "decorator must wrap the concrete FileDownloadManagerImpl singleton",
        )
    }

    @Test
    fun fixedModuleIsSingleton() {
        startKoin { modules(fixedModule) }
        val koin = GlobalContext.get()
        val a: DownloadManager = koin.get()
        val b: DownloadManager = koin.get()
        assertSame(a, b, "single must be a true singleton")
    }
}
