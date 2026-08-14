import { createDecipheriv, timingSafeEqual } from "node:crypto";
import {
  createReadStream,
  existsSync,
  readFileSync,
  realpathSync,
  statSync,
} from "node:fs";
import http from "node:http";
import net from "node:net";
import { dirname, extname, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";
import { WebSocketServer } from "ws";

const moduleDir = dirname(fileURLToPath(import.meta.url));
const clientTemplate = readFileSync(
  process.env.REMOTE_CLIENT_TEMPLATE || resolve(moduleDir, "remote-client.html"),
  "utf8",
);
const assetsRoot = realpathSync(
  process.env.REMOTE_ASSETS_ROOT || resolve(moduleDir, "node_modules", "@novnc", "novnc"),
);
const fixedDesKey = Buffer.from("e84ad660c4721ae0", "hex");

const config = Object.freeze({
  id: required("REMOTE_ID"),
  name: required("REMOTE_NAME"),
  description: process.env.REMOTE_DESCRIPTION || "Tailscale 直连桌面",
  token: required("REMOTE_TOKEN"),
  host: required("REMOTE_BIND_HOST"),
  port: positiveInteger(process.env.REMOTE_PORT || "8790", "REMOTE_PORT"),
  vncHost: process.env.VNC_HOST || "127.0.0.1",
  vncPort: positiveInteger(process.env.VNC_PORT || "5900", "VNC_PORT"),
  passwordFile: process.env.VNC_PASSWORD_FILE || "",
});

function required(name) {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`${name} is required`);
  return value;
}

function positiveInteger(value, name) {
  const parsed = Number.parseInt(value, 10);
  if (!Number.isInteger(parsed) || parsed < 1 || parsed > 65535) {
    throw new Error(`${name} must be a valid TCP port`);
  }
  return parsed;
}

function json(res, status, body) {
  const data = JSON.stringify(body);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(data),
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
  });
  res.end(data);
}

function candidateTokenMatches(candidate) {
  const expected = Buffer.from(config.token);
  const actual = Buffer.from(candidate || "");
  return actual.length === expected.length && timingSafeEqual(actual, expected);
}

function requestTokenMatches(req, url) {
  const authorization = req.headers.authorization || "";
  return candidateTokenMatches(
    authorization.startsWith("Bearer ")
      ? authorization.slice(7)
      : url.searchParams.get("token") || "",
  );
}

function htmlJson(value) {
  return JSON.stringify(value).replace(/</g, "\\u003c");
}

function decryptVncPassword(encrypted) {
  if (!Buffer.isBuffer(encrypted) || encrypted.length !== 8) {
    throw new Error("Invalid VNC password file");
  }
  const tripleKey = Buffer.concat([fixedDesKey, fixedDesKey, fixedDesKey]);
  const decipher = createDecipheriv("des-ede3", tripleKey, null);
  decipher.setAutoPadding(false);
  return Buffer.concat([decipher.update(encrypted), decipher.final()])
    .toString("latin1")
    .replace(/\0+$/g, "");
}

function loadPassword() {
  if (!config.passwordFile) return "";
  const encrypted = readFileSync(config.passwordFile);
  return decryptVncPassword(encrypted);
}

function serveClient(req, res, url) {
  if (!requestTokenMatches(req, url)) {
    json(res, 401, { ok: false, error: "Unauthorized" });
    return;
  }
  if (url.searchParams.get("device") !== config.id) {
    json(res, 404, { ok: false, error: "找不到这台远程电脑" });
    return;
  }
  try {
    const html = clientTemplate
      .replace("__DEVICE_JSON__", htmlJson({
        id: config.id,
        name: config.name,
        description: config.description,
      }))
      .replace("__TOKEN_JSON__", htmlJson(config.token))
      .replace("__PASSWORD_JSON__", htmlJson(loadPassword()));
    res.writeHead(200, {
      "content-type": "text/html; charset=utf-8",
      "content-length": Buffer.byteLength(html),
      "cache-control": "no-store",
      "content-security-policy": "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self' ws: wss:; img-src 'self' data: blob:; font-src 'self' data:",
      "referrer-policy": "no-referrer",
      "x-content-type-options": "nosniff",
    });
    res.end(html);
  } catch (error) {
    json(res, 503, { ok: false, error: `无法读取远程桌面配置：${error.message}` });
  }
}

function serveAsset(req, res, url) {
  let relative;
  try {
    relative = decodeURIComponent(url.pathname.slice("/remote-assets/".length));
  } catch {
    json(res, 400, { ok: false, error: "Invalid asset path" });
    return;
  }
  if (!relative || relative.includes("..") || !/^[A-Za-z0-9_./-]+$/.test(relative)) {
    json(res, 404, { ok: false, error: "Asset not found" });
    return;
  }
  let path;
  let stats;
  try {
    path = realpathSync(resolve(assetsRoot, relative));
    stats = statSync(path);
  } catch {
    json(res, 404, { ok: false, error: "Asset not found" });
    return;
  }
  if (!path.startsWith(`${assetsRoot}${sep}`) || !stats.isFile()) {
    json(res, 404, { ok: false, error: "Asset not found" });
    return;
  }
  const contentType = new Map([
    [".js", "text/javascript; charset=utf-8"],
    [".json", "application/json; charset=utf-8"],
    [".wasm", "application/wasm"],
  ]).get(extname(path).toLowerCase()) || "application/octet-stream";
  res.writeHead(200, {
    "content-type": contentType,
    "content-length": stats.size,
    "cache-control": "public, max-age=86400, immutable",
    "x-content-type-options": "nosniff",
  });
  if (req.method === "HEAD") {
    res.end();
    return;
  }
  createReadStream(path).pipe(res);
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, "http://remote.local");
  if (req.method === "GET" && url.pathname === "/health") {
    json(res, 200, {
      ok: true,
      service: "codex-pocket-direct-remote",
      device: config.id,
      route: "tailscale-direct",
    });
    return;
  }
  if (req.method === "GET" && url.pathname === "/remote/client") {
    serveClient(req, res, url);
    return;
  }
  if ((req.method === "GET" || req.method === "HEAD") && url.pathname.startsWith("/remote-assets/")) {
    serveAsset(req, res, url);
    return;
  }
  if (req.method === "POST" && url.pathname === "/remote/diagnostics") {
    if (!requestTokenMatches(req, url)) {
      json(res, 401, { ok: false, error: "Unauthorized" });
      return;
    }
    req.resume();
    res.writeHead(204, { "cache-control": "no-store" });
    res.end();
    return;
  }
  json(res, 404, { ok: false, error: "Not found" });
});

const remoteWss = new WebSocketServer({ noServer: true, maxPayload: 2 * 1024 * 1024 });

server.on("upgrade", (req, socket, head) => {
  const url = new URL(req.url, "http://remote.local");
  if (
    url.pathname === "/remote/ws" &&
    url.searchParams.get("device") === config.id &&
    requestTokenMatches(req, url)
  ) {
    remoteWss.handleUpgrade(req, socket, head, ws => remoteWss.emit("connection", ws));
    return;
  }
  socket.write("HTTP/1.1 401 Unauthorized\r\nConnection: close\r\n\r\n");
  socket.destroy();
});

remoteWss.on("connection", ws => {
  const vnc = net.createConnection({ host: config.vncHost, port: config.vncPort });
  let closed = false;
  const close = (code, reason) => {
    if (closed) return;
    closed = true;
    vnc.destroy();
    if (ws.readyState === ws.OPEN || ws.readyState === ws.CONNECTING) {
      ws.close(code, reason.slice(0, 120));
    }
  };
  ws.on("message", data => {
    if (!vnc.destroyed) vnc.write(data);
  });
  ws.on("close", () => close(1000, "Client closed"));
  ws.on("error", () => close(1011, "WebSocket error"));
  vnc.on("data", data => {
    if (ws.readyState === ws.OPEN) ws.send(data, { binary: true });
  });
  vnc.on("end", () => close(1000, "VNC closed"));
  vnc.on("error", error => close(1011, `VNC connection failed: ${error.message}`));
});

server.listen(config.port, config.host, () => {
  console.log(`Codex Pocket direct remote for ${config.id} listening on ${config.host}:${config.port}`);
});
