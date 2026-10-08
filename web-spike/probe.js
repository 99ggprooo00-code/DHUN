"use strict";

const PROBE_REVISION = "b1-v1";
const REQUEST_TIMEOUT_MS = 15000;
const MAX_SAFE_BYTE_PROBE = 1024 * 1024;
const VIDEO_ID_PATTERN = /^[A-Za-z0-9_-]{11}$/;

const PLAYER_PROFILE = Object.freeze({
  label: "web_remix",
  endpoint: "https://music.youtube.com/youtubei/v1/player?prettyPrint=false",
  clientName: "WEB_REMIX",
  clientHeaderId: "67",
  clientVersion: "1.20250310.01.00",
});

const elements = Object.freeze({
  origin: document.querySelector("#originValue"),
  videoId: document.querySelector("#videoId"),
  run: document.querySelector("#runProbe"),
  play: document.querySelector("#playResolved"),
  heard: document.querySelector("#heardAudio"),
  reset: document.querySelector("#resetProbe"),
  copy: document.querySelector("#copyResult"),
  audio: document.querySelector("#probeAudio"),
  hint: document.querySelector("#playerHint"),
  overall: document.querySelector("#overallState"),
  json: document.querySelector("#resultJson"),
  timeline: document.querySelector("#timeline"),
  cards: {
    metadata: document.querySelector("#metadataCard"),
    player: document.querySelector("#playerCard"),
    range: document.querySelector("#rangeCard"),
    media: document.querySelector("#mediaCard"),
  },
  results: {
    metadata: document.querySelector("#metadataResult"),
    player: document.querySelector("#playerResult"),
    range: document.querySelector("#rangeResult"),
    media: document.querySelector("#mediaResult"),
  },
});

let report = createReport();
let activeStreamUrl = null;
let runSequence = 0;

function createReport() {
  return {
    probe: "ADR-008-B1",
    revision: PROBE_REVISION,
    status: "NOT_RUN",
    origin: window.location.origin,
    pagePath: window.location.pathname,
    startedAt: null,
    updatedAt: new Date().toISOString(),
    browser: {
      userAgent: navigator.userAgent,
      language: navigator.language,
    },
    policy: {
      credentials: "omit",
      persistence: "none",
      proxy: false,
      responseBodiesExported: false,
      streamUrlsExported: false,
    },
    testItem: {
      videoId: null,
      presentation: "synthetic / no remote artwork",
    },
    stages: {
      metadata: { status: "NOT_RUN" },
      player: { status: "NOT_RUN" },
      range: { status: "NOT_RUN" },
      media: { status: "NOT_RUN" },
    },
    audiblePlayback: {
      confirmedByTester: false,
      confirmedAt: null,
    },
    events: [],
  };
}

function safeError(error) {
  const fallback = typeof error === "string" ? error : "Unknown browser error";
  const rawMessage = error && typeof error.message === "string" ? error.message : fallback;
  return {
    name: error && typeof error.name === "string" ? error.name.slice(0, 60) : "Error",
    message: rawMessage
      .replace(/https?:\/\/[^\s)]+/gi, "[redacted-url]")
      .replace(/[\r\n]+/g, " ")
      .slice(0, 220),
  };
}

function endpointLabel(urlText) {
  const url = new URL(urlText);
  return `${url.origin}${url.pathname}`;
}

function updateJson() {
  report.updatedAt = new Date().toISOString();
  elements.json.textContent = JSON.stringify(report, null, 2);
}

function clearTimeline() {
  elements.timeline.replaceChildren();
}

function addTimeline(stage, summary) {
  if (elements.timeline.children.length === 1 && elements.timeline.textContent.includes("Waiting")) {
    clearTimeline();
  }
  const item = document.createElement("li");
  const time = document.createElement("time");
  time.dateTime = new Date().toISOString();
  time.textContent = new Date().toLocaleTimeString([], { hour12: false });
  const text = document.createTextNode(` · ${stage} · ${summary}`);
  item.append(time, text);
  elements.timeline.append(item);
}

function recordEvent(stage, status, summary, details = {}) {
  const event = {
    at: new Date().toISOString(),
    stage,
    status,
    summary: String(summary).slice(0, 180),
    details,
  };
  report.events.push(event);
  addTimeline(stage, event.summary);
  updateJson();
}

function setOverall(status, label) {
  report.status = status;
  elements.overall.dataset.state =
    status === "AUDIBLE_PLAYBACK_CONFIRMED" ? "pass" :
      status.endsWith("BLOCKED") || status.endsWith("FAILED") ? "fail" :
        status === "RUNNING" ? "running" : "idle";
  elements.overall.textContent = label;
  updateJson();
}

function setStage(stage, state, text, details = {}) {
  elements.cards[stage].dataset.state = state;
  elements.results[stage].textContent = text;
  report.stages[stage] = {
    status: state.toUpperCase(),
    summary: text,
    ...details,
  };
  updateJson();
}

async function fetchWithTimeout(url, options = {}) {
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  try {
    return await fetch(url, {
      ...options,
      mode: "cors",
      credentials: "omit",
      cache: "no-store",
      referrerPolicy: "no-referrer",
      signal: controller.signal,
    });
  } finally {
    window.clearTimeout(timer);
  }
}

async function testMetadata(videoId) {
  setStage("metadata", "running", "Requesting anonymous oEmbed metadata…");
  const endpoint = new URL("https://www.youtube.com/oembed");
  endpoint.searchParams.set("url", `https://www.youtube.com/watch?v=${videoId}`);
  endpoint.searchParams.set("format", "json");

  try {
    const response = await fetchWithTimeout(endpoint.toString());
    const result = {
      endpoint: endpointLabel(endpoint.toString()),
      httpStatus: response.status,
      responseType: response.type,
      contentType: response.headers.get("content-type") || "unknown",
    };
    if (!response.ok) {
      setStage("metadata", "fail", `HTTP ${response.status}; player response may still prove metadata.`, result);
      recordEvent("metadata", "fail", `oEmbed answered HTTP ${response.status}.`, result);
      return false;
    }

    const payload = await response.json();
    result.titlePresent = typeof payload.title === "string" && payload.title.length > 0;
    result.authorPresent = typeof payload.author_name === "string" && payload.author_name.length > 0;
    result.provider = payload.provider_name === "YouTube" ? "YouTube" : "other";
    const minimumMetadataPresent = result.titlePresent && result.authorPresent;
    setStage(
      "metadata",
      minimumMetadataPresent ? "pass" : "fail",
      minimumMetadataPresent
        ? "Anonymous title/author metadata returned; content fields were discarded."
        : "Readable response lacked the minimum title/author fields.",
      result,
    );
    recordEvent(
      "metadata",
      minimumMetadataPresent ? "pass" : "fail",
      minimumMetadataPresent
        ? "Anonymous minimum metadata request succeeded."
        : "Metadata response was readable but incomplete.",
      result,
    );
    return minimumMetadataPresent;
  } catch (error) {
    const details = {
      endpoint: endpointLabel(endpoint.toString()),
      error: safeError(error),
    };
    setStage("metadata", "fail", "Browser blocked the metadata request; testing player response next.", details);
    recordEvent("metadata", "fail", "Metadata request was blocked before a readable response.", details);
    return false;
  }
}

function playerRequestBody(videoId) {
  return {
    context: {
      client: {
        clientName: PLAYER_PROFILE.clientName,
        clientVersion: PLAYER_PROFILE.clientVersion,
        hl: "en",
        gl: "US",
      },
      user: {},
    },
    videoId,
    contentCheckOk: true,
    racyCheckOk: true,
  };
}

function isAllowedMediaUrl(urlText) {
  try {
    const url = new URL(urlText);
    return url.protocol === "https:" &&
      (url.port === "" || url.port === "443") &&
      url.hostname.endsWith(".googlevideo.com");
  } catch (_error) {
    return false;
  }
}

function collectDirectFormats(payload) {
  const streaming = payload && payload.streamingData ? payload.streamingData : {};
  const allFormats = [
    ...(Array.isArray(streaming.adaptiveFormats) ? streaming.adaptiveFormats : []),
    ...(Array.isArray(streaming.formats) ? streaming.formats : []),
  ];

  const direct = allFormats.filter((format) =>
    format &&
    typeof format.url === "string" &&
    typeof format.mimeType === "string" &&
    isAllowedMediaUrl(format.url)
  );

  const audioOnly = direct.filter((format) => format.mimeType.startsWith("audio/"));
  const candidates = audioOnly.length > 0 ? audioOnly : direct;
  const ranked = candidates.map((format) => {
    const mime = format.mimeType.split(";")[0].trim();
    const support = elements.audio.canPlayType(format.mimeType) || elements.audio.canPlayType(mime) || "";
    const supportScore = support === "probably" ? 2 : support === "maybe" ? 1 : 0;
    return {
      format,
      support,
      supportScore,
      bitrate: Number(format.bitrate) || 0,
    };
  }).sort((left, right) =>
    right.supportScore - left.supportScore || right.bitrate - left.bitrate
  );

  return {
    totalFormatCount: allFormats.length,
    directFormatCount: direct.length,
    directAudioCount: audioOnly.length,
    hlsManifestPresent: typeof streaming.hlsManifestUrl === "string",
    selected: ranked[0] || null,
  };
}

async function testPlayer(videoId, metadataAlreadyPassed) {
  setStage("player", "running", "Sending one credential-free WEB_REMIX request…");
  const requestDetails = {
    endpoint: endpointLabel(PLAYER_PROFILE.endpoint),
    profile: PLAYER_PROFILE.label,
    clientHeaderId: PLAYER_PROFILE.clientHeaderId,
    clientVersion: PLAYER_PROFILE.clientVersion,
    credentials: "omit",
  };

  let response;
  try {
    response = await fetchWithTimeout(PLAYER_PROFILE.endpoint, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-YouTube-Client-Name": PLAYER_PROFILE.clientHeaderId,
        "X-YouTube-Client-Version": PLAYER_PROFILE.clientVersion,
      },
      body: JSON.stringify(playerRequestBody(videoId)),
    });
  } catch (error) {
    const details = { ...requestDetails, error: safeError(error) };
    setStage("player", "fail", "Browser/CORS blocked the player request before a readable response.", details);
    recordEvent("player", "fail", "No readable player response reached browser code.", details);
    return null;
  }

  requestDetails.httpStatus = response.status;
  requestDetails.responseType = response.type;
  requestDetails.contentType = response.headers.get("content-type") || "unknown";
  if (!response.ok) {
    setStage("player", "fail", `Player endpoint answered HTTP ${response.status}.`, requestDetails);
    recordEvent("player", "fail", `Player endpoint answered HTTP ${response.status}.`, requestDetails);
    return null;
  }

  let payload;
  try {
    payload = await response.json();
  } catch (error) {
    const details = { ...requestDetails, error: safeError(error) };
    setStage("player", "fail", "Player response was not readable JSON.", details);
    recordEvent("player", "fail", "Readable HTTP response did not contain JSON.", details);
    return null;
  }

  const playability = payload.playabilityStatus || {};
  const videoDetails = payload.videoDetails || {};
  requestDetails.playabilityStatus = typeof playability.status === "string" ? playability.status : "MISSING";
  requestDetails.reasonPresent = typeof playability.reason === "string" && playability.reason.length > 0;
  requestDetails.videoDetailsPresent = videoDetails.videoId === videoId;
  requestDetails.videoTitlePresent = typeof videoDetails.title === "string" && videoDetails.title.length > 0;
  requestDetails.videoAuthorPresent = typeof videoDetails.author === "string" && videoDetails.author.length > 0;

  const playerMetadataPresent = requestDetails.videoDetailsPresent &&
    requestDetails.videoTitlePresent && requestDetails.videoAuthorPresent;
  if (!metadataAlreadyPassed && playerMetadataPresent) {
    const metadataDetails = {
      source: "player.videoDetails",
      videoDetailsPresent: true,
      titlePresent: true,
      authorPresent: true,
    };
    setStage(
      "metadata",
      "pass",
      "Player response contained minimum title/author metadata; content fields were discarded.",
      metadataDetails,
    );
    recordEvent("metadata", "pass", "Minimum metadata was available in player response.", metadataDetails);
  }

  if (requestDetails.playabilityStatus !== "OK") {
    setStage("player", "fail", `Readable response, but playability was ${requestDetails.playabilityStatus}.`, requestDetails);
    recordEvent("player", "fail", `Playability status was ${requestDetails.playabilityStatus}.`, requestDetails);
    return null;
  }

  const formats = collectDirectFormats(payload);
  requestDetails.totalFormatCount = formats.totalFormatCount;
  requestDetails.directFormatCount = formats.directFormatCount;
  requestDetails.directAudioCount = formats.directAudioCount;
  requestDetails.hlsManifestPresent = formats.hlsManifestPresent;

  if (!formats.selected) {
    setStage("player", "fail", "Playable response contained no allow-listed direct media URL.", requestDetails);
    recordEvent("player", "fail", "No direct googlevideo media candidate was available.", requestDetails);
    return null;
  }

  const selected = formats.selected;
  const selectedUrl = new URL(selected.format.url);
  requestDetails.selected = {
    mimeType: selected.format.mimeType.slice(0, 120),
    bitrate: selected.bitrate,
    browserSupport: selected.support || "not advertised",
    host: selectedUrl.hostname,
    queryExported: false,
  };
  setStage("player", "pass", "Direct allow-listed media candidate resolved; URL kept in memory only.", requestDetails);
  recordEvent("player", "pass", "Direct media candidate resolved without exporting its URL.", requestDetails);
  return selected.format.url;
}

async function testByteRange(streamUrl) {
  setStage("range", "running", "Requesting the first 32 KiB with credentials omitted…");
  const url = new URL(streamUrl);
  const baseDetails = {
    host: url.hostname,
    requestedRange: "bytes=0-32767",
    credentials: "omit",
    urlExported: false,
  };

  try {
    const response = await fetchWithTimeout(streamUrl, {
      method: "GET",
      headers: { Range: "bytes=0-32767" },
    });
    const contentLength = Number(response.headers.get("content-length")) || null;
    const finalUrlAllowed = isAllowedMediaUrl(response.url);
    const details = {
      ...baseDetails,
      httpStatus: response.status,
      responseType: response.type,
      responseRedirected: response.redirected,
      finalHost: finalUrlAllowed ? new URL(response.url).hostname : "not allow-listed",
      contentType: response.headers.get("content-type") || "unknown",
      contentRangePresent: Boolean(response.headers.get("content-range")),
      contentLength,
    };

    if (!finalUrlAllowed) {
      if (response.body) await response.body.cancel();
      setStage("range", "fail", "Media redirect did not end on an allow-listed host.", details);
      recordEvent("range", "fail", "Final media response host was not allow-listed.", details);
      return false;
    }

    if (!response.ok) {
      if (response.body) await response.body.cancel();
      setStage("range", "fail", `Media host answered HTTP ${response.status}.`, details);
      recordEvent("range", "fail", `Byte-range request answered HTTP ${response.status}.`, details);
      return false;
    }

    if (response.status !== 206 && contentLength && contentLength > MAX_SAFE_BYTE_PROBE) {
      if (response.body) await response.body.cancel();
      setStage("range", "fail", "Server ignored Range; large response was cancelled before download.", details);
      recordEvent("range", "fail", "Range was ignored and the body was safely cancelled.", details);
      return false;
    }

    let firstChunkBytes = 0;
    if (response.body) {
      const reader = response.body.getReader();
      const first = await reader.read();
      firstChunkBytes = first.value ? first.value.byteLength : 0;
      await reader.cancel();
    }
    details.firstChunkBytes = firstChunkBytes;
    const rangeWorked = response.status === 206 && firstChunkBytes > 0;
    setStage(
      "range",
      rangeWorked ? "pass" : "fail",
      rangeWorked ? "Credential-free CORS byte-range read succeeded." : "Response was readable, but byte-range behavior was not proven.",
      details,
    );
    recordEvent("range", rangeWorked ? "pass" : "fail", rangeWorked ? "First media bytes were readable." : "Readable response did not prove byte ranges.", details);
    return rangeWorked;
  } catch (error) {
    const details = { ...baseDetails, error: safeError(error) };
    setStage("range", "fail", "CORS or network policy blocked the byte-range fetch.", details);
    recordEvent("range", "fail", "Browser code could not read media bytes.", details);
    return false;
  }
}

function prepareMedia(streamUrl) {
  activeStreamUrl = streamUrl;
  elements.audio.pause();
  elements.audio.removeAttribute("src");
  elements.audio.load();
  elements.audio.src = streamUrl;
  elements.play.disabled = false;
  elements.heard.disabled = true;
  elements.hint.textContent = "The URL is held only in memory. Click Play resolved stream; then confirm only if audio is audible.";
  setStage("media", "running", "Direct stream prepared; waiting for a separate user playback click.", {
    crossOrigin: "anonymous",
    credentials: "omit",
    preload: "none",
    urlExported: false,
  });
  recordEvent("media", "ready", "Media element prepared without preloading.");
}

async function runProbe() {
  const videoId = elements.videoId.value.trim();
  if (!VIDEO_ID_PATTERN.test(videoId)) {
    elements.videoId.setCustomValidity("Enter exactly 11 letters, numbers, underscores, or hyphens.");
    elements.videoId.reportValidity();
    return;
  }
  elements.videoId.setCustomValidity("");

  runSequence += 1;
  const thisRun = runSequence;
  resetRuntime(false);
  report = createReport();
  report.startedAt = new Date().toISOString();
  report.testItem.videoId = videoId;
  clearTimeline();
  elements.run.disabled = true;
  setOverall("RUNNING", "Running");
  recordEvent("probe", "start", "B1 probe started by user action.");

  const metadataPassed = await testMetadata(videoId);
  if (thisRun !== runSequence) return;

  const streamUrl = await testPlayer(videoId, metadataPassed);
  if (thisRun !== runSequence) return;

  if (!streamUrl) {
    setStage("range", "fail", "Not attempted because no direct media URL reached browser code.", { attempted: false });
    setStage("media", "fail", "Not attempted because no direct media URL reached browser code.", { attempted: false });
    setOverall("PLAYER_PATH_BLOCKED", "Player path blocked");
    elements.run.disabled = false;
    recordEvent("probe", "stop", "Probe stopped at the player-response boundary.");
    return;
  }

  await testByteRange(streamUrl);
  if (thisRun !== runSequence) return;

  prepareMedia(streamUrl);
  setOverall("MEDIA_READY_FOR_USER_GESTURE", "Ready for play click");
  elements.run.disabled = false;
  recordEvent("probe", "ready", "Direct media is ready for an explicit playback gesture.");
}

async function playResolved() {
  if (!activeStreamUrl) return;
  elements.play.disabled = true;
  try {
    await elements.audio.play();
    recordEvent("media", "play-promise", "HTMLMediaElement.play() resolved.");
  } catch (error) {
    const details = { error: safeError(error) };
    setStage("media", "fail", "Browser rejected playback; see sanitized error.", details);
    setOverall("MEDIA_PLAYBACK_FAILED", "Media failed");
    recordEvent("media", "fail", "HTMLMediaElement.play() rejected.", details);
    elements.play.disabled = false;
  }
}

function confirmAudiblePlayback() {
  if (!activeStreamUrl || elements.audio.paused) return;
  report.audiblePlayback.confirmedByTester = true;
  report.audiblePlayback.confirmedAt = new Date().toISOString();
  setStage("media", "pass", "Tester confirmed audible playback after the playing event.", {
    event: "playing",
    testerConfirmed: true,
    currentTimeSeconds: Math.round(elements.audio.currentTime * 10) / 10,
    durationKnown: Number.isFinite(elements.audio.duration),
  });
  setOverall("AUDIBLE_PLAYBACK_CONFIRMED", "Audible playback confirmed");
  elements.heard.disabled = true;
  recordEvent("media", "pass", "Tester confirmed that audio was audible.");
}

function resetRuntime(incrementSequence = true) {
  if (incrementSequence) runSequence += 1;
  activeStreamUrl = null;
  elements.audio.pause();
  elements.audio.removeAttribute("src");
  elements.audio.load();
  elements.play.disabled = true;
  elements.heard.disabled = true;
  elements.run.disabled = false;
  elements.hint.textContent = "Run the probe first. A separate click is required before playback.";
}

function resetAll() {
  resetRuntime(true);
  report = createReport();
  for (const stage of Object.keys(elements.cards)) {
    setStage(stage, "idle", "Not tested");
  }
  setOverall("NOT_RUN", "Not run");
  elements.timeline.replaceChildren();
  const item = document.createElement("li");
  item.textContent = "Waiting for a manual run.";
  elements.timeline.append(item);
  elements.json.textContent = "Run the probe to produce a report.";
}

async function copyResult() {
  const text = JSON.stringify(report, null, 2);
  try {
    await navigator.clipboard.writeText(text);
  } catch (_error) {
    const field = document.createElement("textarea");
    field.value = text;
    field.setAttribute("readonly", "");
    field.style.position = "fixed";
    field.style.opacity = "0";
    document.body.append(field);
    field.select();
    document.execCommand("copy");
    field.remove();
  }
  const previous = elements.copy.textContent;
  elements.copy.textContent = "Copied";
  window.setTimeout(() => { elements.copy.textContent = previous; }, 1400);
}

const MEDIA_EVENT_NAMES = ["loadstart", "loadedmetadata", "canplay", "playing", "waiting", "stalled", "ended", "error"];
for (const eventName of MEDIA_EVENT_NAMES) {
  elements.audio.addEventListener(eventName, () => {
    if (!activeStreamUrl) return;
    if (eventName === "playing") {
      elements.heard.disabled = false;
      elements.play.disabled = true;
      setStage("media", "running", "Browser emitted playing; confirm only if you can hear audio.", {
        event: "playing",
        currentTimeSeconds: Math.round(elements.audio.currentTime * 10) / 10,
        urlExported: false,
      });
      setOverall("MEDIA_PLAYING_UNCONFIRMED", "Playing; confirm audibility");
    } else if (eventName === "error") {
      const mediaError = elements.audio.error;
      const details = {
        event: "error",
        mediaErrorCode: mediaError ? mediaError.code : null,
        mediaErrorMessage: mediaError && mediaError.message ? safeError(mediaError).message : "unavailable",
        urlExported: false,
      };
      setStage("media", "fail", "Media element emitted an error.", details);
      setOverall("MEDIA_PLAYBACK_FAILED", "Media failed");
      elements.play.disabled = false;
    }
    recordEvent("media", eventName === "error" ? "fail" : "event", `Media event: ${eventName}.`, { event: eventName });
  });
}

elements.origin.textContent = window.location.origin;
elements.run.addEventListener("click", runProbe);
elements.play.addEventListener("click", playResolved);
elements.heard.addEventListener("click", confirmAudiblePlayback);
elements.reset.addEventListener("click", resetAll);
elements.copy.addEventListener("click", copyResult);
elements.videoId.addEventListener("input", () => elements.videoId.setCustomValidity(""));
