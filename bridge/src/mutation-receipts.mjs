export class MutationReceipts {
  constructor({ limit = 2_000, ttlMillis = 24 * 60 * 60 * 1_000, now = Date.now } = {}) {
    this.limit = limit;
    this.ttlMillis = ttlMillis;
    this.now = now;
    this.entries = new Map();
  }

  run(key, operation) {
    if (!key) return Promise.resolve().then(operation);
    const now = this.now();
    this.#prune(now);
    const existing = this.entries.get(key);
    if (existing) return existing.promise;

    const entry = { createdAt: now, settled: false, promise: null };
    entry.promise = Promise.resolve().then(operation);
    this.entries.set(key, entry);
    entry.promise.then(
      () => { entry.settled = true; },
      () => {
        entry.settled = true;
        if (this.entries.get(key) === entry) this.entries.delete(key);
      },
    );
    return entry.promise;
  }

  #prune(now) {
    for (const [key, entry] of this.entries) {
      if (entry.settled && now - entry.createdAt > this.ttlMillis) this.entries.delete(key);
    }
    if (this.entries.size < this.limit) return;
    for (const [key, entry] of this.entries) {
      if (!entry.settled) continue;
      this.entries.delete(key);
      if (this.entries.size < this.limit) break;
    }
  }
}
