import { FORMAT_VERSION } from "./library.js";

/**
 * Importing a library file.
 *
 * The file is the visitor's own export — the app never fetches one. Reading it
 * is deliberately strict: JSON that does not parse, or a shape that is not this
 * app's, is refused with the reason, because a half-imported library is worse
 * than a refused one.
 */
export async function handleFileImport(file, storage) {
  if (!file) throw new Error("no file was chosen");
  if (file.size > 8 * 1024 * 1024) throw new Error("the file is larger than 8 MB");
  const text = await file.text();
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new Error("the file is not JSON");
  }
  if (parsed?.app !== "DHUN web") throw new Error("the file was not exported by this app");
  // Only the format this build writes is read. A file from another format is
  // refused rather than guessed at: the shape could have changed underneath it.
  if (parsed?.version !== FORMAT_VERSION) {
    throw new Error(
      `the file is format version ${parsed?.version ?? "unknown"}; this app reads version ${FORMAT_VERSION}`,
    );
  }
  return storage.importAll(text);
}
