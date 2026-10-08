/**
 * Remove `dist/` before a build.
 *
 * Eleventy does not prune its output directory, so a file it no longer
 * generates (the standalone `assets/styles.css`, before the stylesheet moved
 * into the pages) would survive a rebuild and be compared by the drift check —
 * which reads `git diff`, and therefore never notices a *stale* file that is
 * also committed. Building from empty makes the committed mirror an exact
 * function of `src/` + `css/`.
 */
import { rmSync } from "node:fs";

rmSync(new URL("../dist/", import.meta.url).pathname, { recursive: true, force: true });
