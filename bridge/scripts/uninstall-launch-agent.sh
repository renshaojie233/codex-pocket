#!/bin/zsh
set -euo pipefail

target_plist="$HOME/Library/LaunchAgents/com.codexpocket.bridge.plist"
target_env_plist="$HOME/Library/LaunchAgents/com.codexpocket.desktop-daemon-env.plist"
service="gui/$(id -u)/com.codexpocket.bridge"
env_service="gui/$(id -u)/com.codexpocket.desktop-daemon-env"

if launchctl print "$service" >/dev/null 2>&1; then
    launchctl bootout "$service" >/dev/null 2>&1 || true
fi
if launchctl print "$env_service" >/dev/null 2>&1; then
    launchctl bootout "$env_service" >/dev/null 2>&1 || true
fi

if [[ -f "$target_plist" ]]; then
    mv "$target_plist" "$target_plist.disabled"
fi
if [[ -f "$target_env_plist" ]]; then
    mv "$target_env_plist" "$target_env_plist.disabled"
fi

launchctl unsetenv CODEX_APP_SERVER_USE_LOCAL_DAEMON

echo "Codex Pocket Bridge stopped. The LaunchAgent plist was preserved as .disabled."
