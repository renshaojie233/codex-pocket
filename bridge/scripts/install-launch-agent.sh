#!/bin/zsh
set -euo pipefail

script_dir="${0:A:h}"
bridge_dir="${script_dir:h}"
source_plist="$bridge_dir/launchd/com.codexpocket.bridge.plist"
source_env_plist="$bridge_dir/launchd/com.codexpocket.desktop-daemon-env.plist"
source_repair_plist="$bridge_dir/launchd/com.codexpocket.tailscale-link-repair.plist"
target_dir="$HOME/Library/LaunchAgents"
target_plist="$target_dir/com.codexpocket.bridge.plist"
target_env_plist="$target_dir/com.codexpocket.desktop-daemon-env.plist"
target_repair_plist="$target_dir/com.codexpocket.tailscale-link-repair.plist"
service="gui/$(id -u)/com.codexpocket.bridge"
env_service="gui/$(id -u)/com.codexpocket.desktop-daemon-env"
repair_service="gui/$(id -u)/com.codexpocket.tailscale-link-repair"
node_bin="$(command -v node || true)"
repair_peer="${CODEX_POCKET_TAILSCALE_PEER:-}"

if [[ -z "$repair_peer" && -f "$target_repair_plist" ]]; then
    repair_peer="$(/usr/bin/plutil -extract EnvironmentVariables.CODEX_POCKET_TAILSCALE_PEER raw -o - "$target_repair_plist" 2>/dev/null || true)"
fi

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

bootout_and_wait() {
    local service_name="$1"
    local attempt
    if ! launchctl print "$service_name" >/dev/null 2>&1; then
        return 0
    fi
    # bootout can return before launchd has completely removed the old job.
    launchctl bootout "$service_name" >/dev/null 2>&1 || true
    for attempt in {1..20}; do
        if ! launchctl print "$service_name" >/dev/null 2>&1; then
            return 0
        fi
        /bin/sleep 0.25
    done
    echo "Unable to unload LaunchAgent: $service_name" >&2
    return 1
}

mkdir -p "$target_dir"
mkdir -p "$bridge_dir/data"
cp "$source_plist" "$target_plist"
cp "$source_env_plist" "$target_env_plist"
if [[ -n "$repair_peer" ]]; then
    cp "$source_repair_plist" "$target_repair_plist"
fi
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
if [[ -n "$repair_peer" ]]; then
    /usr/bin/plutil -remove ProgramArguments.1 "$target_repair_plist"
    /usr/bin/plutil -remove ProgramArguments.0 "$target_repair_plist"
    /usr/bin/plutil -insert ProgramArguments.0 -string "$node_bin" "$target_repair_plist"
    /usr/bin/plutil -insert ProgramArguments.1 -string "$bridge_dir/scripts/repair-tailscale-link.mjs" "$target_repair_plist"
    /usr/bin/plutil -replace WorkingDirectory -string "$bridge_dir" "$target_repair_plist"
    /usr/bin/plutil -replace EnvironmentVariables.PATH -string \
        "${node_bin:h}:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin" "$target_repair_plist"
    /usr/bin/plutil -replace EnvironmentVariables.CODEX_POCKET_TAILSCALE_PEER -string "$repair_peer" "$target_repair_plist"
    /usr/bin/plutil -replace StandardOutPath -string "$bridge_dir/data/tailscale-link-repair.log" "$target_repair_plist"
    /usr/bin/plutil -replace StandardErrorPath -string "$bridge_dir/data/tailscale-link-repair-error.log" "$target_repair_plist"
fi
chmod 600 "$target_plist"
chmod 600 "$target_env_plist"
if [[ -n "$repair_peer" ]]; then
    chmod 600 "$target_repair_plist"
fi

bootout_and_wait "$service"
bootout_and_wait "$env_service"
bootout_and_wait "$repair_service"

bootstrap_or_loaded "$target_env_plist" "$env_service"
bootstrap_or_loaded "$target_plist" "$service"
if [[ -n "$repair_peer" ]]; then
    bootstrap_or_loaded "$target_repair_plist" "$repair_service"
fi
launchctl enable "$service"
launchctl kickstart -k "$service"

echo "Codex Pocket Bridge installed and started."
echo "Logs: $bridge_dir/data/bridge.log"
if [[ -n "$repair_peer" ]]; then
    echo "Tailscale direct-link repair enabled for: $repair_peer"
    echo "Link repair logs: $bridge_dir/data/tailscale-link-repair.log"
else
    echo "Tailscale direct-link repair is optional; set CODEX_POCKET_TAILSCALE_PEER when installing to enable it."
fi
