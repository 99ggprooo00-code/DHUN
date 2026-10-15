# On-device lyrics translation review — 2026-10-15

## Owner decisions

The owner explicitly chose **on-device-only translation** and **owner testing on
physical Android and Windows devices**. Remote DeepL/LLM translation is not approved;
there must be no remote fallback, API-key setting or silent lyric upload. Physical
acceptance has not been replaced with emulator/VM acceptance. Keep PR #160 open.

## Source reuse

Inspected the current translation helpers, service adapters and AI settings in:

- https://github.com/99ggprooo00-code/Metrolist-test
- https://github.com/vivizzz007/vivi-music

The inspected translation paths send text to DeepL or configured LLM endpoints,
then attach translated strings to existing timed entries, with streaming, memory
caching and database persistence. They are useful behavior references, not an
on-device inference implementation. Reusing their HTTP path would violate the
chosen privacy policy. DHUN's new offline engine is independently implemented.

Metrolist-test's root license is GPL-3.0. Vivi's root LICENSE contains GPLv3 plus a
special proprietary Musixmatch-module restriction. Do not treat all files as
freely reusable or copy that restricted module. Any future reuse needs a
file-specific license/provenance check and preserved notices; public visibility
is not itself permission. No source was copied in this slice.

## Local runtime/model candidates (review, not approval or shipped dependencies)

### ML Kit

Sources:

- https://developers.google.com/ml-kit/language/translation
- https://developers.google.com/ml-kit/language/translation/android
- https://developers.google.com/ml-kit/language/translation/translation-terms

The documented Android SDK runs translation on device. Its Android guide currently
uses `com.google.mlkit:translate:17.0.3`, describes approximately 30 MB language
models, explicit download/deletion, Wi-Fi download conditions, and closing translators.
Non-English pairs pivot through English and quality must be evaluated for the use
case. Usage guidelines require Google translation attribution and restrict embedded
device use. A developer-page code-sample license is not a blanket model/SDK license.

Not selected yet: the reviewed guide is Android-specific and does not establish a
supported JVM/Windows adapter. Remaining SDK terms, telemetry disclosures, exact
language support and physical performance must be reviewed before adding it. Never
call downloadModelIfNeeded from an inference method: acquisition needs separate
user consent and a remove-model action.

### ONNX Runtime + OPUS/Marian

Sources:

- https://onnxruntime.ai/docs/get-started/with-java.html
- https://github.com/microsoft/onnxruntime/blob/main/LICENSE
- https://huggingface.co/Helsinki-NLP/opus-mt-en-hi/raw/main/README.md
- https://huggingface.co/Xenova/opus-mt-en-hi/raw/main/README.md

The Java runtime guide lists a CPU artifact for Windows x64/Linux x64/macOS x64;
the runtime repository uses MIT. This does not establish Android ABI support or
model compatibility. The Helsinki en-hi card declares Apache-2.0, English-to-Hindi,
normalization + SentencePiece, and dated benchmark results. It is not a claim of
lyric translation quality or reverse-direction support. The Xenova card identifies
an ONNX conversion of that model but does not itself supply a full licensing or
artifact-integrity review. A guessed en-ne model URL returned an authentication/
unavailable response: **Nepali support is not established**.

Before integration: choose immutable model/runtime revisions; review actual model
license/notice files; record exact artifact sizes and SHA-256 hashes; verify tokenizer,
normalization, graph input/output and generation contracts against real inference;
bound token counts, native memory, threads and runtime; test Android ABI/API-floor
and packaged Windows native loading. Native inference must support cancellation or
be isolated so timeout/cancellation does not permit overlapping abandoned sessions.
No runtime dependency or model weights are added in this slice.

## Shared pipeline implemented, not an enabled translation feature

`OfflineTranslation.kt` defines a local-only provider boundary and a cold manual
pipeline. It has explicit source/target validation, bounded input/output, fixed
batches, serialized provider calls, partial line slots, timeout/cancellation handling,
track/source snapshot checks and a bounded process-memory-only LRU. Exact content,
track, languages and model revision participate in cache identity. Clearing cache
invalidates in-flight work. Partial failures are not cached. Output overlays do not
rewrite original text, timestamps, words or roles. Provider errors are not exposed
as raw exception messages. Tests use synthetic local fakes, not real models.

No player/settings wiring, download UI, real inference, model license approval,
language-quality acceptance or device performance result is claimed yet. A coroutine
timeout alone cannot interrupt non-cooperative native code; the eventual adapter
must enforce the runtime lifecycle contract rather than relying on this alone.

## Completion/physical acceptance gates

After real model integration, supply builds and a checklist to the owner covering:

- Default-off behavior; no model download until explicit size/network consent;
  removal/low-storage failure and no background reacquisition.
- Real translations with networking blocked after model acquisition. Compare
  supported language pairs with a fluent reader; unsupported pairs must stay explicit.
- Original lyrics/timing/word seek/roles unchanged, including repeated and blank lines,
  Devanagari/RTL/emoji shaping, foreground/backing overlaps and romanization toggles.
- Track, source and target changes, cancel, clear cache, app restart and model removal
  during inference; no stale overlays or persistence of translation text to disk.
- Android low-memory/battery/frame behavior, TalkBack and large text; Windows native
  loading, keyboard focus, screen-reader behavior and resource release.

Collect actual device/OS/build/model identities and observed results. CI, packaging
and fake-provider tests are supporting evidence, not physical or linguistic acceptance.
