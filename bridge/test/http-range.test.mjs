import test from "node:test";
import assert from "node:assert/strict";
import { parseByteRange } from "../src/http-range.mjs";

test("parses bounded and open-ended download ranges", () => {
  assert.deepEqual(parseByteRange("bytes=0-1023", 5000), { start: 0, end: 1023 });
  assert.deepEqual(parseByteRange("bytes=1024-", 5000), { start: 1024, end: 4999 });
});

test("parses suffix ranges used by browser download managers", () => {
  assert.deepEqual(parseByteRange("bytes=-500", 5000), { start: 4500, end: 4999 });
  assert.deepEqual(parseByteRange("bytes=-9000", 5000), { start: 0, end: 4999 });
});

test("rejects malformed or unsatisfiable ranges", () => {
  assert.equal(parseByteRange("bytes=5000-", 5000), false);
  assert.equal(parseByteRange("bytes=20-10", 5000), false);
  assert.equal(parseByteRange("bytes=0-1,3-4", 5000), false);
});
