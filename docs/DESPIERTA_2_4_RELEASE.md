# Despierta 2.4 — entrega para revisión

## Origen de la recuperación

Esta versión parte del merge funcional de Despierta 2.3 (`0a540854802b22a0d1d7a022b656d1be68ea9fbf`).
Los cambios 2.4 se recuperaron del ZIP de Jules y se contrastaron con la rama remota que
contiene el mismo árbol de fuentes. Los archivos ausentes en el ZIP se conservan desde 2.3.

## Artefactos

- `Despierta-2.4.apk`: APK de depuración instalable para pruebas.
- `Despierta-2.4-unsigned.aab`: Android App Bundle release sin clave privada de publicación.
- `BUILD-INFO.txt`: commit, versión y sumas SHA-256.
- `AAB-SIGNING.txt`: resultado de la comprobación de firma.

## Firma para Google Play

El AAB generado por CI es deliberadamente **no firmado** porque el repositorio no contiene ni
debe contener la clave privada de carga. No está listo para enviarse directamente a Play Console:
debe firmarse mediante una configuración segura de CI con la clave de carga asociada a la app,
o generarse desde Android Studio usando esa misma clave. Google Play App Signing no elimina el
requisito de autenticar cada subida con la clave de carga.

No se debe firmar el AAB release con la clave `debug` ni publicar un keystore, contraseña o
archivo de propiedades de firma en el repositorio.
