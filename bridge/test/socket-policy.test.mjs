import test from "node:test";
import assert from "node:assert/strict";
import {
  filterReplayForClient,
  shouldDeliverSocketPayload,
  socketClientMode,
} from "../src/socket-policy.mjs";

test("recognizes foreground and background websocket clients", () => {
  assert.equal(socketClientMode("/ws"), "foreground");
  assert.equal(socketClientMode("/ws?client=background"), "background");
});

test("background clients skip high-volume streaming deltas", () => {
  assert.equal(
    shouldDeliverSocketPayload("background", { type: "event", event: "agent.delta" }),
    false,
  );
  assert.equal(
    shouldDeliverSocketPayload("background", { type: "event", event: "tool.output" }),
    false,
  );
  assert.equal(
    shouldDeliverSocketPayload("background", { type: "event", event: "turn.completed" }),
    true,
  );
  assert.equal(shouldDeliverSocketPayload("background", { type: "response" }), true);
});

test("background replay keeps completion events and advances the original cursor", () => {
  const result = filterReplayForClient("background", "events.replay", {
    latestSequence: 9,
    events: [
      { type: "event", event: "reasoning.delta", sequence: 7 },
      { type: "event", event: "item.completed", sequence: 8 },
      { type: "event", event: "turn.completed", sequence: 9 },
    ],
  });

  assert.equal(result.latestSequence, 9);
  assert.deepEqual(result.events.map((event) => event.sequence), [8, 9]);
});
