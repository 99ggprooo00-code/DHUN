/**
 * Keyboard control: what is bound, and what must never be stolen.
 *
 * The failure this file exists to prevent is the classic one — a player that
 * pauses when you type a space into its own search box, or that eats `Cmd+W`.
 * Both are tested here directly.
 */
import test from "node:test";
import assert from "node:assert/strict";

import {
  SHORTCUTS,
  shortcutGroups,
  shortcutKeys,
  matchShortcut,
  isTextEntryTarget,
  modifierLabel,
  CHORD_TIMEOUT_MS,
} from "../src/js/shortcuts.js";

const key = (k, extra = {}) => ({ key: k, target: { tagName: "BODY" }, ...extra });

test("typing in a text field is never a shortcut", () => {
  for (const target of [
    { tagName: "INPUT", type: "search" },
    { tagName: "INPUT", type: "text" },
    { tagName: "TEXTAREA" },
    { tagName: "DIV", isContentEditable: true },
  ]) {
    const result = matchShortcut({ key: " ", target }, { capabilities: { hasTrack: true } });
    assert.equal(result.action, null);
    assert.equal(result.swallowed, false, "a space in a search box must stay a space");
  }
  assert.equal(isTextEntryTarget({ tagName: "INPUT", type: "search" }), true);
  assert.equal(isTextEntryTarget({ tagName: "INPUT", type: "range" }), false, "a range slider keeps its keys");
  assert.equal(isTextEntryTarget({ tagName: "BUTTON" }), false);
});

test("the browser's own combinations are left alone", () => {
  for (const event of [
    key("w", { metaKey: true }),
    key("t", { ctrlKey: true }),
    key("l", { metaKey: true }),
    key("c", { ctrlKey: true }),
    key("n", { ctrlKey: true, metaKey: true }),
  ]) {
    const result = matchShortcut(event, { capabilities: { hasTrack: true } });
    assert.equal(result.action, null, `${event.key} was intercepted`);
    assert.equal(result.swallowed, false);
  }
});

test("space plays and pauses, but only when there is a track", () => {
  assert.equal(matchShortcut(key(" "), { capabilities: { hasTrack: true } }).action.id, "play-pause");
  assert.equal(matchShortcut(key(" "), { capabilities: { hasTrack: false } }).action, null);
  assert.equal(matchShortcut(key(" "), { capabilities: {} }).swallowed, false);
});

test("the documented keys are bound, and each says what it does", () => {
  const expected = {
    " ": "play-pause",
    m: "mute",
    s: "shuffle",
    r: "repeat",
    l: "like",
    "/": "search",
    Escape: "close",
  };
  for (const [pressed, id] of Object.entries(expected)) {
    const result = matchShortcut(key(pressed), { capabilities: { hasTrack: true } });
    assert.equal(result.action?.id, id, `${pressed} → ${id}`);
  }
  // `?` is the one binding whose modifier depends on the visitor's keyboard: it
  // needs shift on a US layout and none on an AZERTY one, so both arrive here.
  for (const shiftKey of [false, true]) {
    const help = matchShortcut(key("?", { shiftKey }), { capabilities: { hasTrack: true } });
    assert.equal(help.action?.id, "help", `? with shiftKey=${shiftKey}`);
    assert.equal(help.swallowed, true);
  }
});

test("a capital letter is not a shortcut", () => {
  for (const pressed of ["S", "R", "L", "M"]) {
    const result = matchShortcut(key(pressed, { shiftKey: true }), { capabilities: { hasTrack: true } });
    assert.equal(result.action, null, `${pressed} fired a shortcut`);
    assert.equal(result.swallowed, false, `${pressed} was swallowed`);
  }
});

test("arrows seek by default, and skip with either modifier people use", () => {
  const next = { capabilities: { hasTrack: true } };
  assert.equal(matchShortcut(key("ArrowRight"), next).action.id, "seek-forward");
  assert.equal(matchShortcut(key("ArrowRight", { altKey: true }), next).action.id, "next");
  // Desktop players taught Cmd/Ctrl+arrow for transport; a web app that ignores
  // it feels broken to anyone who has used one.
  assert.equal(matchShortcut(key("ArrowRight", { ctrlKey: true }), next).action.id, "next");
  assert.equal(matchShortcut(key("ArrowLeft", { metaKey: true }), next).action.id, "previous");
  assert.equal(matchShortcut(key("ArrowUp"), next).action.id, "volume-up");
});

test("chords work in two steps and expire", () => {
  const first = matchShortcut(key("g"), { capabilities: {} });
  assert.equal(first.pending, "g");
  assert.equal(first.action, null);
  assert.equal(first.swallowed, true, "a half-typed chord is held back, not typed into the page");

  assert.equal(matchShortcut(key("h"), { capabilities: {}, buffer: "g" }).action.id, "home");
  assert.equal(matchShortcut(key("l"), { capabilities: {}, buffer: "g" }).action.id, "library");
  assert.equal(matchShortcut(key("z"), { capabilities: {}, buffer: "g" }).action, null);
  assert.ok(CHORD_TIMEOUT_MS > 500 && CHORD_TIMEOUT_MS < 3000);
});

test("a chord is abandoned when focus lands in a field", () => {
  const result = matchShortcut({ key: "h", target: { tagName: "INPUT", type: "text" } }, { capabilities: {}, buffer: "g" });
  assert.equal(result.action, null);
  assert.equal(result.pending, null);
});

test("every binding is documented exactly once and grouped for the help sheet", () => {
  const ids = SHORTCUTS.map((shortcut) => shortcut.id);
  assert.equal(new Set(ids).size, ids.length, "two shortcuts share an id");
  for (const shortcut of SHORTCUTS) {
    assert.ok(shortcut.label && shortcut.label.length > 3, `${shortcut.id} has no label`);
    assert.ok(shortcut.group);
    assert.ok(shortcut.keys.length > 0);
  }
  const groups = shortcutGroups();
  assert.ok(groups.length >= 2);
  assert.equal(groups.reduce((sum, group) => sum + group.items.length, 0), SHORTCUTS.length);
});

test("key caps are spelled out per platform", () => {
  assert.equal(modifierLabel("MacIntel"), "⌘");
  assert.equal(modifierLabel("Win32"), "Ctrl");
  const play = SHORTCUTS.find((shortcut) => shortcut.id === "play-pause");
  assert.equal(shortcutKeys(play, "Win32"), "Space");
  const home = SHORTCUTS.find((shortcut) => shortcut.id === "home");
  assert.equal(shortcutKeys(home, "Win32"), "G then H");
  const next = SHORTCUTS.find((shortcut) => shortcut.id === "next");
  assert.equal(shortcutKeys(next, "Win32"), "Alt+→ / Ctrl+→");
  assert.equal(shortcutKeys(next, "MacIntel"), "Alt+→ / ⌘+→");
});

test("a broken event cannot throw the dispatcher", () => {
  for (const event of [null, undefined, {}, { key: 42 }, { key: "a", target: null }]) {
    assert.doesNotThrow(() => matchShortcut(event, { capabilities: {} }));
  }
});
