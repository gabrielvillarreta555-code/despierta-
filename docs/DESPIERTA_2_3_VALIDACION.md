# Despierta 2.3 — validación de fiabilidad

## Cobertura automatizada

- Cálculo de alarmas únicas, semanales, turnos 12×4, 7×7 y personalizados.
- Cruces de mes, año, año bisiesto, zona horaria y cambios DST.
- Persistencia compatible con registros 2.2 y recuperación de datos dañados.
- Posposición de 5, 10 y 15 minutos; rechazo de duraciones inválidas.
- Reemplazo del `PendingIntent` anterior y rechazo de entregas duplicadas o antiguas.
- Reprogramación por reinicio, actualización, desbloqueo, cambio de hora, cambio de zona y
  concesión de alarmas exactas.
- Entrega exacta con pantalla apagada en emulador Android 16, inicio del servicio en primer
  plano y continuidad de una alarma repetitiva.
- Pantalla de alarma activa con hora visible, controles grandes y captura real del emulador
  con escala de fuente aumentada.
- Selector de ruedas y conversiones de hora en formatos 12 y 24 horas.

## Matriz pendiente en teléfonos físicos

Estas comprobaciones dependen del hardware, del fabricante o de decisiones del usuario y no
se pueden declarar aprobadas únicamente con el emulador.

| Escenario | Android 13 | Android 14 | Android 15 | Android 16 |
| --- | --- | --- | --- | --- |
| Sonido audible con pantalla bloqueada | Pendiente | Pendiente | Pendiente | Pendiente |
| Vibración activada/desactivada por alarma | Pendiente | Pendiente | Pendiente | Pendiente |
| Tono personalizado después de reiniciar | Pendiente | Pendiente | Pendiente | Pendiente |
| Posponer 5/10/15 min desde pantalla y notificación | Pendiente | Pendiente | Pendiente | Pendiente |
| Reinicio real y recuperación del siguiente turno 12×4 | Pendiente | Pendiente | Pendiente | Pendiente |
| Actualización sobre 2.2 conservando alarmas | Pendiente | Pendiente | Pendiente | Pendiente |
| Doze prolongado y ahorro de batería del fabricante | Pendiente | Pendiente | Pendiente | Pendiente |
| Volumen de alarma, No molestar y canal configurados por el usuario | Pendiente | Pendiente | Pendiente | Pendiente |

## Restricciones de Android

- `setAlarmClock()` permite una alarma exacta y es compatible con Doze cuando Android concede
  el acceso correspondiente; no permite eludir una detención forzada de la aplicación.
- Un teléfono completamente apagado no puede ejecutar la aplicación.
- Despierta respeta el volumen, No molestar, las notificaciones y el acceso a pantalla completa
  elegidos por el usuario. La pantalla de Ajustes indica cómo corregir los estados detectables.
- Algunos fabricantes aplican restricciones adicionales de batería que Android no expone de
  forma universal; deben comprobarse en cada dispositivo.
