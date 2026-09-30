# ShareApp

**Comparte archivos y texto directamente entre Android y ordenador, dentro de tu red local.** ShareApp descubre otros dispositivos con la aplicación abierta y transfiere el contenido por Wi-Fi, sin cuentas ni servidores en la nube.

> Proyecto Kotlin Multiplatform con interfaz Compose para Android y escritorio (JVM).

## Características principales

- Descubrimiento y anuncio de dispositivos mediante DNS-SD: NSD en Android y JmDNS en escritorio.
- Envío de archivos de hasta **25 MiB** y texto de hasta **100.000 caracteres**.
- Envío del texto actual del portapapeles y copia automática del texto recibido al portapapeles.
- Confirmación de las transferencias entrantes antes de recibir su contenido, activada de forma predeterminada.
- Ajustes para cambiar el nombre del dispositivo, elegir la carpeta de destino y, opcionalmente, aceptar transferencias automáticamente.
- Historial de transferencias; al seleccionar un archivo guardado, ShareApp lo abre o muestra en el explorador del sistema.
- Guardado predeterminado en `Descargas/ShareApp`.
- Comunicación directa por la red local, sin servicio de intermediación en la nube.

## Tecnologías utilizadas

| Área | Tecnología |
| --- | --- |
| Lenguaje | Kotlin 2.4.20 |
| Aplicación multiplataforma | Kotlin Multiplatform |
| Interfaz | Compose Multiplatform y Material 3 |
| Android | Android Gradle Plugin 9.1.1, Android SDK 37, mínimo Android 7.0 (API 24) |
| Escritorio | JVM y Compose Desktop |
| Servidor HTTP integrado | Ktor Server con motor CIO (puerto `47852`) |
| Cliente HTTP | Ktor Client con motor CIO |
| Descubrimiento en Android | Android Network Service Discovery (NSD) |
| Descubrimiento en escritorio | JmDNS / DNS-SD |
| Asincronía y estado | Kotlin Coroutines y `StateFlow` |
| Construcción | Gradle Wrapper 9.5.1; JDK 21 |

## Estructura del proyecto

```text
ShareApp/
├── androidApp/                  # Entrada, manifiesto y recursos de Android
├── desktopApp/                  # Entrada y empaquetado de escritorio
├── shared/
│   └── src/
│       ├── commonMain/          # Interfaz Compose, servidor, cliente y modelos
│       ├── androidMain/         # NSD, portapapeles y archivos en Android
│       ├── jvmMain/             # JmDNS, portapapeles y archivos en escritorio
│       ├── commonTest/          # Pruebas comunes
│       ├── androidHostTest/     # Pruebas de la variante Android
│       └── jvmTest/             # Pruebas JVM
├── gradle/                      # Wrapper, versiones y configuración de JDK
├── build.gradle.kts             # Plugins comunes
└── settings.gradle.kts          # Módulos incluidos
```

## Requisitos previos e instalación

1. Instala **Android Studio** y el **JDK 21**. El proyecto solicita JDK 21 al daemon de Gradle.
2. En Android Studio, instala **Android SDK Platform 37** desde **Tools → SDK Manager → SDK Platforms** y **Android SDK Build-Tools 36.0.0** desde **SDK Tools**.
3. Clona el repositorio y abre en Android Studio la carpeta raíz, la que contiene `settings.gradle.kts`:

   ```bash
   git clone <URL_DEL_REPOSITORIO>
   cd ShareApp
   ```

4. Espera a que termine **Gradle Sync**. Si Android Studio pregunta qué JDK usar para Gradle, selecciona JDK 21.
5. Para compilar el APK de depuración en Windows, abre PowerShell en la raíz del proyecto:

   ```powershell
   .\gradlew.bat :androidApp:assembleDebug
   ```

   El APK se genera en `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

6. Para ejecutar la aplicación de escritorio:

   ```powershell
   .\gradlew.bat :desktopApp:run
   ```

7. Para ejecutar Android, inicia un emulador o conecta un móvil con depuración USB y selecciónalo en Android Studio. También puedes instalar el APK generado en el dispositivo.

> La primera compilación descarga Gradle y las dependencias. Para que dos dispositivos se descubran, ambos deben tener ShareApp abierta y estar conectados a la misma red local. Si el cortafuegos del ordenador lo solicita, permite el acceso en la red privada.

## Variables de entorno

ShareApp no necesita variables de entorno ni credenciales. Android Studio configura la ruta local del SDK en `local.properties`; ese archivo es propio de cada equipo y no se debe compartir ni añadir al repositorio.

## Ejemplo de uso y API

### Enviar contenido

1. Abre ShareApp en el móvil y en el ordenador; conecta ambos a la misma Wi-Fi o red local.
2. En **Dispositivos**, inicia la búsqueda y selecciona el equipo de destino.
3. Elige enviar un archivo, escribir texto o compartir el texto actual del portapapeles.
4. Acepta la solicitud en el dispositivo receptor. El archivo recibido se guarda en `Descargas/ShareApp` o en la carpeta que hayas seleccionado.

El servidor HTTP local escucha en `0.0.0.0:47852`. Las rutas disponibles son:

| Método | Ruta | Función |
| --- | --- | --- |
| `GET` | `/health` | Comprueba que el dispositivo ejecuta ShareApp. Responde `ShareApp disponible`. |
| `POST` | `/transfer/text/request?id=…&sender=…&characters=…` | Solicita permiso para enviar texto. Devuelve `202` con estado `WAITING` o `ACCEPTED`. |
| `GET` | `/transfer/text/status?id=…` | Consulta el estado: `WAITING`, `ACCEPTED`, `REJECTED`, `COMPLETED` o `NOT_FOUND`. |
| `POST` | `/transfer/text/decision?id=…&accepted=true\|false` | Acepta o rechaza una solicitud pendiente. |
| `POST` | `/transfer/text/content?id=…` | Envía el texto como cuerpo `text/plain`, después de su aceptación. |
| `POST` | `/transfer/file/request?id=…&sender=…&name=…&size=…` | Solicita permiso para transferir un archivo (máximo 25 MiB). |
| `POST` | `/transfer/file/decision?id=…&accepted=true\|false` | Acepta o rechaza una solicitud de archivo pendiente. |
| `POST` | `/transfer/file/content?id=…` | Envía los bytes como `application/octet-stream`, después de la aceptación. |

Las rutas de decisión y contenido responden a solicitudes que llegan al dispositivo receptor. El tráfico de la versión actual usa HTTP dentro de la red local; no se configura cifrado TLS ni autenticación de usuario. Mantén activada la aprobación de transferencias y usa una red de confianza.

## Compilación adicional

```powershell
# Compilar escritorio
.\gradlew.bat :desktopApp:compileKotlin

# Preparar la distribución local de escritorio
.\gradlew.bat :desktopApp:createDistributable
```
