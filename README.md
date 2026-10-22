<p align="center">
  <img src="docs/bolon.png" width="132" alt="Don Bolón, un bolón de queso gruñón" />
</p>

<h1 align="center">Don Bolón</h1>

<p align="center">
  <b>Le tomas foto a tu plato. Él te dice la verdad.</b><br/>
  Un coach de nutrición con IA, basado en ciencia, con la cara de un bolón de queso muy gruñón.
</p>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/Android-9%2B-3DDC84?logo=android&logoColor=white" />
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" />
  <img alt="IA" src="https://img.shields.io/badge/IA-Gemini%20%7C%20Claude-F2A93B" />
  <img alt="Privacidad" src="https://img.shields.io/badge/datos-cifrados%20en%20tu%20tel%C3%A9fono-111A17" />
</p>

---

Don Bolón es una app Android para **comer con consciencia todos los días**: registra lo que comes con una foto, calcula tus calorías y macros con fórmulas publicadas, te arma un menú ecuatoriano que cuadra con tus metas y te acompaña en la parte difícil, que no es saber qué comer sino **hacerlo con constancia**.

Él gruñe contra los hábitos, nunca contra ti. No hay culpa, no hay vergüenza y no hay dietas extremas.

<p align="center">
  <img src="docs/registrar.png" width="200" alt="Registrar comida con foto, voz o texto" />
  &nbsp;
  <img src="docs/prioridades.png" width="200" alt="Prioridades en un pentágono" />
  &nbsp;
  <img src="docs/progreso.png" width="200" alt="Progreso y enfoque de la semana" />
  &nbsp;
  <img src="docs/sos.png" width="200" alt="SOS antojo: respiración guiada" />
</p>

## Lo que hace

| | |
|---|---|
| 📷 **Foto primero** | Tocas **+**, tomas la foto y el análisis arranca solo. La IA separa cada ingrediente, estima gramos, cuenta el aceite oculto y comprueba que `kcal ≈ 4·P + 4·C + 9·G`. Solo si algo que la foto no muestra cambia las calorías (¿frito o a la plancha?), te hace **una** pregunta y corrige. |
| 🎤 **O díselo** | Por voz o por texto ("verde majado, huevo frito, café con leche"): también lo analiza la IA. Con **Rápido** repites una comida reciente o lo de siempre en un toque, y en la revisión ajustas cada porción con una barra. Si falla la conexión, una libreta offline con comida ecuatoriana sirve de respaldo. |
| ⬠ **Metas en un pentágono** | Perder grasa, ganar músculo, rendimiento, salud y flexibilidad, de 0 a 100. Arrastras las puntas y todo se recalcula: calorías, proteína, grasa, fibra, margen libre, días de fuerza y cardio. |
| 🍽️ **¿Qué como ahora?** | Una idea para tu próxima comida, ya cuadrada con lo que te queda del día. "Lo comí" la registra con un toque; "Otra idea" propone otra. |
| 🧠 **Memoria** | Don Bolón recuerda lo que le cuentas (gustos, horarios, lo que te cuesta), aprende tus porciones reales cada vez que corriges a la IA y cada lunes elige **un solo enfoque** para la semana a partir de tu hábito más flojo, con su plan Si-Entonces. Todo lo que sabe de ti lo ves y lo borras en Perfil. |
| 🌙 **Ayuno intermitente** | Tu ventana es libre (eliges a qué hora abre y cierra; 12:12 a 18:6 son solo atajos) y la app te **recomienda una con ciencia**: cerrar 3 h antes de dormir (Vujović et al., 2022), 8–10 h de duración y empezando cuando ya sueles comer. Incluye un contador en vivo, ideas de comida solo dentro de la ventana y Don Bolón que no te regaña por no comer cuando estás ayunando. El ayuno funciona igual que cualquier déficit; su ventaja es que a muchos les facilita sostenerlo (Lowe et al., 2020; Liu et al., 2022). |
| 🔔 **Recordatorios** | Agua cada 2 horas solo si vas atrasado, con un botón "Tomé uno" en la misma notificación, y avisos cuando abre tu ventana y una hora antes de que cierre. |
| ✅ **Constancia** | Cuatro hábitos diarios (registré, proteína, entrené, agua), racha hacia los 66 días de un hábito y peso suavizado para ver la tendencia real, no el agua del día. |
| 🧀 **Don Bolón te lo dice** | La ciencia no está pegada en la pantalla: él la trae cuando viene al caso (poca proteína, te pasaste, comiste por estrés, no has entrenado…). Toca su globo para ver el estudio, tócalo a él para hablarle. Si comiste sin hambre, te ofrece un SOS de 3 minutos para surfear el antojo. |
| 🎨 **Personaje con humor** | Respira, parpadea y se le escurre el queso. Cambia de ánimo según tu día: feliz con ojos sonrientes cuando cumples, tranquilo en el día a día, gruñón solo cuando algo necesita arreglo, y echa vapor si lo tocas. |

## La ciencia detrás de cada número

Nada es inventado. Don Bolón las cita cuando te da un consejo, y están en **Perfil → ¿Por qué estos números?**.

| Qué | Cómo se calcula | Fuente |
|---|---|---|
| Gasto en reposo | Mifflin–St Jeor | Mifflin et al. (1990); Frankenfield et al. (2005) |
| Gasto diario | × factor de actividad | FAO/OMS/UNU |
| Déficit o superávit | Grasa pura → −25 %, músculo puro → +10 %, ambos (recomposición) → ≈ −12 %; rendimiento suaviza el déficit hasta 30 % | Helms et al. (2014); Iraki et al. (2019); Barakat et al. (2020); Mountjoy et al. (2018) |
| Límites de seguridad | Déficit ≤ 1 % del peso/semana, superávit ≤ 1,5 %/mes, nunca menos de 1500/1200 kcal | Helms et al. (2014) |
| Proteína | 1,2–2,4 g/kg según prioridades | Morton et al. (2018); Jäger et al. (2017, ISSN) |
| Fibra | ≥ 14 g/1000 kcal y ≥ 25–30 g | Reynolds et al. (2019, The Lancet) |
| Fuerza y cardio | Cada músculo ≥ 2 veces/semana; 150 min de cardio | Schoenfeld et al. (2016); OMS (2020) |
| Registrar | La constancia predice el resultado más que la precisión | Burke et al. (2011) |
| Hábitos | ~66 días para que algo sea automático | Lally et al. (2010) |
| Planes Si-Entonces | Duplican la probabilidad de cumplir | Gollwitzer & Sheeran (2006) |
| Restricción flexible | Menos atracones que las reglas de todo-o-nada | Westenhoefer et al. (1999) |
| Después de un desliz | Autocompasión, sin compensar | Adams & Leary (2007) |
| Antojos | Surfear la ola en vez de pelearla | Bowen & Marlatt (2009) |

## Privacidad y seguridad

- **Tu diario no sale del teléfono.** Se guarda cifrado con AES-256-GCM y una llave que vive en el Android Keystore. La app desactiva el respaldo en la nube y la transferencia entre dispositivos.
- **Tu llave de IA es tuya.** Se escribe dentro de la app y se guarda cifrada igual que el diario. Este repositorio no contiene ninguna llave, token ni dato personal.
- **Solo HTTPS.** El tráfico sin cifrar está bloqueado. Lo único que se envía, y solo cuando tú pides un análisis, es la foto y tu descripción al proveedor que elegiste (Google Gemini o Anthropic Claude).
- **Bloqueo opcional** con huella, cara o PIN.
- **Sin IA también funciona:** la libreta offline, el menú y el coach responden con los datos locales.

## Cómo usarla

1. Compila e instala:
   ```bash
   ./gradlew :app:installDebug
   ```
2. Abre la app, llena tus datos y elige tus prioridades.
3. En **Perfil → Inteligencia artificial**, pega tu llave de [Google AI Studio](https://aistudio.google.com/apikey) o de [Anthropic](https://console.anthropic.com/). Sin llave, la app usa la libreta offline.

Requisitos para compilar: JDK 17 y Android SDK 36. Funciona en Android 9 o superior.

## Hecho con

Kotlin · Jetpack Compose · Material 3 · Android Keystore · BiometricPrompt · Gemini API · Anthropic Java SDK. El personaje está dibujado a mano en un `Canvas` de Compose, sin imágenes.

---

<sub>Don Bolón es una guía educativa y no reemplaza a un médico o nutricionista. Si tienes una condición médica, estás embarazada o tienes historial de trastornos alimentarios, consulta a un profesional.</sub>

## Colores con intención

La paleta es oscura y vintage, y cada color tiene un trabajo según la psicología del color aplicada a la comida:

| Color | Dónde | Por qué |
|---|---|---|
| Verde salvia | Fondo, botones, acciones | Lo verde se percibe fresco y saludable (Schuldt, 2013) |
| Mostaza | Don Bolón, logros, proteína | Los amarillos y naranjas abren el apetito (Spence, 2015): se usan como premio, no de fondo |
| Terracota | Solo cuando te pasas | El rojo funciona como señal de freno y reduce el picoteo (Genschow et al., 2012), suavizado para informar sin regañar |
| Azul polvo | Agua y carbohidratos | Calma y no despierta el apetito |
