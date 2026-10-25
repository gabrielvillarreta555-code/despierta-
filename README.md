# Despierta

Aplicación Android de alarmas. Conserva el identificador `com.example.despierta`,
admite alarmas únicas o semanales, sonido personalizado con alternativa del
sistema, vibración, posponer, detener y desafío matemático opcional.

## Compatibilidad y permisos

- `minSdk 26` (Android 8) y `targetSdk 36` (Android 16).
- Android 13–16: `USE_EXACT_ALARM`, apropiado únicamente porque la función
  principal de Despierta es ser un despertador. Su publicación en Google Play
  requiere declarar y justificar ese uso.
- Android 12/12L: `SCHEDULE_EXACT_ALARM` y acceso especial solicitado desde la
  aplicación.
- Despierta 2.2 añade un selector vertical de horas y minutos con ajuste al
  valor más cercano, formato 12/24 horas, entrada manual y respuesta háptica
  opcional.
- Android 13+: permiso de notificaciones.
- Android 14+: acceso de pantalla completa. Si no se concede, Android puede
  mostrar una notificación emergente en vez de abrir la pantalla de alarma.
- Servicio en primer plano de reproducción multimedia, bloqueo parcial de CPU y
  vibración mientras la alarma está activa.

No se solicitan permisos para leer archivos ni contactos. Ninguna aplicación
puede hacer sonar una alarma con el teléfono completamente apagado.

## Instalar el APK de prueba

1. Descarga `Despierta-2.2.apk` desde el artefacto **Despierta-2.2** de la última
   ejecución de **Actions > Generar APK**, o compílalo con `./gradlew assembleDebug`.
2. En el teléfono, permite temporalmente instalar aplicaciones desconocidas para
   el navegador o gestor de archivos usado para abrir el APK.
3. Instala y abre Despierta.
4. Concede notificaciones. Si aparece una tarjeta de configuración, habilita
   alarmas exactas y/o pantalla completa, y vuelve a la aplicación.
5. Crea una alarma a dos o tres minutos de la hora actual y bloquea la pantalla.

El APK de depuración sirve para pruebas; una publicación en Google Play debe usar
una clave de firma privada de lanzamiento.

## Compilar y verificar

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest   # requiere dispositivo o emulador
```

El flujo de GitHub Actions ejecuta pruebas unitarias, lint, genera el APK y abre
un emulador Android 16 para las pruebas instrumentadas.

## Diagnóstico

Los eventos de programación, recepción, reinicio, audio, vibración, posponer y
detener usan la etiqueta `DespiertaAlarm`:

```bash
adb logcat -s DespiertaAlarm
adb shell dumpsys alarm | grep com.example.despierta
```

Consulta [docs/PRUEBAS.md](docs/PRUEBAS.md) para el protocolo completo.

## Cerrar frente a forzar detención

Deslizar Despierta fuera de recientes o cerrar su interfaz normalmente no cancela
las alarmas registradas. En cambio, **Forzar detención** desde Ajustes coloca el
paquete en estado detenido: Android cancela o impide alarmas y receptores hasta
que el usuario vuelva a abrir la aplicación. Al abrirla, Despierta reconcilia las
alarmas guardadas y las registra otra vez.
