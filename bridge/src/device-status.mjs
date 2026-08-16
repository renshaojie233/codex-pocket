import { execFile } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);
const moduleDir = dirname(fileURLToPath(import.meta.url));

export class DeviceStatusProvider {
  constructor({
    scriptPath = resolve(moduleDir, "..", "scripts", "device-status-monitor.py"),
    statusPath = resolve(moduleDir, "..", "data", "device-status.json"),
    maxAgeMs = 10_000,
  } = {}) {
    this.scriptPath = scriptPath;
    this.statusPath = statusPath;
    this.maxAgeMs = maxAgeMs;
    this.snapshotValue = this.readSaved();
    this.updatedAt = this.snapshotValue ? Date.now() : 0;
    this.refreshPromise = null;
  }

  readSaved() {
    if (!existsSync(this.statusPath)) return null;
    try {
      const parsed = JSON.parse(readFileSync(this.statusPath, "utf8"));
      return Array.isArray(parsed?.devices) ? parsed : null;
    } catch {
      return null;
    }
  }

  async refresh() {
    if (this.refreshPromise) return this.refreshPromise;
    this.refreshPromise = execFileAsync(this.scriptPath, [this.statusPath, "--once"], {
      encoding: "utf8",
      timeout: 18_000,
      maxBuffer: 1024 * 1024,
    })
      .then(() => {
        const snapshot = this.readSaved();
        if (!snapshot) throw new Error("设备监控没有返回有效数据");
        this.snapshotValue = snapshot;
        this.updatedAt = Date.now();
        return snapshot;
      })
      .finally(() => {
        this.refreshPromise = null;
      });
    return this.refreshPromise;
  }

  async snapshot({ force = false } = {}) {
    const stale = Date.now() - this.updatedAt >= this.maxAgeMs;
    if (force || !this.snapshotValue) return this.refresh();
    if (stale && !this.refreshPromise) {
      this.refresh().catch((error) => console.error(`[device-status] ${error.message}`));
    }
    return this.snapshotValue;
  }
}
