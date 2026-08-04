import { randomUUID } from "node:crypto";

export class EventJournal {
  constructor({ instanceId = randomUUID(), limit = 2_000 } = {}) {
    this.instanceId = instanceId;
    this.limit = limit;
    this.sequence = 0;
    this.events = [];
  }

  record(payload) {
    const event = {
      ...payload,
      sequence: ++this.sequence,
      serverInstanceId: this.instanceId,
    };
    // Command output deltas can be very large. Their completed item is still
    // journaled, so reconnecting clients recover the final command state.
    if (event.event !== "tool.output") {
      this.events.push(event);
      if (this.events.length > this.limit) this.events.splice(0, this.events.length - this.limit);
    }
    return event;
  }

  replay({ instanceId, afterSequence = 0 } = {}) {
    const sameInstance = instanceId === this.instanceId;
    const earliestSequence = this.events[0]?.sequence ?? this.sequence + 1;
    return {
      serverInstanceId: this.instanceId,
      latestSequence: this.sequence,
      truncated: sameInstance && afterSequence < earliestSequence - 1,
      events: sameInstance
        ? this.events.filter((event) => event.sequence > afterSequence)
        : [],
    };
  }
}
