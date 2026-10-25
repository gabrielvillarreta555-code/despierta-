#!/usr/bin/env bash

set -euo pipefail

apk_path="${1:?Debe indicarse el APK que se instalará}"
diagnostic_log="${2:?Debe indicarse el archivo de diagnóstico}"

mkdir -p "$(dirname "$diagnostic_log")"
: > "$diagnostic_log"

for attempt in $(seq 1 12); do
  echo "Intento $attempt: instalando $(basename "$apk_path")" | tee -a "$diagnostic_log"

  if install_output="$(adb install -r "$apk_path" 2>&1)"; then
    printf '%s\n' "$install_output" | tee -a "$diagnostic_log"
    if grep -q '^Success$' <<< "$install_output"; then
      exit 0
    fi
  else
    install_status=$?
    printf '%s\n' "$install_output" | tee -a "$diagnostic_log"
    echo "adb install terminó con código $install_status" | tee -a "$diagnostic_log"
  fi

  adb shell service check package >> "$diagnostic_log" 2>&1 || true
  adb shell cmd package list packages android >> "$diagnostic_log" 2>&1 || true
  adb shell dumpsys mount | head -80 >> "$diagnostic_log" 2>&1 || true
  sleep 10
done

echo "No fue posible instalar $(basename "$apk_path") después de 12 intentos" | tee -a "$diagnostic_log"
exit 1
