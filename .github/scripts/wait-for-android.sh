#!/usr/bin/env bash

set -euo pipefail

readiness_log="${1:?Debe indicarse el archivo de diagnóstico}"
mkdir -p "$(dirname "$readiness_log")"
echo "Esperando a que Android termine de iniciar" | tee "$readiness_log"

timeout 120 adb wait-for-device
android_ready=false
stable_checks=0

for attempt in $(seq 1 120); do
  boot_completed="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  package_service="$(adb shell service check package 2>/dev/null | tr -d '\r' || true)"
  activity_service="$(adb shell service check activity 2>/dev/null | tr -d '\r' || true)"
  framework_path="$(adb shell pm path android 2>/dev/null | tr -d '\r' || true)"
  settings_value="$(adb shell settings get global device_provisioned 2>&1 | tr -d '\r' || true)"
  install_location="$(adb shell pm get-install-location 2>&1 | tr -d '\r' || true)"
  package_query="$(adb shell cmd package list packages android 2>&1 | tr -d '\r' || true)"

  package_ready=false
  activity_ready=false
  framework_ready=false
  settings_ready=false
  install_location_ready=false
  package_query_ready=false
  case "$package_service" in *found*) package_ready=true ;; esac
  case "$activity_service" in *found*) activity_ready=true ;; esac
  case "$framework_path" in package:*) framework_ready=true ;; esac
  case "$settings_value" in 0|1|null) settings_ready=true ;; esac
  case "$install_location" in *auto*|*internal*|*external*) install_location_ready=true ;; esac
  case "$package_query" in *package:android*) package_query_ready=true ;; esac

  if [[ "$boot_completed" == "1" &&
        "$package_ready" == "true" &&
        "$activity_ready" == "true" &&
        "$framework_ready" == "true" &&
        "$settings_ready" == "true" &&
        "$install_location_ready" == "true" &&
        "$package_query_ready" == "true" ]]; then
    stable_checks=$((stable_checks + 1))
    if (( stable_checks >= 3 )); then
      android_ready=true
      echo "Android disponible tras $attempt intentos y 3 comprobaciones estables" | tee -a "$readiness_log"
      break
    fi
  else
    stable_checks=0
  fi

  if (( attempt % 10 == 0 )); then
    echo "Intento $attempt: boot=$boot_completed; package=$package_service; activity=$activity_service; settings=$settings_value; install=$install_location" | tee -a "$readiness_log"
  fi
  sleep 2
done

if [[ "$android_ready" != "true" ]]; then
  echo "Android no habilitó PackageManager, ActivityManager y SettingsProvider dentro del plazo" | tee -a "$readiness_log"
  adb shell getprop >> "$readiness_log" 2>&1 || true
  adb shell service list >> "$readiness_log" 2>&1 || true
  exit 1
fi
