/**
 * The keyboard map: one table, two consumers (the dispatcher and the help
 * sheet), and a refusal to steal keys from someone who is typing.
 */
import test from "node:test";
import assert from "node:assert/strict";

import { SHORTCUTS, isTextEntryTarget, matchShortcut, shortcutGroups } from "../src/js/shortcuts.js";

const key = (value, extra = {}) => ({ key: value, target: { tagName: "BODY" }, ...extra });

test("every shortcut has one owner and a readable label", () => {
  const ids = SHORTCUTS.map((shortcut) => shortcut.id);
  assert.equal(new Set(ids).size, ids.length, "two shortcuts answer the same action");
  const keys = SHORTCUTS.map((shortcut) => shortcut.key);
  assert.equal(new Set(keys).size, keys.length, "two shortcuts claim the same key");
  for (const shortcut of SHORTCUTS) assert.ok(shortcut.label.length > 3, `${shortcut.id} has no label`);
});

test("the playback keys are the ones the help sheet lists", () => {
  const actions = SHORTCUTS.map((shortcut) => shortcut.id);
  for (const id of ["play-pause", "next", "previous", "seek-forward", "seek-back", "volume-up", "volume-down", "mute", "shuffle", "repeat", "search", "escape", "help"]) {
    assert.ok(actions.includes(id), `${id} disappeared from the keyboard map`);
  }
});

test("Space is play/pause and it is worth preventing", () => {
  assert.deepEqual(matchShortcut(key(" ")), { action: "play-pause", preventDefault: true });
});

test("arrows seek, up and down change volume", () => {
  assert.equal(matchShortcut(key("ArrowRight")).action, "seek-forward");
  assert.equal(matchShortcut(key("ArrowLeft")).action, "seek-back");
  assert.equal(matchShortcut(key("ArrowUp")).action, "volume-up");
  assert.equal(matchShortcut(key("ArrowDown")).action, "volume-down");
});

test("letters are case-insensitive and modifiers mean 'not ours'", () => {
  assert.equal(matchShortcut(key("N")).action, "next");
  assert.equal(matchShortcut(key("n", { ctrlKey: true })), null, "Ctrl+N belongs to the browser");
  assert.equal(matchShortcut(key("m", { metaKey: true })), null, "Cmd+M is the OS's");
  assert.equal(matchShortcut(key("p", { altKey: true })), null);
});

test("typing wins: no shortcut fires from a text field", () => {
  assert.equal(isTextEntryTarget({ tagName: "INPUT", type: "text" }), true);
  assert.equal(isTextEntryTarget({ tagName: "INPUT", type: "search" }), true);
  assert.equal(isTextEntryTarget({ tagName: "TEXTAREA" }), true);
  assert.equal(isTextEntryTarget({ tagName: "DIV", isContentEditable: true }), true);
  assert.equal(isTextEntryTarget({ tagName: "INPUT", type: "range" }), false, "a slider is not a text field");
  assert.equal(isTextEntryTarget({ tagName: "BUTTON" }), false);
  const typing = key(" ", { target: { tagName: "INPUT", type: "search" } });
  assert.equal(matchShortcut(typing), null);
});

test("Escape is always available, including inside a field and a dialog", () => {
  const inField = key("Escape", { target: { tagName: "INPUT", type: "search" } });
  assert.deepEqual(matchShortcut(inField), { action: "escape", preventDefault: false });
  assert.deepEqual(matchShortcut(key("Escape"), { inDialog: true }), { action: "escape", preventDefault: false });
});

test("inside a dialog nothing but Escape is ours", () => {
  assert.equal(matchShortcut(key(" "), { inDialog: true }), null);
  assert.equal(matchShortcut(key("n"), { inDialog: true }), null);
});

test("the help sheet's groups come from the same table", () => {
  const groups = shortcutGroups();
  assert.deepEqual(groups.map((group) => group.group), ["Playback", "Navigation"]);
  assert.equal(groups.flatMap((group) => group.shortcuts).length, SHORTCUTS.length);
});

test("composition keystrokes belong to the IME, including Escape", () => {
  for (const key of ["Escape", " ", "/", "ArrowRight", "m"]) {
    assert.equal(matchShortcut({ key, isComposing: true, target: { tagName: "DIV" } }), null, key);
  }
});

test("legacy processing-key events do not trigger app shortcuts", () => {
  for (const key of ["Escape", " ", "/"]) {
    assert.equal(matchShortcut({ key, keyCode: 229, target: { tagName: "DIV" } }), null, key);
  }
  assert.equal(matchShortcut({ key: "Escape", target: { tagName: "INPUT" } }).action, "escape");
});
