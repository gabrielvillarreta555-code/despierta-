# Encargo autónomo para Codex — Despierta 2.3

## Base y restricciones
Repositorio: gabrielvillarreta555-code/despierta-
Partir del commit funcional de Despierta 2.2: 03c771385afe513b1a632b08b45c818e2088e65b (PR #9).
Crear rama de implementación propia. Esta rama instructions/despierta-2.3 contiene solo instrucciones.
No fusionar PR ni modificar main automáticamente. Mantener applicationId com.example.despierta, datos, alarmas, turnos 12×4 y estética actual. No cambiar firma intencionalmente.

## Trabajo prioritario
1. Corregir contraste en pantalla de alarma activa: hora actualmente negra sobre fondo oscuro. Usar color legible y contrastado en tema oscuro y claro; probar tamaños de fuente y pantalla bloqueada. Conservar botones grandes de Posponer y Detener.
2. Permitir configurar posposición (5, 10 y 15 minutos), con valor predeterminado compatible con datos previos. Garantizar que la alarma activa solo se detenga si se registra la nueva activación, y evitar duplicados. Añadir pruebas.
3. Permitir seleccionar tono de alarma, preescucharlo de forma controlada y configurar vibración. Manejar URIs inaccesibles y fallback, canales Android y permisos sin forzar ajustes del usuario. Respetar audio focus y liberar recursos.
4. Mostrar diagnóstico claro y accionable cuando falten permisos de alarma exacta, notificaciones, acceso a pantalla completa cuando aplique o cuando el canal de alarmas esté silenciado/bloqueado. No prometer detección universal de restricciones de batería de fabricantes.
5. Auditar escenarios de reprogramación después de reinicio, actualización, pantalla bloqueada y cambios de hora; pruebas automatizadas y matriz de pruebas físicas pendientes.
6. Conservar y probar selector de ruedas, formato 12/24 horas y configuración de turnos.

## Validación y entrega
- Incrementar a versionName 2.3 y versionCode 6 o superior al máximo existente.
- Ejecutar pruebas unitarias, lint, compilación y pruebas instrumentadas; incluir escenarios de posposición y alarmas repetidas.
- Obtener captura real del emulador mostrando la hora corregida en la pantalla de alarma activa. No inventar capturas.
- Publicar APK como artefacto Despierta-2.3 en GitHub Actions; comprobar que procede del commit final y corresponde a la versión 2.3.
- Abrir PR sin fusionarlo; entregar enlace, commit, ejecución de Actions, resultados y límites de verificación en teléfono real.
- No afirmar fiabilidad total basándose solo en emulador. No pedir al usuario desinstalar versión funcional.
- Trabajar de forma autónoma: inspeccionar, implementar, probar, corregir y entregar. Preguntar solo por bloqueos que realmente requieren intervención.
