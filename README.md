# AutoPaw — aplicación móvil Android

AutoPaw es una aplicación Android para apoyar el monitoreo de movimiento y proximidad cerca de un vehículo. No identifica especies. La comunicación entre teléfonos funciona mediante Bluetooth clásico RFCOMM y un protocolo cifrado propio de la aplicación.

## Funciones principales

- Desarrollo nativo en Android Studio con Kotlin.
- Bluetooth clásico RFCOMM con dos roles: **Nodo sensor** y **Teléfono central**.
- Registro e inicio de sesión local.
- Contraseñas con PBKDF2-HMAC-SHA256 y sal aleatoria.
- Bloqueo temporal tras intentos fallidos de acceso.
- Persistencia local con SQLite mediante Room.
- Protocolo AutoPaw v2 con AES-GCM, HMAC-SHA256 y número de secuencia anti-repetición.
- Clave compartida protegida localmente mediante Android Keystore.
- Historial de lecturas y eventos.
- Motor de detección con estabilización, confirmación temporal y lectura inválida por timeout.
- Pruebas unitarias e instrumentadas incluidas en el proyecto.

## Comunicación entre dos teléfonos

1. Instalar AutoPaw en ambos teléfonos y activar Bluetooth.
2. Emparejar los teléfonos desde Android.
3. En un teléfono seleccionar **Equipo → Conexión AutoPaw → Nodo sensor**.
4. En el segundo seleccionar **Teléfono central**.
5. Introducir la misma clave compartida en ambos equipos.
6. El Nodo sensor envía lecturas cifradas y el central las valida, muestra y registra.

## Seguridad

La aplicación implementa controles técnicos de seguridad para la práctica académica: cifrado AES-GCM, HMAC-SHA256, secuencia anti-repetición, validación estricta de mensajes, Android Keystore y almacenamiento de contraseñas mediante PBKDF2. Estos controles no equivalen a una certificación ISO 27400 ni a una auditoría formal.

## Limitación del prototipo

El sensor de proximidad integrado de un teléfono Android normalmente informa cerca/lejos y no reemplaza a un HC-SR04 con medición continua de 2–400 cm. El proyecto no debe presentarse como un sistema de reconocimiento de gatos.

## Proyecto académico

Asignatura: Aplicaciones Móviles para IoT — INACAP.
