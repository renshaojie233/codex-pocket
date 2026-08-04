import test from "node:test";
import assert from "node:assert/strict";
import { EventJournal } from "../src/event-journal.mjs";

test("replays missed events in sequence for the same Bridge instance", () => {
  const journal = new EventJournal({ instanceId: "bridge-a", limit: 10 });
  journal.record({ type: "event", event: "agent.delta", data: { delta: "one" } });
  journal.record({ type: "event", event: "item.completed", data: { item: { id: "two" } } });

  const replay = journal.replay({ instanceId: "bridge-a", afterSequence: 1 });
  assert.equal(replay.latestSequence, 2);
  assert.deepEqual(replay.events.map((event) => event.sequence), [2]);
});

test("does not replay a previous process instance and bounds retained history", () => {
  const journal = new EventJournal({ instanceId: "bridge-b", limit: 2 });
  journal.record({ type: "event", event: "agent.delta", data: { delta: "one" } });
  journal.record({ type: "event", event: "agent.delta", data: { delta: "two" } });
  journal.record({ type: "event", event: "agent.delta", data: { delta: "three" } });

  assert.deepEqual(journal.replay({ instanceId: "old", afterSequence: 0 }).events, []);
  const replay = journal.replay({ instanceId: "bridge-b", afterSequence: 0 });
  assert.equal(replay.truncated, true);
  assert.deepEqual(replay.events.map((event) => event.sequence), [2, 3]);
});

test("skips bulky tool deltas while advancing the replay cursor", () => {
  const journal = new EventJournal({ instanceId: "bridge-c" });
  journal.record({ type: "event", event: "tool.output", data: { delta: "large" } });
  const completed = journal.record({ type: "event", event: "item.completed", data: {} });

  const replay = journal.replay({ instanceId: "bridge-c", afterSequence: 0 });
  assert.equal(replay.latestSequence, 2);
  assert.deepEqual(replay.events, [completed]);
});
