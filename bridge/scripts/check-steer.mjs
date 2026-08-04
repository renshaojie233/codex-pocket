import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import WebSocket from "ws";

const config = JSON.parse(readFileSync(resolve("data/config.json"), "utf8"));
const cwd = resolve("..");
const cleanupOnlyThreadId = process.argv[2] || null;
const ws = new WebSocket(`ws://${config.host}:${config.port}/ws`, {
  headers: { Authorization: `Bearer ${config.token}` },
});
const timeout = setTimeout(() => {
  console.error("Turn-steer check timed out");
  process.exit(1);
}, 30_000);

let threadId;
let turnId;
let startAcknowledged = false;
let turnStarted = false;
let steerSent = false;
let interruptAcknowledged = false;
let turnCompleted = false;
let deleting = false;

function request(id, method, params) {
  ws.send(JSON.stringify({ type: "request", id, method, params }));
}

function maybeDelete() {
  if (!deleting && interruptAcknowledged && turnCompleted) {
    deleting = true;
    request("delete", "thread.delete", { threadId });
  }
}

function maybeSteer() {
  if (!steerSent && startAcknowledged && turnStarted) {
    steerSent = true;
    request("steer", "turn.steer", {
      threadId,
      turnId,
      text: "引导测试：停止等待，改为只确认已收到这条引导。",
      clientMessageId: crypto.randomUUID(),
    });
  }
}

ws.on("message", (buffer) => {
  const message = JSON.parse(buffer.toString("utf8"));
  if (message.type === "hello") {
    if (cleanupOnlyThreadId) {
      threadId = cleanupOnlyThreadId;
      request("delete", "thread.delete", { threadId });
      return;
    }
    request("create", "thread.create", { cwd, ephemeral: false });
    return;
  }
  if (message.type === "event" && message.event === "turn.completed" && message.data?.threadId === threadId) {
    turnCompleted = true;
    maybeDelete();
    return;
  }
  if (message.type === "event" && message.event === "turn.started" && message.data?.threadId === threadId) {
    turnStarted = true;
    turnId = message.data.turn?.id || turnId;
    maybeSteer();
    return;
  }
  if (message.type !== "response") return;
  if (!message.ok) throw new Error(`${message.id}: ${message.error}`);
  if (message.id === "create") {
    threadId = message.result.thread.id;
    request("start", "turn.start", {
      threadId,
      text: "这是 Codex Pocket 的自动化引导测试。请执行命令 sleep 20，完成后再回复。",
      clientMessageId: crypto.randomUUID(),
    });
  } else if (message.id === "start") {
    turnId = message.result.turnId;
    startAcknowledged = true;
    maybeSteer();
  } else if (message.id === "steer") {
    request("interrupt", "turn.interrupt", { threadId, turnId });
  } else if (message.id === "interrupt") {
    interruptAcknowledged = true;
    maybeDelete();
  } else if (message.id === "delete") {
    clearTimeout(timeout);
    console.log(JSON.stringify(cleanupOnlyThreadId ? { cleanup: "ok" } : {
      create: "ok", start: "ok", steer: "ok", interrupt: "ok", cleanup: "ok",
    }, null, 2));
    ws.close();
  }
});

ws.on("error", (error) => {
  clearTimeout(timeout);
  console.error(error.message);
  process.exit(1);
});
