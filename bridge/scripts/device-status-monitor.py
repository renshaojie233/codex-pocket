#!/opt/homebrew/bin/python3
"""Collect lightweight status from hosts configured in ~/.ssh/config."""

from __future__ import annotations

import base64
import concurrent.futures
import datetime as dt
import json
import os
import re
import signal
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from typing import Any


SSH_CONFIG = Path.home() / ".ssh" / "config"
REFRESH_SECONDS = 10
HIDDEN_ALIASES = {"tianyi-batchcom", "corl-experiment"}
DISPLAY_NAMES = {
    "workstation": ("Workstation", "电脑"),
    "agilex": ("AgileX", "机器人电脑"),
    "orin": ("Orin", "机器人电脑"),
    "rsj-pc": ("RSG PC", "电脑"),
    "laptop": ("RSG Table", "笔记本"),
    "tianyi-batchcom": ("天翼服务器 1", "服务器"),
    "tianyi-batchcom-2": ("RSJ", "服务器"),
    "tianyi-batchcom-3": ("WC", "服务器"),
    "congwang": ("WC", "服务器"),
    "pytu1": ("PYTU1", "服务器"),
    "corl-experiment": ("CORL Server", "服务器"),
    "aliyun-codex-relay": ("阿里云中继", "服务器"),
}

REMOTE_SCRIPT = r"""
export LC_ALL=C

encode() {
    printf '%s' "$1" | base64 | tr -d '\n'
}

printf 'HOSTNAME|%s\n' "$(hostname 2>/dev/null || echo unknown)"
if [ -r /etc/os-release ]; then
    os_name=$(sed -n 's/^PRETTY_NAME=//p' /etc/os-release | head -n 1 | sed 's/^"//;s/"$//')
    printf 'OS|%s\n' "$(encode "$os_name")"
fi

uptime_seconds=$(cut -d. -f1 /proc/uptime 2>/dev/null || echo 0)
load_line=$(cat /proc/loadavg 2>/dev/null || echo '0 0 0')
cpu_count=$(getconf _NPROCESSORS_ONLN 2>/dev/null || nproc 2>/dev/null || echo 0)
printf 'UPTIME|%s\n' "$uptime_seconds"
printf 'LOAD|%s|%s\n' "$cpu_count" "$load_line"

cpu_percent=$(top -bn1 2>/dev/null | awk '
    /Cpu\(s\)|%Cpu/ {
        for (i=1; i<=NF; i++) {
            if ($i ~ /^id,?$/ && i > 1) {
                idle=$(i-1); gsub(/,/, "", idle); printf "%.1f", 100-idle; exit
            }
        }
    }')
[ -n "$cpu_percent" ] || cpu_percent=0
printf 'CPU|%s\n' "$cpu_percent"

free -b 2>/dev/null | awk '/^Mem:/ {printf "MEM|%s|%s\n", $2, $3}'
df -B1 -P / 2>/dev/null | awk 'NR==2 {printf "DISK|%s|%s\n", $2, $3}'

ps -eo pid=,user=,pcpu=,pmem=,etime=,comm= --sort=-pcpu 2>/dev/null \
    | head -n 12 \
    | while IFS= read -r process; do
        [ -n "$process" ] && printf 'PROC|%s\n' "$(encode "$process")"
      done

if command -v nvidia-smi >/dev/null 2>&1; then
    nvidia-smi \
        --query-gpu=index,name,utilization.gpu,memory.used,memory.total,temperature.gpu,power.draw \
        --format=csv,noheader,nounits 2>/dev/null \
        | while IFS= read -r gpu; do
            [ -n "$gpu" ] && printf 'GPU|%s\n' "$(encode "$gpu")"
          done
    nvidia-smi \
        --query-compute-apps=pid,process_name,used_memory \
        --format=csv,noheader,nounits 2>/dev/null \
        | while IFS= read -r gpu_process; do
            [ -n "$gpu_process" ] && printf 'GPUPROC|%s\n' "$(encode "$gpu_process")"
          done
elif command -v tegrastats >/dev/null 2>&1; then
    jetson=$(timeout 2 tegrastats --interval 100 --count 1 2>/dev/null | tail -n 1)
    [ -n "$jetson" ] && printf 'JETSON|%s\n' "$(encode "$jetson")"
fi
"""


def decode_text(value: str) -> str:
    try:
        return base64.b64decode(value).decode("utf-8", errors="replace")
    except Exception:
        return ""


def parse_aliases() -> list[str]:
    if not SSH_CONFIG.exists():
        return []
    aliases: list[str] = []
    for raw in SSH_CONFIG.read_text(encoding="utf-8", errors="replace").splitlines():
        match = re.match(r"^\s*Host\s+(.+?)\s*$", raw, re.IGNORECASE)
        if not match:
            continue
        candidates = match.group(1).split()
        candidate = next(
            (
                value
                for value in candidates
                if "*" not in value
                and "?" not in value
                and not value.endswith("-bash")
                and "-bash-" not in value
            ),
            None,
        )
        if candidate and candidate not in aliases:
            aliases.append(candidate)
    aliases = [alias for alias in aliases if alias not in HIDDEN_ALIASES]
    preferred = {alias: index for index, alias in enumerate(DISPLAY_NAMES)}
    return sorted(aliases, key=lambda alias: (preferred.get(alias, len(preferred)), aliases.index(alias)))


def resolved_target(alias: str) -> dict[str, str]:
    result = subprocess.run(
        ["/usr/bin/ssh", "-G", alias],
        capture_output=True,
        text=True,
        timeout=3,
        check=False,
    )
    values: dict[str, str] = {}
    for line in result.stdout.splitlines():
        key, _, value = line.partition(" ")
        if key in {"hostname", "user", "port"}:
            values[key] = value.strip()
    return values


def deduplicate_aliases(aliases: list[str]) -> list[str]:
    unique: list[str] = []
    seen: set[tuple[str, str, str]] = set()
    for alias in aliases:
        target = resolved_target(alias)
        key = (
            target.get("hostname", alias),
            target.get("user", ""),
            target.get("port", "22"),
        )
        if key in seen:
            continue
        seen.add(key)
        unique.append(alias)
    return unique


def parse_process(raw: str) -> dict[str, Any] | None:
    parts = raw.strip().split(None, 5)
    if len(parts) < 6:
        return None
    pid, user, cpu, memory, elapsed, command = parts
    try:
        cpu_value = float(cpu)
    except ValueError:
        cpu_value = 0.0
    try:
        memory_value = float(memory)
    except ValueError:
        memory_value = 0.0
    return {
        "pid": pid,
        "user": user,
        "cpu": cpu_value,
        "memory": memory_value,
        "elapsed": elapsed,
        "command": command,
    }


def error_summary(stderr: str, return_code: int) -> str:
    text = stderr.strip().splitlines()
    message = text[-1] if text else f"SSH 退出码 {return_code}"
    replacements = {
        "Operation timed out": "连接超时",
        "Connection timed out": "连接超时",
        "No route to host": "无法到达设备",
        "Connection refused": "SSH 服务未响应",
        "Permission denied": "SSH 身份验证失败",
        "Could not resolve hostname": "无法解析地址",
    }
    for needle, summary in replacements.items():
        if needle.lower() in message.lower():
            return summary
    return message[:160]


def collect(alias: str) -> dict[str, Any]:
    started = time.monotonic()
    target = resolved_target(alias)
    display_name, category = DISPLAY_NAMES.get(alias, (alias, "SSH 设备"))
    item: dict[str, Any] = {
        "alias": alias,
        "name": display_name,
        "category": category,
        "address": target.get("hostname", ""),
        "user": target.get("user", ""),
        "port": target.get("port", "22"),
        "online": False,
        "latency_ms": 0,
        "hostname": "",
        "os": "",
        "uptime_seconds": 0,
        "cpu_percent": 0.0,
        "cpu_count": 0,
        "load": [0.0, 0.0, 0.0],
        "memory_total": 0,
        "memory_used": 0,
        "disk_total": 0,
        "disk_used": 0,
        "gpus": [],
        "gpu_processes": [],
        "processes": [],
        "jetson": "",
        "error": "",
    }
    command = [
        "/usr/bin/ssh",
        "-T",
        "-o",
        "BatchMode=yes",
        "-o",
        "ConnectTimeout=5",
        "-o",
        "ConnectionAttempts=1",
        "-o",
        "RemoteCommand=none",
        "-o",
        "RequestTTY=no",
        alias,
        "bash -s",
    ]
    try:
        result = subprocess.run(
            command,
            input=REMOTE_SCRIPT,
            capture_output=True,
            text=True,
            timeout=8,
            check=False,
        )
    except subprocess.TimeoutExpired:
        item["error"] = "连接超时"
        return item
    except Exception as exc:
        item["error"] = f"{type(exc).__name__}: {exc}"
        return item

    item["latency_ms"] = int((time.monotonic() - started) * 1000)
    if result.returncode != 0:
        item["error"] = error_summary(result.stderr or result.stdout, result.returncode)
        return item

    item["online"] = True
    for line in result.stdout.splitlines():
        kind, separator, value = line.partition("|")
        if not separator:
            continue
        values = value.split("|")
        try:
            if kind == "HOSTNAME":
                item["hostname"] = value.strip()
            elif kind == "OS":
                item["os"] = decode_text(value)
            elif kind == "UPTIME":
                item["uptime_seconds"] = int(float(value))
            elif kind == "LOAD" and len(values) >= 2:
                item["cpu_count"] = int(values[0])
                loads = values[1].split()[:3]
                item["load"] = [float(load) for load in loads]
            elif kind == "CPU":
                item["cpu_percent"] = max(0.0, min(100.0, float(value)))
            elif kind == "MEM" and len(values) >= 2:
                item["memory_total"] = int(values[0])
                item["memory_used"] = int(values[1])
            elif kind == "DISK" and len(values) >= 2:
                item["disk_total"] = int(values[0])
                item["disk_used"] = int(values[1])
            elif kind == "PROC":
                process = parse_process(decode_text(value))
                if process:
                    item["processes"].append(process)
            elif kind == "GPU":
                gpu_values = [part.strip() for part in decode_text(value).split(",")]
                if len(gpu_values) >= 7:
                    item["gpus"].append(
                        {
                            "index": gpu_values[0],
                            "name": gpu_values[1],
                            "utilization": float(gpu_values[2] or 0),
                            "memory_used_mb": float(gpu_values[3] or 0),
                            "memory_total_mb": float(gpu_values[4] or 0),
                            "temperature": float(gpu_values[5] or 0),
                            "power_watts": float(gpu_values[6] or 0),
                        }
                    )
            elif kind == "GPUPROC":
                process_values = [part.strip() for part in decode_text(value).split(",", 2)]
                if len(process_values) == 3:
                    item["gpu_processes"].append(
                        {
                            "pid": process_values[0],
                            "command": process_values[1],
                            "memory_mb": float(process_values[2] or 0),
                        }
                    )
            elif kind == "JETSON":
                item["jetson"] = decode_text(value)
        except (TypeError, ValueError):
            continue
    return item


def write_status(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
            json.dump(payload, handle, ensure_ascii=False)
        os.replace(temporary_name, path)
    finally:
        if os.path.exists(temporary_name):
            os.unlink(temporary_name)


def collect_all(aliases: list[str]) -> dict[str, Any]:
    with concurrent.futures.ThreadPoolExecutor(max_workers=min(12, max(1, len(aliases)))) as pool:
        future_by_alias = {pool.submit(collect, alias): alias for alias in aliases}
        collected = {future_by_alias[future]: future.result() for future in future_by_alias}
    devices = [collected[alias] for alias in aliases]
    return {
        "updated": dt.datetime.now().astimezone().isoformat(timespec="seconds"),
        "refresh_seconds": REFRESH_SECONDS,
        "devices": devices,
    }


def main() -> int:
    if len(sys.argv) not in {2, 3}:
        print(f"usage: {Path(sys.argv[0]).name} STATUS_JSON [--once]", file=sys.stderr)
        return 2
    status_path = Path(sys.argv[1]).expanduser()
    once = len(sys.argv) == 3 and sys.argv[2] == "--once"
    aliases = deduplicate_aliases(parse_aliases())
    running = True

    def stop(*_args: object) -> None:
        nonlocal running
        running = False

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    while running:
        write_status(status_path, collect_all(aliases))
        if once:
            break
        for _ in range(REFRESH_SECONDS * 2):
            if not running:
                break
            time.sleep(0.5)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
