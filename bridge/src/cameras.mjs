import { execFile, spawn } from "node:child_process";
import { Transform } from "node:stream";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);
const SSH_OPTIONS = [
  "-T",
  "-o", "BatchMode=yes",
  "-o", "ConnectTimeout=6",
  "-o", "ServerAliveInterval=10",
  "-o", "ServerAliveCountMax=2",
  "-o", "RemoteCommand=none",
];
const REMOTE_STREAMER = "$HOME/.local/bin/codex-pocket-camera-stream.py";
const INVENTORY_MAX_AGE_MS = 20_000;

const genericDiscoverySource = String.raw`
import glob, importlib.util, json, os, re, sys

cameras = []
seen = set()
opencv = importlib.util.find_spec("cv2") is not None

def add_camera(path, label):
    try:
        resolved = os.path.realpath(path)
    except OSError:
        return
    if not re.fullmatch(r"/dev/video\d+", resolved) or resolved in seen:
        return
    seen.add(resolved)
    node = os.path.basename(resolved)
    try:
        index = open(f"/sys/class/video4linux/{node}/index", encoding="utf-8").read().strip()
    except OSError:
        index = ""
    if index and index != "0":
        return
    try:
        kernel_name = open(f"/sys/class/video4linux/{node}/name", encoding="utf-8").read().strip()
    except OSError:
        kernel_name = label
    cameras.append({
        "id": f"v4l-{node}",
        "name": label or kernel_name or node,
        "detail": kernel_name or resolved,
        "kind": "v4l",
        "source": resolved,
        "python": sys.executable,
        "available": opencv,
    })

for link in sorted(glob.glob("/dev/v4l/by-id/*-video-index0")):
    raw = os.path.basename(link).replace("-video-index0", "")
    raw = re.sub(r"^usb-", "", raw)
    raw = re.sub(r"_[0-9A-Fa-f]{4,}$", "", raw)
    label = re.sub(r"[_-]+", " ", raw).strip()
    add_camera(link, label)

if not cameras:
    for path in sorted(glob.glob("/dev/video*"), key=lambda value: int(re.search(r"\d+$", value).group())):
        add_camera(path, "")

print(json.dumps(cameras, ensure_ascii=False))
`;

const realSenseDiscoverySource = String.raw`
import json
import pyrealsense2 as rs

cameras = []
for index, device in enumerate(rs.context().query_devices(), 1):
    try:
        serial = device.get_info(rs.camera_info.serial_number)
        name = device.get_info(rs.camera_info.name)
    except Exception:
        continue
    cameras.append({
        "id": f"realsense-{serial}",
        "name": f"RealSense {index}",
        "detail": f"{name} · {serial}",
        "kind": "realsense",
        "source": serial,
        "python": "/home/ubuntu/anaconda3/bin/python",
        "available": True,
    })
print(json.dumps(cameras, ensure_ascii=False))
`;

function encodedPythonCommand(source, python = "/usr/bin/python3") {
  const payload = Buffer.from(source, "utf8").toString("base64");
  return `${python} -c "import base64;exec(base64.b64decode('${payload}'))"`;
}

async function sshOutput(alias, command, timeout = 8_000) {
  const { stdout } = await execFileAsync("/usr/bin/ssh", [...SSH_OPTIONS, alias, command], {
    encoding: "utf8",
    timeout,
    maxBuffer: 1024 * 1024,
  });
  return stdout.trim();
}

function safeJsonArray(value) {
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function publicCamera(camera) {
  return {
    id: camera.id,
    name: camera.name,
    detail: camera.detail,
    kind: camera.kind,
    available: camera.available !== false,
  };
}

export class MjpegMultipartTransform extends Transform {
  constructor(boundary = "codex-pocket-frame") {
    super();
    this.boundary = boundary;
    this.pending = Buffer.alloc(0);
  }

  _transform(chunk, _encoding, callback) {
    this.pending = Buffer.concat([this.pending, chunk]);
    while (true) {
      const start = this.pending.indexOf(Buffer.from([0xff, 0xd8]));
      if (start < 0) {
        if (this.pending.length > 2) this.pending = this.pending.subarray(this.pending.length - 2);
        break;
      }
      const end = this.pending.indexOf(Buffer.from([0xff, 0xd9]), start + 2);
      if (end < 0) {
        if (start > 0) this.pending = this.pending.subarray(start);
        if (this.pending.length > 12 * 1024 * 1024) this.pending = Buffer.alloc(0);
        break;
      }
      const frame = this.pending.subarray(start, end + 2);
      this.pending = this.pending.subarray(end + 2);
      this.push(Buffer.from(
        `--${this.boundary}\r\nContent-Type: image/jpeg\r\nContent-Length: ${frame.length}\r\n\r\n`,
      ));
      this.push(frame);
      this.push(Buffer.from("\r\n"));
    }
    callback();
  }

  _flush(callback) {
    this.push(Buffer.from(`--${this.boundary}--\r\n`));
    callback();
  }
}

export class CameraProvider {
  constructor(deviceStatusProvider) {
    this.deviceStatusProvider = deviceStatusProvider;
    this.inventoryValue = null;
    this.updatedAt = 0;
    this.refreshPromise = null;
    this.activeStreams = new Set();
  }

  async discoverDevice(device) {
    try {
      let cameras = safeJsonArray(await sshOutput(
        device.alias,
        encodedPythonCommand(
          genericDiscoverySource,
          device.alias === "workstation" ? "/home/ubuntu/anaconda3/bin/python" : "/usr/bin/python3",
        ),
      ));
      if (device.alias === "workstation") {
        try {
          const realSense = safeJsonArray(await sshOutput(
            device.alias,
            encodedPythonCommand(realSenseDiscoverySource, "/home/ubuntu/anaconda3/bin/python"),
          ));
          if (realSense.length) {
            cameras = [
              ...realSense,
              ...cameras.filter((camera) => !/realsense/i.test(`${camera.name} ${camera.detail}`)),
            ];
          }
        } catch {
          // Generic V4L cameras remain usable when librealsense is unavailable.
        }
      }
      return {
        id: device.alias,
        name: device.name,
        online: true,
        cameras: cameras.map(publicCamera),
        privateCameras: cameras,
        error: "",
      };
    } catch (error) {
      return {
        id: device.alias,
        name: device.name,
        online: true,
        cameras: [],
        privateCameras: [],
        error: error instanceof Error ? error.message.slice(0, 180) : String(error).slice(0, 180),
      };
    }
  }

  async refresh() {
    if (this.refreshPromise) return this.refreshPromise;
    this.refreshPromise = (async () => {
      const status = await this.deviceStatusProvider.snapshot();
      const online = status.devices.filter((device) => device.online === true);
      const discovered = await Promise.all(online.map((device) => this.discoverDevice(device)));
      const byId = new Map(discovered.map((device) => [device.id, device]));
      this.inventoryValue = {
        updated: new Date().toISOString(),
        devices: status.devices.map((device) => byId.get(device.alias) || {
          id: device.alias,
          name: device.name,
          online: false,
          cameras: [],
          privateCameras: [],
          error: device.error || "设备离线",
        }),
      };
      this.updatedAt = Date.now();
      return this.inventoryValue;
    })().finally(() => {
      this.refreshPromise = null;
    });
    return this.refreshPromise;
  }

  async inventory({ force = false, includePrivate = false } = {}) {
    if (force || !this.inventoryValue || Date.now() - this.updatedAt > INVENTORY_MAX_AGE_MS) {
      await this.refresh();
    }
    if (includePrivate) return this.inventoryValue;
    return {
      updated: this.inventoryValue.updated,
      devices: this.inventoryValue.devices.map(({ privateCameras: _privateCameras, ...device }) => device),
    };
  }

  async openStream(deviceId, cameraId) {
    const inventory = await this.inventory({ includePrivate: true });
    const device = inventory.devices.find((candidate) => candidate.id === deviceId && candidate.online);
    const camera = device?.privateCameras.find((candidate) => candidate.id === cameraId);
    if (!device || !camera) throw new Error("找不到这个摄像头，可能已经离线");
    if (!camera.available) throw new Error("目标设备缺少摄像头解码组件");

    const sourceValid = camera.kind === "realsense"
      ? /^\d{6,20}$/.test(camera.source)
      : /^\/dev\/video\d+$/.test(camera.source);
    const pythonValid = /^\/[A-Za-z0-9_./-]+$/.test(camera.python);
    if (!sourceValid || !pythonValid) throw new Error("摄像头参数无效");

    const sourceFlag = camera.kind === "realsense" ? "--realsense" : "--v4l";
    const command = `exec ${camera.python} "${REMOTE_STREAMER}" ${sourceFlag} ${camera.source} ` +
      "--width 1280 --height 720 --fps 20 --quality 80";
    const child = spawn("/usr/bin/ssh", [...SSH_OPTIONS, device.id, command], {
      stdio: ["ignore", "pipe", "pipe"],
    });
    this.activeStreams.add(child);
    child.once("exit", () => this.activeStreams.delete(child));
    child.once("error", () => this.activeStreams.delete(child));
    return { child, camera: publicCamera(camera), device: { id: device.id, name: device.name } };
  }

  stop() {
    for (const child of this.activeStreams) {
      if (!child.killed) child.kill("SIGTERM");
    }
    this.activeStreams.clear();
  }
}
