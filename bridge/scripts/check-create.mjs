import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import WebSocket from "ws";

const config = JSON.parse(readFileSync(resolve("data/config.json"), "utf8"));
const cwd = resolve("..");
const ws = new WebSocket(`ws://${config.host}:${config.port}/ws`, {
  headers: { Authorization: `Bearer ${config.token}` },
});

const timeout = setTimeout(() => {
  console.error("Create-thread check timed out");
  process.exit(1);
}, 15_000);
let createdThreadId;

function request(id, method, params) {
  ws.send(JSON.stringify({ type: "request", id, method, params }));
}

ws.on("message", (buffer) => {
  const message = JSON.parse(buffer.toString("utf8"));
  if (message.type === "hello") {
    request("directory", "directories.list", { path: cwd });
    return;
  }
  if (message.type !== "response") return;
  if (!message.ok) throw new Error(message.error);
  if (message.id === "directory") {
    if (message.result.path !== cwd || !Array.isArray(message.result.directories)) {
      throw new Error("Directory browser returned an invalid response");
    }
    request("create", "thread.create", { cwd, ephemeral: false });
    return;
  }
  if (message.id === "create") {
    const threadId = message.result.thread?.id;
    if (!threadId) throw new Error("Thread creation returned no ID");
    createdThreadId = threadId;
    request("archive", "thread.archive", { threadId });
    return;
  }
  if (message.id === "archive") {
    clearTimeout(timeout);
    console.log(JSON.stringify({
      directories: "ok", create: "ok", archive: "ok", cleanup: "archive removed blank probe",
    }, null, 2));
    ws.close();
  }
});

ws.on("error", (error) => {
  clearTimeout(timeout);
  console.error(error.message);
  process.exit(1);
});
