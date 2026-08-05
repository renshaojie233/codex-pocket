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

  replay({ instanceId, afterSequence = 0, maxEvents = 250 } = {}) {
    const sameInstance = instanceId === this.instanceId;
    const earliestSequence = this.events[0]?.sequence ?? this.sequence + 1;
    const retainedEvents = sameInstance
      ? this.events.filter((event) => event.sequence > afterSequence)
      : [];
    const replayLimit = Math.min(Math.max(Number(maxEvents) || 250, 1), this.limit);
    const exceedsReplayLimit = retainedEvents.length > replayLimit;
    return {
      serverInstanceId: this.instanceId,
      latestSequence: this.sequence,
      truncated: sameInstance && (
        afterSequence < earliestSequence - 1 || exceedsReplayLimit
      ),
      // A current thread snapshot is both smaller and more accurate than a
      // very large delta replay. Return no partial window when the gap is too
      // large so cellular/DERP reconnects do not spend seconds downloading
      // stale streaming fragments before the app becomes usable.
      events: exceedsReplayLimit ? [] : retainedEvents,
    };
  }
}
