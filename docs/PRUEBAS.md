# Protocolo de pruebas de alarmas

Antes de cada prueba, confirma que el volumen de alarmas sea audible y que las
tarjetas de permisos ya no aparezcan. Mantén `adb logcat -s DespiertaAlarm` activo
cuando sea posible. Programa cada alarma con dos o tres minutos de margen.

## Casos obligatorios

1. **Aplicación abierta:** crea una alarma única y deja Despierta visible. Deben
   aparecer la pantalla y notificación, sonar el audio y vibrar si el equipo tiene
   vibrador.
2. **Aplicación cerrada:** crea otra alarma, vuelve al inicio y quita Despierta de
   recientes sin usar «Forzar detención». La alarma debe activarse.
3. **Pantalla bloqueada:** programa una alarma, bloquea el teléfono y espera. Con
   el acceso de pantalla completa concedido debe mostrarse la pantalla sobre el
   bloqueo; sin él debe aparecer al menos la notificación de alta prioridad y el
   sonido/vibración.
4. **Después de reiniciar:** crea una alarma futura, reinicia el teléfono, espera
   a que finalice el arranque y comprueba con `dumpsys alarm` que esté registrada.
5. **Varias alarmas:** crea al menos tres horas distintas. Comprueba que todas
   aparecen en `dumpsys alarm` y que cada una se activa una sola vez.
6. **Repetitiva:** selecciona el día actual y otro día, espera la activación y
   comprueba que la siguiente fecha queda registrada sin duplicados.
7. **Posponer y detener:** pulsa «Posponer 5 minutos», reinicia opcionalmente
   durante la espera y confirma la segunda activación. Después pulsa «Detener» y
   verifica que cesen audio, vibración y notificación.
8. **Desafío matemático:** habilítalo, introduce una respuesta incorrecta y luego
   la correcta. La acción directa de detener no debe eludir el desafío.
9. **Sonido alternativo:** elige un tono, revoca o invalida su URI si el dispositivo
   lo permite y confirma en el registro `fallback=true` y un tono audible del
   sistema.

## Restricciones del sistema

- Repite los casos 2 y 3 con ahorro de batería/Doze; `setAlarmClock()` debe seguir
  representado como alarma despertador del sistema.
- Repite un caso tras **Forzar detención**. Se espera que no suene hasta volver a
  abrir Despierta; esto es una restricción deliberada de Android, distinta de
  cerrar la aplicación normalmente.
- No pruebes ni prometas activación con el teléfono completamente apagado.

## Evidencia esperada

En el registro deben aparecer, según el caso: `schedule_exact`, `receiver_fired`,
`service_start_requested`, `sound_started`, `vibration_started`, `snooze_saved`,
`ring_stopped` y `reconcile_complete`. Cualquier `schedule_failed`,
`service_start_failed` o `sound_unavailable` debe conservarse junto con modelo de
teléfono, versión Android y estado de permisos.

## Cobertura automática de Despierta 2.2

- Las pruebas JVM verifican ciclos 12×4, 7×7 y personalizados, cambios de mes y
  año, año bisiesto, horario de verano, excepciones y conversiones 12/24 horas.
- Las pruebas instrumentadas verifican almacenamiento corrupto, identificadores
  duplicados, restauración por arranque/actualización, reemplazo de
  `PendingIntent`, posponer y entrega exacta con la pantalla del emulador apagada.
- GitHub Actions ejecuta las pruebas instrumentadas en Android 16 (API 36).

La invocación directa del receptor de arranque comprueba la reprogramación, pero
no sustituye una prueba de reinicio físico completo. Android 13, 14 y 15, los
fabricantes con políticas agresivas de batería, el volumen audible y la pantalla
de bloqueo real requieren pruebas manuales en dispositivos. «Forzar detención»
continúa bloqueando alarmas y receptores hasta que el usuario abre la aplicación.
