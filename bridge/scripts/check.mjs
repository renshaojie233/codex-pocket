import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import WebSocket from "ws";

const config = JSON.parse(readFileSync(resolve("data/config.json"), "utf8"));
const ws = new WebSocket(`ws://${config.host}:${config.port}/ws`, {
  headers: { Authorization: `Bearer ${config.token}` },
});

const timeout = setTimeout(() => {
  console.error("Bridge check timed out");
  process.exit(1);
}, 10_000);

let threadResult;

ws.on("message", (buffer) => {
  const message = JSON.parse(buffer.toString("utf8"));
  if (message.type === "hello") {
    ws.send(JSON.stringify({ type: "request", id: "check-1", method: "threads.list", params: { limit: 3 } }));
    return;
  }
  if (message.type === "response" && message.id === "check-1") {
    if (!message.ok) throw new Error(message.error);
    const first = message.result.threads[0];
    ws.send(JSON.stringify({
      type: "request",
      id: "check-2",
      method: "thread.read",
      params: { threadId: first.id },
    }));
    return;
  }
  if (message.type === "response" && message.id === "check-2") {
    if (!message.ok) throw new Error(message.error);
    threadResult = message.result;
    ws.send(JSON.stringify({ type: "request", id: "check-3", method: "models.list", params: { limit: 100 } }));
    return;
  }
  if (message.type === "response" && message.id === "check-3") {
    clearTimeout(timeout);
    if (!message.ok) throw new Error(message.error);
    console.log(JSON.stringify({
      thread: threadResult.thread.title,
      messages: threadResult.messages.length,
      currentModel: threadResult.settings?.model || null,
      models: message.result.models.map((model) => ({
        id: model.id,
        efforts: model.efforts.map((effort) => effort.id),
      })),
    }, null, 2));
    ws.close();
  }
});

ws.on("error", (error) => {
  clearTimeout(timeout);
  console.error(error.message);
  process.exit(1);
});
