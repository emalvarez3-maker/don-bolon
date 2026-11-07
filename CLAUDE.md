# Don Bolón

App Android (Kotlin, Jetpack Compose) que registra lo que comes con IA. Paquete `ec.donbolon.app`.

## Cómo se desarrolla

Toda tarea que cambie código sigue el método de grafos de [docs/WORKFLOW.md](docs/WORKFLOW.md):

1. Rellena las entradas (TAREA, SALIDA, CHECKS, FUENTES, ALCANCE, LIMITE). Si falta un dato que cambia el resultado, haz una sola pregunta.
2. Mide la línea base: build y tests antes de tocar nada.
3. Escribe `graph.md` con nodos, aristas y checks, y espera la aprobación del usuario antes de ejecutar.
4. Ejecuta el bucle: nodos independientes en paralelo, cada salida por su puerta, fallos de vuelta solo a su nodo (máximo tres intentos).
5. Cierra con `checks.md`, `progress.md` y las reglas nuevas apuntadas aquí.

Cada ejecución vive en `.claude/runs/AAAA-MM-DD-tarea/` (graph.md, checks.md, progress.md). Esa carpeta no se sube a git porque puede contener datos personales. Al retomar una tarea, lee primero su graph.md y progress.md.

Un cambio de una línea o una pregunta no necesita grafo: basta con correr los checks fijos.

## Checks fijos (puertas deterministas)

Toda ejecución pasa estas puertas antes de cerrarse:

| Puerta | Comando o evidencia |
|---|---|
| Compila | `gradlew.bat :app:assembleDebug` termina en BUILD SUCCESSFUL |
| Tests | `gradlew.bat :app:testDebugUnitTest`, 0 fallos (revisar `app/build/test-results`) |
| El APK trae el cambio | `unzip -p app/build/outputs/apk/debug/app-debug.apk 'classes*.dex' \| grep -a` con un texto nuevo |
| Funciona en el teléfono | Instalado en el Pixel 7 (`adb install -r`), probado con uiautomator y capturas; leer `logcat -s DonBolon:*` |
| Sin secretos | `git diff` sin `AIza…`, `AQ.…` ni `sk-ant-…`; nunca nombre, peso ni comidas del usuario |

## Reglas aprendidas

- Nunca pedir la llave de IA por el chat ni repetirla; los registros muestran solo una huella (3 primeros caracteres y longitud).
- Para saber si una llave se guardó, mira la fecha de `files/secrets/GEMINI` con `run-as ec.donbolon.app ls -l files/secrets`, no lo que diga el botón.
- Las llaves Gemini `AQ.` reciben 401 en algunos modelos: `gemini-3.6-flash` va primero y un 401 prueba el siguiente.
- No tocar el teléfono mientras el usuario lo usa; nunca desbloquearlo ni cambiar ajustes del sistema.
- Publicar (push, release) se hace en las dos cuentas: `origin` (emalvarez3-maker) y `tebl22`.
- La mascota es un bolón de queso, nunca un oso; tema oscuro vintage, nada morado ni parecido a Dexter.
