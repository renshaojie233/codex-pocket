import { randomUUID, timingSafeEqual } from "node:crypto";
import {
  createReadStream, createWriteStream, existsSync, mkdirSync, readFileSync, readdirSync,
  realpathSync, renameSync, statSync, unlinkSync, writeFileSync,
} from "node:fs";
import http from "node:http";
import { homedir } from "node:os";
import { dirname, extname, isAbsolute, join, normalize, resolve, sep } from "node:path";
import { pipeline, Transform } from "node:stream";
import { fileURLToPath } from "node:url";
import { WebSocketServer } from "ws";
import { CodexClient } from "./codex-client.mjs";
import { parseByteRange } from "./http-range.mjs";
import { EventJournal } from "./event-journal.mjs";
import { loadConfig } from "./config.mjs";
import { mapModel, mapNotification, mapThreadDetail, mapThreadSummary } from "./mapper.mjs";

const config = loadConfig();
const moduleDir = dirname(fileURLToPath(import.meta.url));
const VERSION = "0.15.9";
const apkPath = process.env.APK_PATH || resolve(moduleDir, "..", "..", "outputs", `codex-pocket-${VERSION}.apk`);
const codex = new CodexClient({ codexBin: config.codexBin });
const loadedThreads = new Map();
const pendingServerRequests = new Map();
const sockets = new Set();
const eventJournal = new EventJournal();
const automationsRoot = resolve(homedir(), ".codex", "automations");
const uploadRoot = resolve(moduleDir, "..", "data", "uploads");
const DEFAULT_PERMISSION_PROFILE = ":danger-full-access";
const DEFAULT_MESSAGE_LIMIT = 120;
const MAX_UPLOAD_BYTES = 15 * 1024 * 1024;
const MAX_UPLOAD_FILES = 400;
const MAX_UPLOAD_STORAGE_BYTES = 1024 * 1024 * 1024;

mkdirSync(uploadRoot, { recursive: true });

function permissionProfileFromSettings(settings) {
  const activeId = settings?.activePermissionProfile?.id;
  if (typeof activeId === "string" && activeId) return activeId;
  switch (settings?.sandboxPolicy?.type || settings?.sandbox?.type) {
    case "dangerFullAccess":
      return ":danger-full-access";
    case "workspaceWrite":
    case "externalSandbox":
      return ":workspace";
    case "readOnly":
      return ":read-only";
    default:
      return null;
  }
}

function resolvePermissionProfile(value) {
  return typeof value === "string" && value.trim() ? value.trim() : DEFAULT_PERMISSION_PROFILE;
}

function approvalPolicyForPermission(permissionProfile) {
  return permissionProfile === ":danger-full-access" ? "never" : "on-request";
}

function json(res, status, body) {
  const data = JSON.stringify(body);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(data),
    "cache-control": "no-store",
  });
  res.end(data);
}

function candidateTokenMatches(candidate) {
  const expectedBuffer = Buffer.from(config.token);
  const candidateBuffer = Buffer.from(candidate);
  return candidateBuffer.length === expectedBuffer.length && timingSafeEqual(candidateBuffer, expectedBuffer);
}

function tokenMatches(request) {
  const header = request.headers.authorization || "";
  return candidateTokenMatches(header.startsWith("Bearer ") ? header.slice(7) : "");
}

const mediaContentTypes = new Map([
  [".jpg", "image/jpeg"], [".jpeg", "image/jpeg"], [".png", "image/png"],
  [".gif", "image/gif"], [".webp", "image/webp"], [".avif", "image/avif"],
  [".heic", "image/heic"], [".heif", "image/heif"], [".bmp", "image/bmp"],
  [".mp4", "video/mp4"], [".m4v", "video/x-m4v"], [".mov", "video/quicktime"],
  [".webm", "video/webm"], [".mkv", "video/x-matroska"],
  [".mp3", "audio/mpeg"], [".m4a", "audio/mp4"], [".aac", "audio/aac"],
  [".wav", "audio/wav"], [".ogg", "audio/ogg"], [".flac", "audio/flac"],
]);

const uploadExtensions = new Map([
  ["image/jpeg", ".jpg"], ["image/png", ".png"], ["image/webp", ".webp"],
  ["image/gif", ".gif"], ["image/avif", ".avif"], ["image/heic", ".heic"],
  ["image/heif", ".heif"], ["image/bmp", ".bmp"],
]);

function serveMedia(req, res, url) {
  if (!tokenMatches(req) && !candidateTokenMatches(url.searchParams.get("token") || "")) {
    json(res, 401, { ok: false, error: "Unauthorized" });
    return;
  }
  const requested = url.searchParams.get("path") || "";
  let sourcePath;
  try {
    sourcePath = requested.startsWith("file://") ? fileURLToPath(requested) : requested;
  } catch {
    json(res, 400, { ok: false, error: "Invalid media path" });
    return;
  }
  if (!isAbsolute(sourcePath)) {
    json(res, 400, { ok: false, error: "Media path must be absolute" });
    return;
  }

  let resolvedPath;
  let stats;
  try {
    resolvedPath = realpathSync(sourcePath);
    stats = statSync(resolvedPath);
  } catch {
    json(res, 404, { ok: false, error: "Media file not found" });
    return;
  }
  const contentType = mediaContentTypes.get(extname(resolvedPath).toLowerCase());
  if (!stats.isFile() || !contentType) {
    json(res, 415, { ok: false, error: "Unsupported media type" });
    return;
  }

  const commonHeaders = {
    "content-type": contentType,
    "accept-ranges": "bytes",
    "cache-control": "private, max-age=300",
    "x-content-type-options": "nosniff",
  };
  const range = req.headers.range?.match(/^bytes=(\d*)-(\d*)$/);
  if (range) {
    const start = range[1] ? Number(range[1]) : 0;
    const end = range[2] ? Math.min(Number(range[2]), stats.size - 1) : stats.size - 1;
    if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start < 0 || end < start || start >= stats.size) {
      res.writeHead(416, { "content-range": `bytes */${stats.size}` });
      res.end();
      return;
    }
    res.writeHead(206, {
      ...commonHeaders,
      "content-length": end - start + 1,
      "content-range": `bytes ${start}-${end}/${stats.size}`,
    });
    if (req.method === "HEAD") {
      res.end();
      return;
    }
    createReadStream(resolvedPath, { start, end }).pipe(res);
    return;
  }
  res.writeHead(200, { ...commonHeaders, "content-length": stats.size });
  if (req.method === "HEAD") {
    res.end();
    return;
  }
  createReadStream(resolvedPath).pipe(res);
}

function safeUploadName(encodedName, extension) {
  let decoded = "image";
  try {
    decoded = Buffer.from(encodedName || "", "base64").toString("utf8") || "image";
  } catch {
    decoded = "image";
  }
  const withoutExtension = decoded.replace(/\.[^.]+$/, "");
  const stem = withoutExtension
    .normalize("NFKC")
    .replace(/[^\p{L}\p{N}_.-]+/gu, "-")
    .replace(/^[.-]+|[.-]+$/g, "")
    .slice(0, 64) || "image";
  return `${Date.now()}-${randomUUID()}-${stem}${extension}`;
}

function trimUploadedImages() {
  const files = readdirSync(uploadRoot)
    .map((name) => join(uploadRoot, name))
    .filter((path) => {
      try {
        return statSync(path).isFile() && !path.endsWith(".part");
      } catch {
        return false;
      }
    })
    .sort((left, right) => statSync(right).mtimeMs - statSync(left).mtimeMs);
  let retainedBytes = 0;
  files.forEach((path, index) => {
    retainedBytes += statSync(path).size;
    if (index >= MAX_UPLOAD_FILES || retainedBytes > MAX_UPLOAD_STORAGE_BYTES) {
      try { unlinkSync(path); } catch { /* best-effort phone upload cache cleanup */ }
    }
  });
}

function handleImageUpload(req, res) {
  const mimeType = String(req.headers["content-type"] || "").split(";", 1)[0].toLowerCase();
  const extension = uploadExtensions.get(mimeType);
  if (!extension) {
    json(res, 415, { ok: false, error: "只支持常见图片格式" });
    return;
  }
  const declaredSize = Number(req.headers["content-length"]);
  if (Number.isFinite(declaredSize) && (declaredSize <= 0 || declaredSize > MAX_UPLOAD_BYTES)) {
    json(res, 413, { ok: false, error: "单张图片不能超过 15 MB" });
    return;
  }

  const fileName = safeUploadName(req.headers["x-file-name-base64"], extension);
  const destination = join(uploadRoot, fileName);
  const temporary = `${destination}.part`;
  let received = 0;
  const limiter = new Transform({
    transform(chunk, encoding, callback) {
      received += chunk.length;
      if (received > MAX_UPLOAD_BYTES) {
        const error = new Error("单张图片不能超过 15 MB");
        error.code = "UPLOAD_TOO_LARGE";
        callback(error);
      } else {
        callback(null, chunk);
      }
    },
  });
  pipeline(req, limiter, createWriteStream(temporary, { flags: "wx" }), (error) => {
    if (error || received === 0) {
      try { unlinkSync(temporary); } catch { /* no partial file to remove */ }
      if (!res.headersSent) {
        json(res, error?.code === "UPLOAD_TOO_LARGE" ? 413 : 400, {
          ok: false,
          error: error?.message || "图片内容为空",
        });
      }
      return;
    }
    try {
      renameSync(temporary, destination);
      trimUploadedImages();
      json(res, 201, { path: destination, name: fileName, mimeType, size: received });
    } catch (writeError) {
      try { unlinkSync(temporary); } catch { /* no partial file to remove */ }
      json(res, 500, { ok: false, error: writeError.message || "无法保存图片" });
    }
  });
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, "http://bridge.local");
  if (req.method === "GET" && url.pathname === "/health") {
    json(res, 200, {
      ok: true,
      service: "codex-pocket-bridge",
      version: VERSION,
      codexReady: codex.started,
      clients: sockets.size,
    });
    return;
  }
  if ((req.method === "GET" || req.method === "HEAD") && url.pathname === "/media") {
    serveMedia(req, res, url);
    return;
  }
  if (req.method === "POST" && url.pathname === "/upload/image") {
    if (!tokenMatches(req)) {
      json(res, 401, { ok: false, error: "Unauthorized" });
      return;
    }
    handleImageUpload(req, res);
    return;
  }
  if (req.method === "GET" && req.url === "/download") {
    const html = `<!doctype html><meta name="viewport" content="width=device-width"><title>Codex Pocket</title><style>body{font-family:system-ui;background:#f8f8fc;color:#17171c;display:grid;place-items:center;min-height:90vh;margin:0}.card{background:white;padding:32px;border-radius:24px;box-shadow:0 12px 40px #29294a18;max-width:360px;text-align:center}a{display:block;background:#625bff;color:white;text-decoration:none;padding:15px 20px;border-radius:16px;font-weight:650;margin-top:24px}small{color:#686876}</style><div class="card"><h1>Codex Pocket</h1><p>小米手机专用测试版</p><a href="/download/codex-pocket.apk">下载 APK</a><p><small>需要 Android 8.0 或更高版本</small></p></div>`;
    res.writeHead(200, {
      "content-type": "text/html; charset=utf-8",
      "content-length": Buffer.byteLength(html),
      "cache-control": "no-store",
    });
    res.end(html);
    return;
  }
  if ((req.method === "GET" || req.method === "HEAD") && req.url === "/download/codex-pocket.apk") {
    if (!existsSync(apkPath)) {
      json(res, 404, { ok: false, error: "APK is not built yet" });
      return;
    }
    const apkStats = statSync(apkPath);
    const size = apkStats.size;
    const etag = `"codex-pocket-${VERSION}-${size}-${Math.trunc(apkStats.mtimeMs)}"`;
    const lastModified = apkStats.mtime.toUTCString();
    const commonHeaders = {
      "content-type": "application/vnd.android.package-archive",
      "content-disposition": `attachment; filename="codex-pocket-${VERSION}.apk"`,
      "accept-ranges": "bytes",
      "etag": etag,
      "last-modified": lastModified,
      "cache-control": "no-store",
      "x-content-type-options": "nosniff",
    };
    const ifRange = req.headers["if-range"];
    const requestedRange = !ifRange || ifRange === etag || ifRange === lastModified
      ? parseByteRange(req.headers.range, size)
      : null;
    if (requestedRange === false) {
      res.writeHead(416, { ...commonHeaders, "content-range": `bytes */${size}` });
      res.end();
      return;
    }
    if (requestedRange) {
      const { start, end } = requestedRange;
      res.writeHead(206, {
        ...commonHeaders,
        "content-length": end - start + 1,
        "content-range": `bytes ${start}-${end}/${size}`,
      });
      if (req.method === "HEAD") {
        res.end();
        return;
      }
      createReadStream(apkPath, { start, end }).pipe(res);
      return;
    }
    res.writeHead(200, { ...commonHeaders, "content-length": size });
    if (req.method === "HEAD") {
      res.end();
      return;
    }
    createReadStream(apkPath).pipe(res);
    return;
  }
  json(res, 404, { ok: false, error: "Not found" });
});

const wss = new WebSocketServer({ noServer: true, maxPayload: 2 * 1024 * 1024 });

server.on("upgrade", (request, socket, head) => {
  const path = new URL(request.url, "http://bridge.local").pathname;
  if (path !== "/ws" || !tokenMatches(request)) {
    socket.write("HTTP/1.1 401 Unauthorized\r\nConnection: close\r\n\r\n");
    socket.destroy();
    return;
  }
  wss.handleUpgrade(request, socket, head, (ws) => wss.emit("connection", ws, request));
});

function send(ws, payload) {
  if (ws.readyState === ws.OPEN) ws.send(JSON.stringify(payload));
}

function broadcast(payload) {
  const outgoing = payload?.type === "event" ? eventJournal.record(payload) : payload;
  for (const ws of sockets) send(ws, outgoing);
}

async function ensureThreadLoaded(threadId) {
  if (loadedThreads.has(threadId)) return loadedThreads.get(threadId);
  const resumed = await codex.request("thread/resume", { threadId, excludeTurns: true });
  const settings = {
    model: resumed.model || null,
    effort: resumed.reasoningEffort || null,
    serviceTier: resumed.serviceTier || null,
    mode: "default",
    permissionProfile: permissionProfileFromSettings(resumed),
    approvalPolicy: resumed.approvalPolicy || null,
  };
  loadedThreads.set(threadId, settings);
  return settings;
}

function collaborationMode(mode, model, effort) {
  const resolvedMode = mode === "plan" ? "plan" : "default";
  return {
    mode: resolvedMode,
    settings: {
      model,
      reasoning_effort: resolvedMode === "plan" ? "medium" : (effort || null),
      developer_instructions: null,
    },
  };
}

function uploadedImageInputs(params) {
  const rootPrefix = `${realpathSync(uploadRoot)}${sep}`;
  return (Array.isArray(params.images) ? params.images : []).slice(0, 4).map((image) => {
    if (!image || typeof image.path !== "string") throw new Error("图片参数无效");
    const path = realpathSync(image.path);
    const stats = statSync(path);
    if (!path.startsWith(rootPrefix) || !stats.isFile() || !mediaContentTypes.has(extname(path).toLowerCase())) {
      throw new Error("图片不在 Bridge 上传目录中");
    }
    return { type: "localImage", path };
  });
}

function userTurnInput(params) {
  const input = [];
  if (typeof params.text === "string" && params.text.trim()) {
    input.push({ type: "text", text: params.text, text_elements: [] });
  }
  input.push(...uploadedImageInputs(params));
  if (input.length === 0) throw new Error("消息或图片不能为空");
  return input;
}

function mapGoal(goal) {
  if (!goal) return null;
  return {
    threadId: goal.threadId,
    objective: goal.objective,
    status: goal.status,
    tokenBudget: goal.tokenBudget,
    tokensUsed: goal.tokensUsed || 0,
    timeUsedSeconds: goal.timeUsedSeconds || 0,
    createdAt: goal.createdAt,
    updatedAt: goal.updatedAt,
  };
}

function tomlField(source, key) {
  const match = source.match(new RegExp(`^${key.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}\\s*=\\s*(.+)$`, "m"));
  if (!match) return null;
  const raw = match[1].trim();
  if (raw.startsWith('"')) {
    try {
      return JSON.parse(raw);
    } catch {
      return raw.slice(1, -1);
    }
  }
  const number = Number(raw);
  return Number.isFinite(number) ? number : raw;
}

function readAutomations() {
  if (!existsSync(automationsRoot)) return [];
  return readdirSync(automationsRoot, { withFileTypes: true })
    .filter((entry) => entry.isDirectory() && /^[A-Za-z0-9_-]+$/.test(entry.name))
    .flatMap((entry) => {
      const configPath = join(automationsRoot, entry.name, "automation.toml");
      if (!existsSync(configPath)) return [];
      try {
        const source = readFileSync(configPath, "utf8");
        const prompt = String(tomlField(source, "prompt") || "");
        return [{
          id: String(tomlField(source, "id") || entry.name),
          name: String(tomlField(source, "name") || entry.name),
          kind: String(tomlField(source, "kind") || "heartbeat"),
          status: String(tomlField(source, "status") || "PAUSED"),
          rrule: String(tomlField(source, "rrule") || ""),
          targetThreadId: tomlField(source, "target_thread_id") || null,
          promptPreview: prompt.replace(/\s+/g, " ").trim().slice(0, 180),
          createdAt: Number(tomlField(source, "created_at") || 0),
          updatedAt: Number(tomlField(source, "updated_at") || 0),
        }];
      } catch {
        return [];
      }
    })
    .sort((left, right) => right.updatedAt - left.updatedAt);
}

function setAutomationStatus(id, status) {
  if (!/^[A-Za-z0-9_-]+$/.test(id) || !new Set(["ACTIVE", "PAUSED"]).has(status)) {
    throw new Error("无效的自动化状态");
  }
  const configPath = join(automationsRoot, id, "automation.toml");
  if (!existsSync(configPath)) throw new Error("找不到这个自动化任务");
  const source = readFileSync(configPath, "utf8");
  if (!/^status\s*=\s*"(?:ACTIVE|PAUSED)"$/m.test(source)) {
    throw new Error("自动化配置缺少可管理的状态字段");
  }
  const updated = source
    .replace(/^status\s*=\s*"(?:ACTIVE|PAUSED)"$/m, `status = "${status}"`)
    .replace(/^updated_at\s*=\s*\d+$/m, `updated_at = ${Date.now()}`);
  const temporaryPath = `${configPath}.codexpocket.tmp`;
  writeFileSync(temporaryPath, updated, { encoding: "utf8", mode: statSync(configPath).mode });
  renameSync(temporaryPath, configPath);
  return readAutomations().find((automation) => automation.id === id);
}

function flattenRateLimits(result) {
  if (!result) return [];
  const byId = result.rateLimitsByLimitId && Object.keys(result.rateLimitsByLimitId).length
    ? Object.entries(result.rateLimitsByLimitId)
    : [[result.rateLimits?.limitId || "codex", result.rateLimits]];
  return byId.flatMap(([id, snapshot]) => {
    if (!snapshot) return [];
    const name = snapshot.limitName || id || "Codex";
    return [
      snapshot.primary && { name, period: "primary", ...snapshot.primary },
      snapshot.secondary && { name, period: "secondary", ...snapshot.secondary },
      snapshot.individualLimit && {
        name: `${name} · 费用限额`,
        period: "spend",
        usedPercent: Math.max(0, 100 - Number(snapshot.individualLimit.remainingPercent || 0)),
        remainingPercent: Number(snapshot.individualLimit.remainingPercent || 0),
        resetsAt: snapshot.individualLimit.resetsAt,
        limit: snapshot.individualLimit.limit,
        used: snapshot.individualLimit.used,
      },
    ].filter(Boolean).map((window) => ({
      ...window,
      usedPercent: Number(window.usedPercent || 0),
      remainingPercent: window.remainingPercent ?? Math.max(0, 100 - Number(window.usedPercent || 0)),
    }));
  });
}

async function handleRequest(message) {
  const params = message.params || {};
  switch (message.method) {
    case "bridge.ping":
      return { now: Date.now() };
    case "events.replay":
      return eventJournal.replay({
        instanceId: params.serverInstanceId,
        afterSequence: Number(params.afterSequence) || 0,
      });
    case "threads.list": {
      const result = await codex.request("thread/list", {
        limit: Math.min(Math.max(params.limit || 30, 1), 100),
        sortKey: "updated_at",
        sortDirection: "desc",
        archived: false,
      });
      const goals = await Promise.allSettled(
        result.data.map((thread) => codex.request("thread/goal/get", { threadId: thread.id })),
      );
      return {
        threads: result.data.map((thread, index) => {
          const goalResult = goals[index];
          const goal = goalResult.status === "fulfilled" ? mapGoal(goalResult.value.goal) : null;
          return { ...mapThreadSummary(thread), goal };
        }),
        nextCursor: result.nextCursor || null,
      };
    }
    case "models.list": {
      const result = await codex.request("model/list", {
        limit: Math.min(Math.max(params.limit || 100, 1), 100),
        includeHidden: false,
      });
      return {
        models: result.data.map(mapModel),
        nextCursor: result.nextCursor || null,
      };
    }
    case "modes.list": {
      const result = await codex.request("collaborationMode/list", {});
      return {
        modes: result.data.map((mode) => ({
          id: mode.mode || "default",
          name: mode.name,
          model: mode.model || null,
          effort: mode.reasoning_effort || null,
        })),
      };
    }
    case "permissions.list": {
      const permissionParams = {
        limit: Math.min(Math.max(params.limit || 100, 1), 100),
      };
      if (typeof params.cwd === "string" && isAbsolute(params.cwd)) {
        permissionParams.cwd = normalize(params.cwd);
      }
      const result = await codex.request("permissionProfile/list", permissionParams);
      return {
        profiles: result.data.map((profile) => ({
          id: profile.id,
          description: profile.description || null,
          allowed: profile.allowed === true,
        })),
        nextCursor: result.nextCursor || null,
      };
    }
    case "automations.list":
      return { automations: readAutomations() };
    case "automation.status.set": {
      const automation = setAutomationStatus(params.id, params.active === true ? "ACTIVE" : "PAUSED");
      broadcast({ type: "event", event: "automation.updated", data: { automation } });
      return { automation };
    }
    case "account.status": {
      const [accountResult, limitsResult, usageResult] = await Promise.allSettled([
        codex.request("account/read", { refreshToken: false }),
        codex.request("account/rateLimits/read", {}),
        codex.request("account/usage/read", {}),
      ]);
      const account = accountResult.status === "fulfilled" ? accountResult.value.account : null;
      const limits = limitsResult.status === "fulfilled" ? limitsResult.value : null;
      const usage = usageResult.status === "fulfilled" ? usageResult.value : null;
      return {
        account: account ? {
          type: account.type,
          email: account.email || null,
          planType: account.planType || null,
        } : null,
        limits: flattenRateLimits(limits),
        credits: limits?.rateLimits?.credits || null,
        resetCredits: limits?.rateLimitResetCredits?.availableCount ?? null,
        usage: usage?.summary || null,
        unavailable: [
          accountResult.status === "rejected" ? "account" : null,
          limitsResult.status === "rejected" ? "limits" : null,
          usageResult.status === "rejected" ? "usage" : null,
        ].filter(Boolean),
      };
    }
    case "directories.list": {
      if (typeof params.path !== "string" || !isAbsolute(params.path)) {
        throw new Error("文件夹路径必须是 Mac 上的绝对路径");
      }
      const currentPath = normalize(params.path);
      const result = await codex.request("fs/readDirectory", { path: currentPath });
      return {
        path: currentPath,
        parent: dirname(currentPath),
        directories: result.entries
          .filter((entry) => entry.isDirectory && entry.fileName !== "." && entry.fileName !== "..")
          .map((entry) => ({ name: entry.fileName, path: join(currentPath, entry.fileName) }))
          .sort((left, right) => left.name.localeCompare(right.name, "zh-CN")),
      };
    }
    case "thread.create": {
      if (typeof params.cwd !== "string" || !isAbsolute(params.cwd)) {
        throw new Error("请选择 Mac 上的有效项目目录");
      }
      const startParams = {
        cwd: normalize(params.cwd),
        ephemeral: Boolean(params.ephemeral),
        sessionStartSource: "startup",
        threadSource: "codex-pocket",
      };
      const permissionProfile = resolvePermissionProfile(params.permissionProfile);
      startParams.permissions = permissionProfile;
      startParams.approvalPolicy = approvalPolicyForPermission(permissionProfile);
      if (typeof params.model === "string" && params.model) startParams.model = params.model;
      if (params.fastMode === true) startParams.serviceTier = "priority";
      const result = await codex.request("thread/start", startParams);
      const settings = {
        model: result.model || startParams.model || null,
        effort: params.effort || result.reasoningEffort || null,
        serviceTier: result.serviceTier || startParams.serviceTier || null,
        mode: params.mode === "plan" ? "plan" : "default",
        permissionProfile,
        approvalPolicy: startParams.approvalPolicy,
      };
      loadedThreads.set(result.thread.id, settings);
      return { thread: mapThreadSummary(result.thread), settings };
    }
    case "thread.delete": {
      if (typeof params.threadId !== "string" || !params.threadId) {
        throw new Error("缺少任务 ID");
      }
      try {
        await codex.request("thread/delete", { threadId: params.threadId });
      } catch (error) {
        if (!(error instanceof Error) || !error.message.includes("no rollout found")) throw error;
        await codex.request("thread/unarchive", { threadId: params.threadId });
        await codex.request("thread/delete", { threadId: params.threadId });
      }
      loadedThreads.delete(params.threadId);
      return { deleted: true };
    }
    case "thread.archive": {
      if (typeof params.threadId !== "string" || !params.threadId) {
        throw new Error("缺少任务 ID");
      }
      try {
        await codex.request("thread/archive", { threadId: params.threadId });
      } catch (error) {
        if (!(error instanceof Error) || !error.message.includes("no rollout found")) throw error;
        // A brand-new blank thread has no rollout to move into the archive.
        // Removing that empty shell matches what the user expects from Archive.
        await codex.request("thread/delete", { threadId: params.threadId });
      }
      loadedThreads.delete(params.threadId);
      return { archived: true };
    }
    case "thread.unarchive": {
      if (typeof params.threadId !== "string" || !params.threadId) {
        throw new Error("缺少任务 ID");
      }
      const result = await codex.request("thread/unarchive", { threadId: params.threadId });
      return { thread: mapThreadSummary(result.thread) };
    }
    case "thread.read": {
      const settings = await ensureThreadLoaded(params.threadId);
      const [threadResult, goalResult] = await Promise.all([
        codex.request("thread/read", {
          threadId: params.threadId,
          includeTurns: true,
        }),
        codex.request("thread/goal/get", { threadId: params.threadId }),
      ]);
      const requestedLimit = Number(params.messageLimit);
      const messageLimit = Number.isFinite(requestedLimit)
        ? Math.min(200, Math.max(20, Math.trunc(requestedLimit)))
        : DEFAULT_MESSAGE_LIMIT;
      return {
        ...mapThreadDetail(threadResult.thread, {
          messageLimit,
          beforeMessageId: params.beforeMessageId,
          clientMessageIds: Array.isArray(params.clientMessageIds)
            ? params.clientMessageIds.slice(0, 20)
            : [],
        }),
        settings,
        goal: mapGoal(goalResult.goal),
      };
    }
    case "thread.resume": {
      const settings = await ensureThreadLoaded(params.threadId);
      return { threadId: params.threadId, loaded: true, settings };
    }
    case "thread.mode.set": {
      if (params.mode !== "default" && params.mode !== "plan") {
        throw new Error("不支持的 Codex 模式");
      }
      const current = await ensureThreadLoaded(params.threadId);
      const model = typeof params.model === "string" && params.model ? params.model : current.model;
      const effort = typeof params.effort === "string" && params.effort ? params.effort : current.effort;
      if (!model) throw new Error("需要先选择一个模型");
      const selected = collaborationMode(params.mode, model, effort);
      await codex.request("thread/settings/update", {
        threadId: params.threadId,
        collaborationMode: selected,
      });
      const settings = {
        ...current,
        model: selected.settings.model,
        effort: selected.settings.reasoning_effort,
        mode: selected.mode,
      };
      loadedThreads.set(params.threadId, settings);
      return { settings };
    }
    case "thread.fast.set": {
      const current = await ensureThreadLoaded(params.threadId);
      const serviceTier = params.enabled === true ? "priority" : null;
      await codex.request("thread/settings/update", {
        threadId: params.threadId,
        serviceTier,
      });
      const settings = { ...current, serviceTier };
      loadedThreads.set(params.threadId, settings);
      return { settings };
    }
    case "thread.permissions.set": {
      const current = await ensureThreadLoaded(params.threadId);
      const permissionProfile = resolvePermissionProfile(params.permissionProfile);
      const approvalPolicy = approvalPolicyForPermission(permissionProfile);
      await codex.request("thread/settings/update", {
        threadId: params.threadId,
        permissions: permissionProfile,
        approvalPolicy,
      });
      const settings = { ...current, permissionProfile, approvalPolicy };
      loadedThreads.set(params.threadId, settings);
      return { settings };
    }
    case "thread.goal.set": {
      const objective = typeof params.objective === "string" ? params.objective.trim() : "";
      if (!objective) throw new Error("请输入目标内容");
      const goalParams = {
        threadId: params.threadId,
        objective,
        status: "active",
      };
      if (Number.isSafeInteger(params.tokenBudget) && params.tokenBudget > 0) {
        goalParams.tokenBudget = params.tokenBudget;
      }
      const result = await codex.request("thread/goal/set", goalParams);
      return { goal: mapGoal(result.goal) };
    }
    case "thread.goal.status": {
      const allowed = new Set(["active", "paused"]);
      if (!allowed.has(params.status)) throw new Error("不支持的目标状态");
      const result = await codex.request("thread/goal/set", {
        threadId: params.threadId,
        status: params.status,
      });
      return { goal: mapGoal(result.goal) };
    }
    case "thread.goal.clear": {
      await codex.request("thread/goal/clear", { threadId: params.threadId });
      return { cleared: true };
    }
    case "turn.start": {
      const current = await ensureThreadLoaded(params.threadId);
      const turnParams = {
        threadId: params.threadId,
        clientUserMessageId: params.clientMessageId || null,
        input: userTurnInput(params),
        summary: "auto",
      };
      const permissionProfile = resolvePermissionProfile(params.permissionProfile);
      turnParams.permissions = permissionProfile;
      turnParams.approvalPolicy = approvalPolicyForPermission(permissionProfile);
      if (typeof params.model === "string" && params.model) turnParams.model = params.model;
      if (typeof params.effort === "string" && params.effort) turnParams.effort = params.effort;
      const mode = params.mode === "plan" ? "plan" : (current.mode || "default");
      const model = turnParams.model || current.model;
      const effort = turnParams.effort || current.effort;
      if (model) turnParams.collaborationMode = collaborationMode(mode, model, effort);
      if (typeof params.fastMode === "boolean") {
        turnParams.serviceTier = params.fastMode ? "priority" : null;
      }
      const result = await codex.request("turn/start", turnParams);
      loadedThreads.set(params.threadId, {
        model: model || null,
        effort: mode === "plan" ? "medium" : (effort || null),
        serviceTier: params.fastMode === true ? "priority" : null,
        mode,
        permissionProfile,
        approvalPolicy: turnParams.approvalPolicy,
      });
      return { turnId: result.turn.id, status: result.turn.status };
    }
    case "turn.steer": {
      if (!params.threadId || !params.turnId) throw new Error("当前没有可以引导的运行任务");
      const result = await codex.request("turn/steer", {
        threadId: params.threadId,
        expectedTurnId: params.turnId,
        clientUserMessageId: params.clientMessageId || null,
        input: userTurnInput(params),
      });
      return { turnId: result.turnId, steered: true };
    }
    case "turn.interrupt": {
      await codex.request("turn/interrupt", {
        threadId: params.threadId,
        turnId: params.turnId,
      });
      return { interrupted: true };
    }
    case "codex.respond": {
      const appServerId = pendingServerRequests.get(params.requestId);
      if (appServerId === undefined) throw new Error("授权请求已过期");
      pendingServerRequests.delete(params.requestId);
      codex.respond(appServerId, params.result);
      return { delivered: true };
    }
    default:
      throw new Error(`Unsupported method: ${message.method}`);
  }
}

wss.on("connection", (ws) => {
  ws.isAlive = true;
  sockets.add(ws);
  send(ws, {
    type: "hello",
    version: VERSION,
    serverTime: Date.now(),
    serverInstanceId: eventJournal.instanceId,
    eventSequence: eventJournal.sequence,
    capabilities: [
      "threads", "create", "archive", "directories", "history", "streaming",
      "interrupt", "steer", "models", "reasoning", "approvals", "media", "usage",
      "modes", "goal", "fast", "automations", "permissions",
    ],
  });

  ws.on("message", async (buffer) => {
    let message;
    try {
      message = JSON.parse(buffer.toString("utf8"));
      if (message.type !== "request" || !message.id || !message.method) {
        throw new Error("Invalid request envelope");
      }
      const result = await handleRequest(message);
      send(ws, { type: "response", id: message.id, ok: true, result });
    } catch (error) {
      send(ws, {
        type: "response",
        id: message?.id || null,
        ok: false,
        error: error instanceof Error ? error.message : String(error),
      });
    }
  });
  ws.on("close", () => sockets.delete(ws));
  ws.on("error", () => sockets.delete(ws));
  ws.on("pong", () => {
    ws.isAlive = true;
  });
});

const socketHeartbeat = setInterval(() => {
  for (const ws of sockets) {
    if (!ws.isAlive) {
      sockets.delete(ws);
      ws.terminate();
      continue;
    }
    ws.isAlive = false;
    ws.ping();
  }
}, 30_000);
socketHeartbeat.unref();

codex.on("notification", (message) => {
  if (message.method === "thread/settings/updated") {
    const threadId = message.params?.threadId;
    const threadSettings = message.params?.threadSettings;
    if (threadId && threadSettings) {
      loadedThreads.set(threadId, {
        model: threadSettings.model || null,
        effort: threadSettings.effort || null,
        serviceTier: threadSettings.serviceTier || null,
        mode: threadSettings.collaborationMode?.mode || "default",
        permissionProfile: permissionProfileFromSettings(threadSettings),
        approvalPolicy: threadSettings.approvalPolicy || null,
      });
    }
  }
  const mapped = mapNotification(message);
  if (mapped) broadcast({ type: "event", ...mapped });
});

codex.on("serverRequest", (message) => {
  const requestId = randomUUID();
  pendingServerRequests.set(requestId, message.id);
  broadcast({
    type: "event",
    event: "codex.request",
    data: { requestId, method: message.method, params: message.params || {} },
  });
});

codex.on("log", (line) => {
  if (line) console.error(`[codex] ${line}`);
});

codex.on("exit", (error) => {
  console.error(error.message);
  broadcast({ type: "event", event: "bridge.error", data: { message: error.message } });
  server.close(() => process.exit(1));
});

await codex.start();
server.listen(config.port, config.host, () => {
  console.log(`Codex Pocket Bridge is ready on ws://${config.host}:${config.port}/ws`);
  console.log(`Health check: http://${config.host}:${config.port}/health`);
  console.log(`APK download: http://${config.host}:${config.port}/download`);
  console.log(`Pairing token is stored in ${config.configPath}`);
});

function shutdown() {
  clearInterval(socketHeartbeat);
  for (const ws of sockets) ws.close(1001, "Bridge shutting down");
  server.close();
  codex.stop();
}

process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
