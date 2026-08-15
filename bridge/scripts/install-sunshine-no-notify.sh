#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
library_dir="$HOME/.local/lib/codex-pocket"
dropin_dir="$HOME/.config/systemd/user/codex-pocket-sunshine.service.d"

install -d "$library_dir" "$dropin_dir"
cc -O2 -fPIC -shared \
  -o "$library_dir/libsunshine-no-notify.so" \
  "$repo_root/bridge/systemd/sunshine-no-notify.c"
install -m 0644 \
  "$repo_root/bridge/systemd/codex-pocket-sunshine-no-notify.conf" \
  "$dropin_dir/no-notify.conf"

systemctl --user daemon-reload
systemctl --user restart codex-pocket-sunshine.service

pid="$(systemctl --user show codex-pocket-sunshine.service -p MainPID --value)"
if [[ -z "$pid" || "$pid" == "0" ]]; then
  echo "Sunshine did not restart" >&2
  exit 1
fi

if ! grep -q 'libsunshine-no-notify.so' "/proc/$pid/maps"; then
  echo "Notification suppression library is not loaded" >&2
  exit 1
fi

echo "Sunshine desktop notifications disabled (PID $pid)"
