# Despierta 2.5 — identidad visual recuperada

Despierta 2.5 parte del commit funcional de Despierta 2.4 y mantiene sin cambios el
programador, receptor, servicio y almacenamiento de alarmas.

## Cambios visibles

- Paleta coherente azul noche, celeste suave y lavanda en la pantalla principal,
  editor, turnos, ajustes y pantalla de alarma activa.
- Tarjeta de descanso estimado calculada desde la hora actual hasta la siguiente alarma.
- Mensajes breves para preparar el descanso, sin sustituir recomendaciones médicas.
- Versión visible 2.5 (`versionCode` 8).

## Artefactos

GitHub Actions publica el APK de prueba y el AAB release sin firma dentro del artefacto
`Despierta-2.5`. El AAB sigue necesitando la clave de carga privada antes de enviarlo a
Google Play; ninguna clave se incluye en el repositorio.
