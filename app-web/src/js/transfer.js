import { FORMAT_VERSION, parseImportJSON, validateImport } from "./library.js";
import { checkBackupSize } from "./backup-policy.js";

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
  checkBackupSize(file.size);
  const text = await file.text();
  const parsed = parseImportJSON(text);
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

/** Warn if a downloaded file cannot be restored under this build's policy.
 * Do not discard the file: it may be the visitor's only copy. */
export function backupExportWarning(text, sourceLibrary) {
  try {
    validateImport(parseImportJSON(text));
    if (sourceLibrary) validateImport({ ...sourceLibrary, app: "DHUN web", version: FORMAT_VERSION });
    return null;
  } catch (error) {
    return `This backup cannot be restored by this version: ${error?.message ?? "validation failed"}. Keep the file and existing library; seek recovery help.`;
  }
}
