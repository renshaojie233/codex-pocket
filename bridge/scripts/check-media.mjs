import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const mediaPath = process.argv[2];
if (!mediaPath) throw new Error("Usage: node scripts/check-media.mjs /absolute/path/to/media");

const config = JSON.parse(readFileSync(resolve("data/config.json"), "utf8"));
const url = new URL(`http://${config.host}:${config.port}/media`);
url.searchParams.set("path", mediaPath);

const unauthorized = await fetch(url);
if (unauthorized.status !== 401) {
  throw new Error(`Expected unauthenticated media request to return 401, got ${unauthorized.status}`);
}

url.searchParams.set("token", config.token);
const metadata = await fetch(url, { method: "HEAD" });
if (metadata.status !== 200) throw new Error(`Expected media HEAD request to return 200, got ${metadata.status}`);
const ranged = await fetch(url, { headers: { Range: "bytes=0-31" } });
if (ranged.status !== 206) throw new Error(`Expected media range request to return 206, got ${ranged.status}`);
const bytes = new Uint8Array(await ranged.arrayBuffer());
if (bytes.length !== 32) throw new Error(`Expected 32 media bytes, got ${bytes.length}`);

console.log(JSON.stringify({
  unauthorized: unauthorized.status,
  metadata: metadata.status,
  authorizedRange: ranged.status,
  contentType: ranged.headers.get("content-type"),
  bytes: bytes.length,
}, null, 2));
