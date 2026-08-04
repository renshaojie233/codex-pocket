import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import WebSocket from "ws";

const config = JSON.parse(readFileSync(resolve("data/config.json"), "utf8"));
const ws = new WebSocket(`ws://${config.host}:${config.port}/ws`, {
  headers: { Authorization: `Bearer ${config.token}` },
});

let nextId = 1;
const pending = new Map();

function request(method, params = {}) {
  const id = `mode-check-${nextId++}`;
  return new Promise((resolveRequest, rejectRequest) => {
    pending.set(id, { resolveRequest, rejectRequest });
    ws.send(JSON.stringify({ type: "request", id, method, params }));
  });
}

ws.on("message", (buffer) => {
  const message = JSON.parse(buffer.toString("utf8"));
  if (message.type !== "response") return;
  const callback = pending.get(message.id);
  if (!callback) return;
  pending.delete(message.id);
  if (message.ok) callback.resolveRequest(message.result);
  else callback.rejectRequest(new Error(message.error));
});

await new Promise((resolveConnection, rejectConnection) => {
  const timeout = setTimeout(() => rejectConnection(new Error("Bridge mode check timed out")), 10_000);
  ws.on("message", (buffer) => {
    const message = JSON.parse(buffer.toString("utf8"));
    if (message.type === "hello") {
      clearTimeout(timeout);
      resolveConnection();
    }
  });
  ws.once("error", rejectConnection);
});

let threadId;
try {
  const modes = await request("modes.list");
  const automations = await request("automations.list");
  const created = await request("thread.create", {
    cwd: resolve(".."),
    ephemeral: false,
    model: "gpt-5.6-sol",
    effort: "medium",
  });
  threadId = created.thread.id;
  await request("thread.mode.set", {
    threadId,
    mode: "plan",
    model: "gpt-5.6-sol",
    effort: "medium",
  });
  await request("thread.fast.set", { threadId, enabled: true });
  await request("thread.goal.set", {
    threadId,
    objective: "Codex Pocket Goal API verification",
    tokenBudget: 1000,
  });
  const detail = await request("thread.read", { threadId });
  await request("thread.goal.status", { threadId, status: "paused" });
  await request("thread.goal.clear", { threadId });
  await request("thread.fast.set", { threadId, enabled: false });
  await request("thread.mode.set", {
    threadId,
    mode: "default",
    model: "gpt-5.6-sol",
    effort: "medium",
  });

  console.log(JSON.stringify({
    modes: modes.modes.map((mode) => mode.id),
    automations: automations.automations.length,
    activeAutomations: automations.automations.filter((automation) => automation.status === "ACTIVE").length,
    goal: detail.goal?.status,
    goalBudget: detail.goal?.tokenBudget,
    mode: detail.settings?.mode,
    fast: detail.settings?.serviceTier,
  }, null, 2));
} finally {
  if (threadId) {
    try {
      await request("thread.delete", { threadId });
    } catch {
      // A blank probe without a rollout can race with desktop catalog cleanup.
    }
  }
  ws.close();
}
