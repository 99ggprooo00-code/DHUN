/** Resource budgets for an untrusted UTF-8 JSON backup, also applied to exports.
 * The schema permits up to 200,000 playlist references, plus snapshots and
 * their metadata. The token budget leaves room for normal schema-shaped files
 * without allowing a small file made of millions of JSON values to exhaust
 * memory during JSON.parse. Deeply nested extension settings are bounded too.
 */
export const BACKUP_MAX_BYTES = 24 * 1024 * 1024;
export const BACKUP_MAX_TOKENS = 1_500_000;
export const BACKUP_MAX_DEPTH = 128;

export function checkBackupSize(bytes) {
  if (bytes > BACKUP_MAX_BYTES) {
    throw new Error("the backup exceeds the 24 MiB UTF-8 limit; keep the original file and reduce the library or use a compatible recovery tool");
  }
}

/** A non-allocating lexical budget check, before JSON.parse. JSON.parse remains
 * the syntax authority; this scanner just skips strings and counts tokens. */
export function checkBackupComplexity(text) {
  let tokens = 0, depth = 0;
  for (let i = 0; i < text.length; i += 1) {
    const c = text[i];
    if (c === '"') {
      tokens += 1;
      while (++i < text.length) {
        if (text[i] === "\\") { i += 1; continue; }
        if (text[i] === '"') break;
      }
    } else if (c === "{" || c === "[") {
      tokens += 1;
      depth += 1;
      if (depth > BACKUP_MAX_DEPTH) throw new Error(`the backup exceeds the maximum nesting depth of ${BACKUP_MAX_DEPTH}; keep the original file`);
    } else if (c === "}" || c === "]") {
      depth -= 1;
    } else if (c === "," || c === ":" || c === " " || c === "\n" || c === "\r" || c === "\t") {
      continue;
    } else {
      tokens += 1;
      // Skip one primitive token; syntax and numeric integrity are checked later.
      while (i + 1 < text.length && !/[\s,\]}:{[\"]/.test(text[i + 1])) i += 1;
    }
    if (tokens > BACKUP_MAX_TOKENS) throw new Error(`the backup exceeds the limit of ${BACKUP_MAX_TOKENS.toLocaleString("en-US")} JSON tokens; keep the original file`);
  }
}

/** UTF-8 bytes, not UTF-16 code units. Blob matches the bytes of the download. */
export function checkBackupText(text) {
  checkBackupSize(new Blob([text]).size);
  checkBackupComplexity(text);
}
