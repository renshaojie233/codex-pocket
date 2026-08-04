import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import WebSocket from "ws";

const config = JSON.parse(readFileSync(resolve("data/config.json"), "utf8"));
const ws = new WebSocket(`ws://${config.host}:${config.port}/ws`, {
  headers: { Authorization: `Bearer ${config.token}` },
});
const timeout = setTimeout(() => {
  console.error("Account-status check timed out");
  process.exit(1);
}, 15_000);

ws.on("message", (buffer) => {
  const message = JSON.parse(buffer.toString("utf8"));
  if (message.type === "hello") {
    ws.send(JSON.stringify({ type: "request", id: "account", method: "account.status", params: {} }));
    return;
  }
  if (message.type === "response" && message.id === "account") {
    clearTimeout(timeout);
    if (!message.ok) throw new Error(message.error);
    console.log(JSON.stringify({
      account: message.result.account ? "available" : "unavailable",
      plan: message.result.account?.planType || null,
      limitWindows: message.result.limits?.length || 0,
      usage: message.result.usage ? "available" : "unavailable",
      unavailable: message.result.unavailable || [],
    }, null, 2));
    ws.close();
  }
});

ws.on("error", (error) => {
  clearTimeout(timeout);
  console.error(error.message);
  process.exit(1);
});
