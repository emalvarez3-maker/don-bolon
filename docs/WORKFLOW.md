# Método de trabajo: grafo de nodos

Prompt de grafos inspirado en el autoresearch de Andrej Karpathy. Es la base para desarrollar esta app: toda tarea que cambie código se resuelve con él. `CLAUDE.md` dice cómo se aplica aquí.

```xml
<prompt>
<objetivo>
Resuelve {{TAREA}} como un grafo de nodos acotados, no como una sola sesión larga. Deja un resultado usable, un informe de verificación y un graph.md para que otra sesión pueda repetirlo o ampliarlo. Define qué es «terminado» antes de empezar. Para cuando todos los nodos pasen sus checks o se llegue al límite pactado.
</objetivo>

<rol>
Eres quien diseña, ejecuta y mantiene el grafo. Lee primero las instrucciones del proyecto y revisa el trabajo existente antes de tocar nada. Planifica e integra tú; delega en subagentes trabajo con límites claros. Anota cada decisión que cambie el grafo.
</rol>

<principios>
Ideas tomadas del autoresearch de Karpathy:
- Mide una línea base antes de cambiar el sistema.
- Cada nodo: un alcance claro y un evaluador fijo.
- Cambia una cosa, mídela y quédate con lo que mejore. Registra también los intentos fallidos.
- Mejor pocos nodos si así los resultados se pueden comparar.
</principios>

<entradas>
Tarea: {{TAREA}}
Entregable y ruta: {{SALIDA}}
Criterios de aceptación: {{CHECKS}}
Material de partida: {{FUENTES}}
Cambios permitidos: {{ALCANCE}}
Límite de ejecución: {{LIMITE}}
Rellénalas antes de empezar. Si falta un dato que cambia el resultado, haz una sola pregunta concreta.
</entradas>

<capa_1_division>
Parte {{TAREA}} en nodos antes de trabajar. Un nodo = un trabajo acotado: una entrada, una salida, un check.
- Divide por lo que cada nodo necesita leer, no por carpetas.
- Solo hay arista si un nodo usa la salida de otro.
- Los nodos sin arista entre sí van en paralelo.
Escribe nodos, aristas y checks en graph.md y espera aprobación.
</capa_1_division>

<capa_2_nodos>
Cada nodo corre en su propio subagente, solo con el contexto que necesita:
1. Recibe su especificación, entradas y check.
2. Construye únicamente su artefacto.
3. Pasa su check contra el archivo guardado.
4. Devuelve ruta, veredicto y evidencia.
Que las especificaciones sirvan para otros datos de entrada.
</capa_2_nodos>

<capa_3_nodos_de_codigo>
Si un paso tiene una única respuesta correcta (unir, ordenar, deduplicar, comparar, contar), hazlo con código, no con el modelo. El modelo es solo para juzgar.
- Lista cada nodo de código en graph.md.
- Nada determinista pasa por un subagente.
Si falta una herramienta, nombra el bloqueo y conserva los nodos que sí se pueden completar.
</capa_3_nodos_de_codigo>

<capa_4_permisos>
Comprueba los permisos activos antes de lanzar el grafo. Las ediciones, dentro de {{ALCANCE}}. Usa reglas de herramientas o sandbox cuando haga falta imponerlo; ejecutar en solitario no es un límite.
- Pregunta antes de publicar, enviar o gastar.
- Nunca dos sesiones editando el mismo archivo.
- Guarda una versión recuperable antes de fusiones arriesgadas.
</capa_4_permisos>

<capa_5_puertas>
Pon una puerta en cada arista con trabajo arriesgado. Solo se abre con evidencia: primero checks deterministas, luego el informe del nodo y, en último lugar, la confianza del modelo.
Guarda checks.md con una fila por requisito:
nodo | requisito | veredicto | evidencia
Usa pasa, falla o sin resolver. Incluye rutas o la salida real del check. Que la evidencia que falte se vea.
</capa_5_puertas>

<capa_6_vueltas>
Dos caminos de vuelta.
Corrección: una puerta fallida devuelve el trabajo al nodo que lo hizo, con motivo, evidencia y el alcance «arregla solo este nodo». Máximo tres intentos.
Aprendizaje: tras un resultado aceptado, apunta en graph.md la regla que confirma, para que la siguiente ejecución parta de ahí.
</capa_6_vueltas>

<capa_7_cierre>
Antes de ejecutar, crea una checklist de cierre a partir de {{CHECKS}}, con evidencia observable en cada punto:
- La salida de cada nodo existe y se puede abrir.
- Cada puerta se pasó sobre la versión guardada.
- Los fallos están arreglados o declarados.
- CLAUDE.md recoge las reglas de esta ejecución.
Si un límite o bloqueo corta la ejecución, devuelve un estado parcial con los pasos exactos pendientes.
</capa_7_cierre>

<bucle_de_ejecucion>
1. Lee graph.md y elige los nodos listos.
2. Lanza en paralelo los independientes.
3. Pasa cada salida por su puerta.
4. Devuelve los nodos fallidos, nunca el lote entero.
5. Integra lo aceptado y corre los checks completos.
6. Sigue hasta terminar, bloquearte o llegar a {{LIMITE}}.
Nunca rebajes una puerta para conseguir un aprobado.
</bucle_de_ejecucion>

<relevo>
Tras cada fase, actualiza progress.md:
Grafo: nodos hechos, en curso y bloqueados.
Salidas: rutas exactas de los archivos guardados.
Decisiones: qué cambió en graph.md y por qué.
Pendientes: fallos, dudas y bloqueos.
Siguiente acción: el próximo nodo listo.
Al retomar, lee graph.md y progress.md y sigue desde ahí.
</relevo>

<entrega>
Devuelve {{SALIDA}}, graph.md, checks.md y progress.md. Di qué se comprobó, qué pasó y qué queda abierto. Guarda el grafo como plantilla reutilizable. Informa del consumo medido solo si lo tienes; no deduzcas ahorros a partir del prompt.
</entrega>
</prompt>
```
