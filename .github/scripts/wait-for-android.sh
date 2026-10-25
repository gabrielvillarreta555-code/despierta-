#!/usr/bin/env bash

set -euo pipefail

readiness_log="${1:?Debe indicarse el archivo de diagnóstico}"
mkdir -p "$(dirname "$readiness_log")"
echo "Esperando a que Android termine de iniciar" | tee "$readiness_log"

timeout 120 adb wait-for-device
android_ready=false

for attempt in $(seq 1 120); do
  boot_completed="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  package_service="$(adb shell service check package 2>/dev/null | tr -d '\r' || true)"
  activity_service="$(adb shell service check activity 2>/dev/null | tr -d '\r' || true)"
  framework_path="$(adb shell pm path android 2>/dev/null | tr -d '\r' || true)"

  package_ready=false
  activity_ready=false
  framework_ready=false
  case "$package_service" in *found*) package_ready=true ;; esac
  case "$activity_service" in *found*) activity_ready=true ;; esac
  case "$framework_path" in package:*) framework_ready=true ;; esac

  if [[ "$boot_completed" == "1" &&
        "$package_ready" == "true" &&
        "$activity_ready" == "true" &&
        "$framework_ready" == "true" ]]; then
    android_ready=true
    echo "Android disponible tras $attempt intentos" | tee -a "$readiness_log"
    break
  fi

  if (( attempt % 10 == 0 )); then
    echo "Intento $attempt: boot=$boot_completed; package=$package_service; activity=$activity_service" | tee -a "$readiness_log"
  fi
  sleep 2
done

if [[ "$android_ready" != "true" ]]; then
  echo "Android no habilitó PackageManager y ActivityManager dentro del plazo" | tee -a "$readiness_log"
  adb shell getprop >> "$readiness_log" 2>&1 || true
  adb shell service list >> "$readiness_log" 2>&1 || true
  exit 1
fi
