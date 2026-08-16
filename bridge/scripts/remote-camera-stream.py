#!/usr/bin/env python3
"""Emit a selected Linux camera as a low-latency sequence of JPEG frames."""

from __future__ import annotations

import argparse
import fcntl
import hashlib
import os
from pathlib import Path
import signal
import subprocess
import sys


def camera_nodes_for_usb_root(physical_port: str) -> list[Path]:
    port = Path(physical_port)
    usb_root = next(
        (
            parent
            for parent in [port, *port.parents]
            if parent.name and parent.name[0].isdigit() and "-" in parent.name
        ),
        None,
    )
    if usb_root is None:
        return []
    nodes: list[Path] = []
    for entry in Path("/sys/class/video4linux").glob("video*"):
        try:
            device_path = (entry / "device").resolve()
        except OSError:
            continue
        if device_path == usb_root or usb_root in device_path.parents:
            nodes.append(Path("/dev") / entry.name)
    return sorted(nodes, key=lambda node: int(node.name.removeprefix("video")))


def camera_is_busy(nodes: list[Path]) -> bool:
    if not nodes:
        return False
    result = subprocess.run(
        ["fuser", *map(str, nodes)],
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        check=False,
    )
    return bool(result.stdout.strip())


def lock_camera(source: str):
    lock_name = hashlib.sha256(source.encode("utf-8")).hexdigest()[:20]
    handle = open(f"/tmp/codex-pocket-camera-{lock_name}.lock", "w")
    try:
        fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError as error:
        handle.close()
        raise RuntimeError("摄像头已经在另一处观看") from error
    return handle


def encode_and_write(image, quality: int) -> bool:
    import cv2

    ok, encoded = cv2.imencode(
        ".jpg",
        image,
        [cv2.IMWRITE_JPEG_QUALITY, quality],
    )
    if not ok:
        return True
    try:
        sys.stdout.buffer.write(encoded.tobytes())
        sys.stdout.buffer.flush()
        return True
    except (BrokenPipeError, OSError):
        return False


def stream_v4l(args: argparse.Namespace, running) -> int:
    import cv2

    device = Path(args.v4l)
    if not device.exists() or not device.name.startswith("video"):
        print("摄像头设备不存在", file=sys.stderr)
        return 3
    if camera_is_busy([device]):
        print("摄像头正在被其他程序使用", file=sys.stderr)
        return 4

    capture = cv2.VideoCapture(str(device), cv2.CAP_V4L2)
    if not capture.isOpened():
        print("无法打开摄像头", file=sys.stderr)
        return 5
    capture.set(cv2.CAP_PROP_FOURCC, cv2.VideoWriter_fourcc(*"MJPG"))
    capture.set(cv2.CAP_PROP_FRAME_WIDTH, args.width)
    capture.set(cv2.CAP_PROP_FRAME_HEIGHT, args.height)
    capture.set(cv2.CAP_PROP_FPS, args.fps)
    capture.set(cv2.CAP_PROP_BUFFERSIZE, 1)
    try:
        while running():
            ok, image = capture.read()
            if not ok:
                continue
            if not encode_and_write(image, args.quality):
                break
    finally:
        capture.release()
    return 0


def stream_realsense(args: argparse.Namespace, running) -> int:
    import numpy as np
    import pyrealsense2 as rs

    devices = list(rs.context().query_devices())
    target = next(
        (
            device
            for device in devices
            if device.get_info(rs.camera_info.serial_number) == args.realsense
        ),
        None,
    )
    if target is None:
        print("RealSense 摄像头当前未连接", file=sys.stderr)
        return 3
    nodes = camera_nodes_for_usb_root(target.get_info(rs.camera_info.physical_port))
    if camera_is_busy(nodes):
        print("RealSense 摄像头正在被采集任务使用", file=sys.stderr)
        return 4

    pipeline = rs.pipeline()
    config = rs.config()
    config.enable_device(args.realsense)
    color_profiles = []
    for sensor in target.query_sensors():
        for profile in sensor.get_stream_profiles():
            try:
                video = profile.as_video_stream_profile()
            except RuntimeError:
                continue
            if video.stream_type() == rs.stream.color and video.format() == rs.format.bgr8:
                color_profiles.append(video)
    requested_size = [
        profile
        for profile in color_profiles
        if profile.width() == args.width and profile.height() == args.height
    ]
    candidates = requested_size or color_profiles
    if not candidates:
        print("RealSense 没有可用的彩色画面配置", file=sys.stderr)
        return 5
    selected = min(
        candidates,
        key=lambda profile: (
            abs(profile.fps() - args.fps),
            -profile.fps(),
            abs(profile.width() * profile.height() - args.width * args.height),
        ),
    )
    config.enable_stream(
        rs.stream.color,
        selected.width(),
        selected.height(),
        rs.format.bgr8,
        selected.fps(),
    )
    started = False
    try:
        pipeline.start(config)
        started = True
        for _ in range(min(args.fps, 20)):
            if not running():
                return 0
            pipeline.wait_for_frames(5000)
        while running():
            frames = pipeline.wait_for_frames(5000)
            color = frames.get_color_frame()
            if not color:
                continue
            image = np.asanyarray(color.get_data())
            if not encode_and_write(image, args.quality):
                break
    except RuntimeError as error:
        print(f"RealSense 启动失败：{error}", file=sys.stderr)
        return 5
    finally:
        if started:
            pipeline.stop()
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--v4l")
    source.add_argument("--realsense")
    parser.add_argument("--width", type=int, default=1280)
    parser.add_argument("--height", type=int, default=720)
    parser.add_argument("--fps", type=int, default=20)
    parser.add_argument("--quality", type=int, default=80)
    args = parser.parse_args()
    args.width = max(320, min(args.width, 1920))
    args.height = max(180, min(args.height, 1080))
    args.fps = max(5, min(args.fps, 30))
    args.quality = max(45, min(args.quality, 92))

    source_id = args.v4l or f"realsense:{args.realsense}"
    try:
        lock = lock_camera(source_id)
    except RuntimeError as error:
        print(error, file=sys.stderr)
        return 2

    active = True

    def stop(_signum, _frame):
        nonlocal active
        active = False

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    try:
        if args.v4l:
            return stream_v4l(args, lambda: active)
        return stream_realsense(args, lambda: active)
    finally:
        lock.close()


if __name__ == "__main__":
    raise SystemExit(main())
