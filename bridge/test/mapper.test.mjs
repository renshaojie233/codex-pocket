import test from "node:test";
import assert from "node:assert/strict";
import { mapItem, mapModel, mapNotification, mapThreadDetail, mapThreadSummary } from "../src/mapper.mjs";

test("maps thread summary with a useful title", () => {
  const mapped = mapThreadSummary({
    id: "thread-1",
    name: null,
    preview: "Hello from Codex",
    cwd: "/tmp/project",
    status: { type: "idle" },
    createdAt: 1,
    updatedAt: 2,
    recencyAt: 3,
    isPinned: true,
  });
  assert.equal(mapped.title, "Hello from Codex");
  assert.equal(mapped.status, "idle");
  assert.equal(mapped.isPinned, true);
});

test("maps user and assistant messages", () => {
  assert.deepEqual(
    mapItem({ id: "u1", type: "userMessage", content: [{ type: "text", text: "你好" }] }, "turn-1"),
    { id: "u1", turnId: "turn-1", kind: "userMessage", role: "user", text: "你好", attachments: [] },
  );
  assert.equal(
    mapItem({ id: "a1", type: "agentMessage", text: "收到" }, "turn-1").text,
    "收到",
  );
  assert.equal(
    mapItem({ id: "a2", type: "agentMessage", text: "处理中", phase: "commentary" }, "turn-1").phase,
    "commentary",
  );
  assert.equal(
    mapItem({ id: "a3", type: "agentMessage", text: "完成", phase: "final_answer" }, "turn-1").phase,
    "final_answer",
  );
});

test("maps image and video media for the mobile renderer", () => {
  const user = mapItem({
    id: "u-media",
    type: "userMessage",
    content: [
      { type: "text", text: "看看这些" },
      { type: "localImage", path: "/tmp/screenshot.png" },
      { type: "image", url: "https://example.com/reference.webp" },
    ],
  }, "turn-media");
  assert.deepEqual(user.attachments.map(({ kind, isLocal }) => ({ kind, isLocal })), [
    { kind: "image", isLocal: true },
    { kind: "image", isLocal: false },
  ]);

  const assistant = mapItem({
    id: "a-media",
    type: "agentMessage",
    text: "[演示视频](/tmp/demo.mp4)\n\n![结果图](https://example.com/result.png)",
  }, "turn-media");
  assert.deepEqual(assistant.attachments.map((attachment) => attachment.kind), ["video", "image"]);
});

test("flattens turn items into mobile messages", () => {
  const detail = mapThreadDetail({
    id: "thread-1",
    name: "Task",
    preview: "Task",
    cwd: "/tmp/project",
    status: { type: "notLoaded" },
    createdAt: 1,
    updatedAt: 2,
    recencyAt: 2,
    isPinned: false,
    turns: [
      {
        id: "turn-1",
        items: [
          { id: "u1", type: "userMessage", content: [{ type: "text", text: "开始" }] },
          { id: "a1", type: "agentMessage", text: "完成" },
        ],
      },
    ],
  });
  assert.equal(detail.messages.length, 2);
  assert.deepEqual(detail.messages.map((message) => message.role), ["user", "assistant"]);
  assert.equal(detail.totalMessageCount, 2);
  assert.equal(detail.hasOlderMessages, false);
  assert.equal(detail.messageWindowStart, 0);
  assert.equal(detail.messageWindowEnd, 2);
});

test("returns only the newest requested message window", () => {
  const detail = mapThreadDetail({
    id: "thread-window",
    preview: "Window",
    cwd: "/tmp/project",
    status: { type: "idle" },
    turns: [{
      id: "turn-window",
      items: [1, 2, 3, 4].map((number) => ({
        id: `message-${number}`,
        type: "agentMessage",
        text: `Message ${number}`,
      })),
    }],
  }, { messageLimit: 2 });

  assert.deepEqual(detail.messages.map((message) => message.id), ["message-3", "message-4"]);
  assert.equal(detail.totalMessageCount, 4);
  assert.equal(detail.hasOlderMessages, true);
});

test("pages backward before the oldest visible message", () => {
  const detail = mapThreadDetail({
    id: "thread-pages",
    preview: "Pages",
    turns: [{
      id: "turn-pages",
      items: [1, 2, 3, 4, 5].map((number) => ({
        id: `message-${number}`,
        type: "agentMessage",
        text: `Message ${number}`,
      })),
    }],
  }, { messageLimit: 2, beforeMessageId: "message-4" });

  assert.deepEqual(detail.messages.map((message) => message.id), ["message-2", "message-3"]);
  assert.equal(detail.messageWindowStart, 1);
  assert.equal(detail.messageWindowEnd, 3);
  assert.equal(detail.hasOlderMessages, true);
  assert.equal(detail.hasNewerMessages, true);
  assert.equal(detail.cursorFound, true);
});

test("confirms optimistic client message ids outside the returned message window", () => {
  const detail = mapThreadDetail({
    id: "thread-receipts",
    preview: "Receipts",
    turns: [{
      id: "turn-receipts",
      items: [
        {
          id: "server-old",
          clientId: "client-confirmed",
          type: "userMessage",
          content: [{ type: "text", text: "confirmed" }],
        },
        ...[1, 2, 3].map((number) => ({
          id: `latest-${number}`,
          type: "agentMessage",
          text: `Latest ${number}`,
        })),
      ],
    }],
  }, { messageLimit: 2, clientMessageIds: ["client-confirmed", "client-missing"] });

  assert.deepEqual(detail.messages.map((message) => message.id), ["latest-2", "latest-3"]);
  assert.deepEqual(detail.confirmedClientMessageIds, ["client-confirmed"]);
});

test("maps selectable models and reasoning efforts", () => {
  assert.deepEqual(
    mapModel({
      id: "catalog-id",
      model: "gpt-5.6",
      displayName: "GPT-5.6",
      description: "Frontier model",
      supportedReasoningEfforts: [
        { reasoningEffort: "medium", description: "Balanced" },
        { reasoningEffort: "high", description: "Thorough" },
      ],
      defaultReasoningEffort: "medium",
      isDefault: true,
    }),
    {
      id: "gpt-5.6",
      displayName: "GPT-5.6",
      description: "Frontier model",
      efforts: [
        { id: "medium", description: "Balanced" },
        { id: "high", description: "Thorough" },
      ],
      defaultEffort: "medium",
      isDefault: true,
      serviceTiers: [],
      defaultServiceTier: null,
    },
  );
});

test("maps reasoning and tool progress notifications", () => {
  assert.equal(
    mapNotification({
      method: "item/reasoning/summaryTextDelta",
      params: { threadId: "t1", turnId: "r1", itemId: "i1", delta: "检查项目" },
    }).event,
    "reasoning.delta",
  );
  const started = mapNotification({
    method: "item/started",
    params: {
      threadId: "t1",
      turnId: "r1",
      item: { id: "cmd1", type: "commandExecution", command: "npm test", status: "inProgress" },
    },
  });
  assert.equal(started.data.activity.title, "正在执行命令");
  assert.equal(started.data.item.command, "npm test");
});

test("maps shared thread settings and active turn state", () => {
  const settings = mapNotification({
    method: "thread/settings/updated",
    params: {
      threadId: "t1",
      threadSettings: {
        model: "gpt-5.6-sol",
        effort: "high",
        serviceTier: "priority",
        collaborationMode: { mode: "plan" },
        approvalPolicy: "never",
        activePermissionProfile: { id: ":danger-full-access", extends: null },
      },
    },
  });
  assert.deepEqual(settings, {
    event: "thread.settings",
    data: {
      threadId: "t1",
      model: "gpt-5.6-sol",
      effort: "high",
      serviceTier: "priority",
      mode: "plan",
      permissionProfile: ":danger-full-access",
      approvalPolicy: "never",
    },
  });

  const detail = mapThreadDetail({
    id: "t1",
    preview: "同步",
    cwd: "/tmp/project",
    status: { type: "active", activeFlags: [] },
    createdAt: 1,
    updatedAt: 2,
    isPinned: false,
    turns: [{ id: "turn-live", status: "inProgress", items: [] }],
  });
  assert.equal(detail.activeTurnId, "turn-live");
});
