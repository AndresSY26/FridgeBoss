# 📱 FridgeBoss

> **Transforma tu nevera, reduce el desperdicio y cocina con inteligencia.** 

**FridgeBoss** es una aplicación móvil moderna para Android diseñada para combatir el desperdicio de comida en los hogares mediante el uso de Inteligencia Artificial de vanguardia. Gracias a la integración con modelos locales y en la nube de Gemini y un sistema de gamificación integrado, FridgeBoss te ayuda a gestionar tu inventario de alimentos, predecir fechas de vencimiento de forma inteligente y sugerir recetas rescatadoras de comida exquisitas.

---

## 🚀 Características Principales

### 1. Despensa Inteligente (OCR con Gemini IA)
Olvida registrar tus alimentos uno a uno de forma manual. FridgeBoss te permite escanear o subir una foto de tus tickets de compra reales de supermercados locales. La IA procesa la imagen automáticamente para extraer:
*   **Alineación de nombres:** Limpieza y estandarización de nombres comerciales a ingredientes reconocibles.
*   **Precios y Cantidades:** Registro exacto para el control financiero.
*   **Estimación de Vida Útil:** Cálculo algorítmico y con IA para determinar cuántos días le quedan a cada alimento antes de vencer en tu refrigerador.

### 2. Chef IA (Cocina Rescatadora)
Un asistente gastronómico con gran sazón hogareña especializado en desperdicio cero. Analiza en tiempo real los ingredientes más críticos próximos a vencer en tu nevera para sugerir recetas creativas, rápidas y llenas de calor de hogar (incorporando deliciosos toques tradicionales como los icónicos *Casquitos de Guayaba Pera en Almíbar de Panela*). El sistema te indicará qué tienes en tu nevera y qué ingredientes adicionales muy básicos de despensa podrías llegar a necesitar.

### 3. Notificaciones Push en Segundo Plano
Integración robusta con **WorkManager** para programar de forma persistente y eficiente alertas de sistema diarias. Todas las mañanas a las **9:00 AM**, un servicio silencioso en segundo plano analiza el inventario local en la base de datos de Room y dispara notificaciones del sistema de alta importancia indicando los productos con menos de 2 días de vida útil, invitando al usuario a cocinarlos antes de que caduquen.

### 4. Sistema de Gamificación: "Racha Cero Desperdicio"
El control ambiental y financiero se convierte en un juego con incentivos saludables:
*   **Racha Actual:** Incrementa +1 día por cada jornada consecutiva en la que consumas alimentos antes de que caduquen.
*   **Récord Máximo (`maxRacha`):** Almacena y despliega tu récord histórico de días consecutivos sin desperdiciar comida.
*   **Dinero Salvado:** Suma de manera acumulativa el precio estimado de cada alimento consumido con éxito durante el mes actual.
*   **Penalización de Basura:** Si dejas que un ingrediente venza y presionas el botón de "Eliminar/Basura", tu racha se reiniciará inmediatamente a 0 días para motivar una mayor consciencia en tu próxima compra.

---

## 🛠️ Arquitectura y Tech Stack

FridgeBoss se ha construido siguiendo los estándares modernos de desarrollo nativo en Android y lineamientos de Google para aplicaciones escalables y mantenibles:

*   **Patrón Arquitectónico:** Clean Architecture y MVVM (Model-View-ViewModel) para una separación clara de responsabilidades.
*   **Interfaz de Usuario:** **Jetpack Compose** impulsando un diseño cohesivo e interactivo bajo la especificación **Material Design 3 (M3)**.
*   **Persistencia de Datos:**
    *   **Room Database:** Base de datos relacional SQLite local con soporte para flujos reactivos (`Flow`) para gestionar el ciclo de vida de los ingredientes del refrigerador y las recetas salvadas.
    *   **SharedPreferences / Preferences DataStore:** Almacenamiento rápido y seguro de variables de puntuación y gamificación global como las estadísticas de racha y saldo de ahorro.
*   **Tareas en Segundo Plano:** **WorkManager** y constructor de canales de notificación avanzados compatibles con Android Oreo (8.0) en adelante.
*   **Asincronía y Flujos:** Kotlin Coroutines y `StateFlow`/`SharedFlow` para la propagación reactiva de eventos y estado en la interfaz de pantalla principal.
*   **Integración de IA:** Google Gen AI SDK con llamadas estructuradas a los modelos **Gemini 1.5 Pro / Flash** con configuraciones avanzadas de temperatura y prompts optimizados en formato JSON estructurado.

---

## 💻 Guía de Instalación (Para Desarrolladores)

### Requisitos Previos
*   **Android Studio Jellyfish** (2023.3.1) o superior.
*   **Android SDK 34** (Plataforma y Build Tools correspondientes).
*   Un dispositivo físico o emulador con Android 8.0 (API 26) o superior para el soporte completo de canales de notificación y servicios de WorkManager.

### Paso 1: Clonar el Repositorio
Abre la terminal de tu máquina local y clona el repositorio ejecutando:

```bash
git clone https://github.com/AndressY26/FridgeBoss.git
```

### Paso 2: Importar en Android Studio
1. Abre Android Studio.
2. Selecciona **File > Open** o **Import Project**.
3. Navega hasta el directorio donde clonaste el repositorio y selecciónalo.
4. Espera a que Gradle termine de sincronizar y descargar todas las dependencias del proyecto.

### Paso 3: Configurar la API Key de Gemini
Para que el motor del Chef Inteligente y la digitalización de tickets por OCR funcionen, necesitas añadir tu API Key de Google AI Studio. 

1. Ve a la raíz del proyecto.
2. Abre o crea el archivo `local.properties`.
3. Añade la siguiente línea con tu clave secreta:

```properties
gemini.api.key=TU_API_KEY_AQUÍ
```

*(Nota: Esta clave es inyectada en tiempo de compilación a través del BuildConfig del compilador y nunca se expone en el código ni se sube al control de versiones de git).*

---

## 🤝 Contribuciones

¡Las contribuciones de la comunidad hacen que FridgeBoss crezca y mejore con mayor velocidad! Si tienes ideas para perfeccionar el reconocimiento con IA, añadir nuevas insignias de gamificación, mejorar el soporte de vistas en tabletas o refactorizar el flujo de datos:

1. Realiza un **Fork** de este proyecto.
2. Crea una rama para tu nueva característica o solución de error:
   ```bash
   git checkout -b feature/nueva-mejora
   ```
3. Realiza tus modificaciones y sube el commit asegurando explicaciones claras:
   ```bash
   git commit -m "Añadida mejora en el cálculo de días de vida de vegetales"
   ```
4. Sube la rama a tu repositorio remoto:
   ```bash
   git push origin feature/nueva-mejora
   ```
5. Abre un **Pull Request** explicando detalladamente el alcance de tus cambios.

---

*¡Únete al movimiento Desperdicio Cero y sé el verdadero **FridgeBoss** de tu cocina!* 🍃🥑
