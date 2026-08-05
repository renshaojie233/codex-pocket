import test from "node:test";
import assert from "node:assert/strict";
import { MutationReceipts } from "../src/mutation-receipts.mjs";

test("runs concurrent retries for the same client message exactly once", async () => {
  const receipts = new MutationReceipts();
  let executions = 0;
  const operation = async () => {
    executions += 1;
    await Promise.resolve();
    return { turnId: "turn-1" };
  };

  const [first, retry] = await Promise.all([
    receipts.run("turn.start:thread-1:client-1", operation),
    receipts.run("turn.start:thread-1:client-1", operation),
  ]);

  assert.equal(executions, 1);
  assert.deepEqual(first, { turnId: "turn-1" });
  assert.deepEqual(retry, first);
});

test("allows a failed mutation to be attempted again", async () => {
  const receipts = new MutationReceipts();
  let executions = 0;
  await assert.rejects(receipts.run("turn.steer:t:c", async () => {
    executions += 1;
    throw new Error("temporary failure");
  }));

  const result = await receipts.run("turn.steer:t:c", async () => {
    executions += 1;
    return { steered: true };
  });

  assert.equal(executions, 2);
  assert.deepEqual(result, { steered: true });
});
