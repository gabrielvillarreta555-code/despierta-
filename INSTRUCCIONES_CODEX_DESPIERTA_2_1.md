# Encargo autónomo para Codex: Despierta 2.1

## Objetivo
Implementar y entregar una mejora sustancial y verificable de la aplicación Android Despierta. NO basta con renombrar APK, cambiar versionName, redactar un informe o abrir un PR sin cambios visuales apreciables.

## Contexto y precauciones
- Repositorio: gabrielvillarreta555-code/despierta-
- Revisar main, PR #6 (fix/turnos-inteligentes) y PR #7 (feature/despierta-2-ui-integrada), evitando perder sus correcciones.
- La rama de instrucciones es una referencia; crear una rama de implementación propia basada en el código correcto. No fusionar main ni PR existentes sin autorización.
- Mantener applicationId, datos locales y compatibilidad con alarmas ya guardadas. No desinstalar la app del usuario.
- El sistema de alarmas es crítico: no sacrificar fiabilidad por estética.

## Trabajo obligatorio
1. Rediseñar realmente la pantalla de inicio: cabecera compacta, reloj digital actualizado en vivo, fecha, próxima alarma y cuenta regresiva precisa, tarjetas de alarmas con nombre/hora/repetición/estado, botón de añadir claro, estado vacío útil y sin grandes espacios desperdiciados.
2. Diseñar una experiencia coherente y pulida: paleta oscura con un único acento, tipografía y espaciados consistentes, accesibilidad, tamaños táctiles adecuados, pantallas pequeñas, animaciones cortas y háptica opcional.
3. Presentar secciones Alarmas, Turnos y Ajustes, integrándolas con la navegación existente sin romper funciones.
4. Dar una interfaz clara a turnos inteligentes: calendario o vista de días de trabajo/descanso y próximas activaciones, preservando los algoritmos corregidos y sus tests.
5. Asegurar que crear, editar, activar, pausar, borrar, repetir y reprogramar alarmas continúe funcionando. Revisar reinicio del móvil, permisos y pantalla bloqueada; informar qué solo puede comprobarse en dispositivo real.
6. Establecer versionName 2.1 y versionCode mayor que el máximo actual. No cambiar identificador de paquete ni firma deliberadamente.

## Verificación obligatoria
- Antes y después: capturas reales de la UI generadas por emulador o prueba visual, cuando el entorno lo permita. No inventar capturas.
- Pruebas unitarias, lint, compilación y pruebas instrumentadas cuando el emulador funcione. Distinguir fallos de infraestructura de fallos de app, sin ocultarlos.
- Verificar que el APK FINAL contiene el commit exacto de implementación, versionName 2.1, versionCode actualizado y cambios de interfaz. Publicarlo como artefacto GitHub Actions con nombre Despierta-2.1.
- Entregar PR, commit SHA, enlace directo al artefacto, capturas y lista breve de pruebas que pasaron/fallaron.
- NO declarar completado el trabajo si solo se hizo planificación, no hay APK o la interfaz sigue esencialmente igual.
- No fusionar automáticamente. No borrar datos ni pedir al usuario que desinstale la versión funcional.

## Forma de ejecución
Actúa como agente de desarrollo autónomo: inspecciona, implementa, prueba, corrige y publica el resultado en una rama/PR. Pregunta solamente por bloqueos reales que requieran permisos o decisiones del usuario. Prioriza un resultado visible y funcional, no informes extensos.
