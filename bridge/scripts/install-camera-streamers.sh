#!/bin/zsh
set -euo pipefail

script_dir=${0:A:h}
streamer="$script_dir/remote-camera-stream.py"
if [[ -n "${CODEX_POCKET_CAMERA_HOSTS:-}" ]]; then
  hosts=(${=CODEX_POCKET_CAMERA_HOSTS})
else
  hosts=(workstation agilex rsj-pc laptop)
fi

for host in "${hosts[@]}"; do
  echo "Installing camera streamer on $host"
  /usr/bin/scp -q -o BatchMode=yes -o ConnectTimeout=8 "$streamer" "$host:/tmp/codex-pocket-camera-stream.py"
  /usr/bin/ssh -T -o BatchMode=yes -o ConnectTimeout=8 -o RemoteCommand=none "$host" \
    'install -d -m 755 "$HOME/.local/bin" && install -m 755 /tmp/codex-pocket-camera-stream.py "$HOME/.local/bin/codex-pocket-camera-stream.py" && rm -f /tmp/codex-pocket-camera-stream.py'
done

echo "Camera streamers installed."
