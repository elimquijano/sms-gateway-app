# SMS Gateway - Android Client

![Kotlin](https://img.shields.io/badge/Kotlin-1.9.x-7F52FF?style=for-the-badge&logo=kotlin)
![Android API](https://img.shields.io/badge/API-26%2B-A4C639?style=for-the-badge&logo=android)
![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)

Esta es la aplicación cliente nativa de Android para el sistema de [Pasarela de SMS con Node.js](https://github.com/tu-usuario/tu-repo-servidor). Su función es convertir un teléfono Android en una pasarela de envío de SMS programable, controlada remotamente por un servidor backend.

La aplicación establece una conexión persistente con el servidor, recibe órdenes de envío y utiliza la SIM y el plan de SMS del teléfono para ejecutar las tareas.

**⚠️ Nota Importante:** Esta aplicación está diseñada para uso personal, empresarial interno o para fines de prueba. Debido a las estrictas políticas de Google Play sobre el permiso `SEND_SMS`, **no es apta para ser publicada en la tienda oficial**.

## 📱 Screenshot

*(Añade aquí una captura de pantalla de la aplicación en funcionamiento)*

![App Screenshot](https://via.placeholder.com/300x600.png?text=App+Screenshot)

## 🚀 Características

- **Servicio en Primer Plano (`ForegroundService`):** Asegura que la aplicación se mantenga activa y conectada en segundo plano, mostrando una notificación persistente para informar al usuario.
- **Conexión WebSocket Robusta:** Establece una conexión en tiempo real con el servidor backend y cuenta con un sistema de reconexión automática con backoff exponencial en caso de pérdida de señal.
- **Configuración Dinámica en la App:** La URL completa del servidor (incluyendo IP, puerto y token) se configura directamente desde la interfaz, sin necesidad de recompilar.
- **Persistencia de Datos (`DataStore`):** La última URL configurada y el historial de logs se guardan localmente, para que estén disponibles al reiniciar la app.
- **Logs en Tiempo Real:** Muestra un registro detallado de la actividad (conexiones, mensajes recibidos, SMS enviados, errores) en la pantalla principal, con un botón para limpiar el historial.
- **Gestión de Permisos Moderna:** Solicita de forma clara los permisos necesarios (`SMS`, `Notificaciones` en Android 13+) y guía al usuario para desactivar la optimización de batería, un paso crucial para el funcionamiento a largo plazo.
- **Interfaz Atractiva y Adaptable:** Los colores y la apariencia se ajustan automáticamente al modo claro u oscuro del sistema operativo.

## ⚙️ Cómo Funciona

El rol de la aplicación es ser un "worker" o trabajador que ejecuta las órdenes del servidor:
1.  **Conexión:** Al iniciar el servicio, la app se conecta a la URL del servidor proporcionada, autenticándose vía WebSocket.
2.  **Escucha Activa:** Mantiene la conexión abierta en segundo plano, esperando recibir mensajes.
3.  **Ejecución de Tareas:** Cuando recibe una tarea con el formato `{ "type": "NEW_TASK", ... }`, utiliza el `SmsManager` de Android para enviar el mensaje de texto.
4.  **Reporte de Estado:** Tras intentar el envío, notifica inmediatamente al servidor si la tarea fue completada (`SENT`) o si falló (`FAILED`), devolviendo los detalles del error en este último caso.

## 📋 Prerrequisitos

- [Android Studio](https://developer.android.com/studio) (última versión estable).
- Un **teléfono Android físico** con un plan de SMS activo. Los emuladores de Android Studio no pueden enviar SMS reales.
- El **servidor backend de la pasarela debe estar en ejecución** y accesible desde la misma red WiFi que el teléfono.
- La URL completa del servidor (ej: `ws://192.168.1.100:3000?token=...`).

## 🛠️ Instalación y Configuración

1.  **Clona este repositorio:**
    ```bash
    git clone https://github.com/elimquijano/sms-gateway-app.git
    ```
2.  **Abre el proyecto en Android Studio:**
    -   Desde Android Studio, selecciona **"Open"**.
    -   Navega hasta la carpeta del proyecto que acabas de clonar y ábrela.
3.  **Sincronización:** Espera a que Android Studio termine de sincronizar el proyecto con Gradle. Esto puede tardar unos minutos la primera vez.
4.  **Conecta tu teléfono:** Conecta tu dispositivo Android físico al ordenador mediante un cable USB y asegúrate de que la **Depuración USB** esté habilitada en las opciones de desarrollador del teléfono.

## 🚀 Cómo Usar la App

1.  **Ejecuta la aplicación** desde Android Studio, seleccionando tu teléfono físico como dispositivo de destino.
2.  Una vez instalada, abre la app en tu teléfono.
3.  **Configura la URL:**
    -   En el campo de texto, introduce la **URL completa** de tu servidor backend. Debe tener el siguiente formato:
        `ws://<IP_DEL_SERVIDOR>:<PUERTO>?token=<TU_TOKEN_SECRETO>`
        *Ejemplo: `ws://192.168.1.100:3000?token=un_token_muy_largo_y_secreto`*
4.  **Inicia el Servicio:**
    -   Pulsa el botón verde **"Iniciar Servicio"**.
    -   La aplicación te pedirá permiso para **enviar SMS**. Acéptalo.
    -   En Android 13 o superior, te pedirá permiso para **enviar notificaciones**. Acéptalo (es necesario para el servicio en primer plano).
    -   Aparecerá un diálogo sobre la **optimización de batería**. Pulsa "Ir a Configuración", busca la app en la lista y selecciona "No optimizar" o "Sin restricciones". Este paso es vital.
5.  Una vez concedidos los permisos, el botón cambiará a color rojo y el texto a **"Detener Servicio"**. En la sección de logs, verás los mensajes de conexión.
6.  ¡Listo! La pasarela está activa. Ahora puedes enviar solicitudes a la API de tu servidor y el teléfono se encargará de mandar los SMS.

## 📄 Licencia

Distribuido bajo la Licencia MIT.