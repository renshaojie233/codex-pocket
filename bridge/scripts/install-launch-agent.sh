#!/bin/zsh
set -euo pipefail

script_dir="${0:A:h}"
bridge_dir="${script_dir:h}"
source_plist="$bridge_dir/launchd/com.codexpocket.bridge.plist"
source_env_plist="$bridge_dir/launchd/com.codexpocket.desktop-daemon-env.plist"
target_dir="$HOME/Library/LaunchAgents"
target_plist="$target_dir/com.codexpocket.bridge.plist"
target_env_plist="$target_dir/com.codexpocket.desktop-daemon-env.plist"
service="gui/$(id -u)/com.codexpocket.bridge"
env_service="gui/$(id -u)/com.codexpocket.desktop-daemon-env"
node_bin="$(command -v node || true)"

if [[ -z "$node_bin" || ! -x "$node_bin" ]]; then
    echo "Node.js 20 or newer is required before installing the Bridge." >&2
    exit 1
fi

bootstrap_or_loaded() {
    local plist_path="$1"
    local service_name="$2"
    local attempt
    for attempt in {1..8}; do
        if launchctl bootstrap "gui/$(id -u)" "$plist_path" 2>/dev/null; then
            return 0
        fi
        if launchctl print "$service_name" >/dev/null 2>&1; then
            return 0
        fi
        /bin/sleep 0.5
    done
    echo "Unable to load LaunchAgent: $service_name" >&2
    return 1
}

mkdir -p "$target_dir"
mkdir -p "$bridge_dir/data"
cp "$source_plist" "$target_plist"
cp "$source_env_plist" "$target_env_plist"
/usr/bin/plutil -remove ProgramArguments.1 "$target_plist"
/usr/bin/plutil -remove ProgramArguments.0 "$target_plist"
/usr/bin/plutil -insert ProgramArguments.0 -string "$node_bin" "$target_plist"
/usr/bin/plutil -insert ProgramArguments.1 -string "$bridge_dir/src/server.mjs" "$target_plist"
/usr/bin/plutil -replace WorkingDirectory -string "$bridge_dir" "$target_plist"
/usr/bin/plutil -replace EnvironmentVariables.PATH -string \
    "${node_bin:h}:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin" "$target_plist"
/usr/bin/plutil -replace StandardOutPath -string "$bridge_dir/data/bridge.log" "$target_plist"
/usr/bin/plutil -replace StandardErrorPath -string "$bridge_dir/data/bridge-error.log" "$target_plist"
/usr/bin/plutil -replace StandardOutPath -string "$bridge_dir/data/desktop-start.log" "$target_env_plist"
/usr/bin/plutil -replace StandardErrorPath -string "$bridge_dir/data/desktop-start-error.log" "$target_env_plist"
chmod 600 "$target_plist"
chmod 600 "$target_env_plist"

if launchctl print "$service" >/dev/null 2>&1; then
    # macOS can report an I/O error even after it has removed the job.
    launchctl bootout "$service" >/dev/null 2>&1 || true
fi
if launchctl print "$env_service" >/dev/null 2>&1; then
    launchctl bootout "$env_service" >/dev/null 2>&1 || true
fi

bootstrap_or_loaded "$target_env_plist" "$env_service"
bootstrap_or_loaded "$target_plist" "$service"
launchctl enable "$service"
launchctl kickstart -k "$service"

echo "Codex Pocket Bridge installed and started."
echo "Logs: $bridge_dir/data/bridge.log"
