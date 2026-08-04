/**
 * Parses one RFC 7233 byte range. Returns null when no Range was requested
 * and false when the range cannot be satisfied.
 */
export function parseByteRange(header, size) {
  if (!header) return null;
  if (!Number.isSafeInteger(size) || size <= 0) return false;
  const match = String(header).match(/^bytes=(\d*)-(\d*)$/);
  if (!match || (!match[1] && !match[2])) return false;

  let start;
  let end;
  if (!match[1]) {
    const suffixLength = Number(match[2]);
    if (!Number.isSafeInteger(suffixLength) || suffixLength <= 0) return false;
    start = Math.max(0, size - suffixLength);
    end = size - 1;
  } else {
    start = Number(match[1]);
    end = match[2] ? Number(match[2]) : size - 1;
  }
  if (
    !Number.isSafeInteger(start) || !Number.isSafeInteger(end) ||
    start < 0 || start >= size || end < start
  ) return false;
  return { start, end: Math.min(end, size - 1) };
}
