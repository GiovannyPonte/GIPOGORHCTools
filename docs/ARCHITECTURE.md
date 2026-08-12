# Arquitectura de GIPOGO RHC Tools

La aplicación sigue una separación MVVM pragmática sobre Kotlin, Jetpack Compose, coroutines,
Flow y Room. El objetivo de estas fronteras es mantener los cálculos clínicos verificables y
evitar que la interfaz conozca detalles de persistencia.

## Flujo de una operación

1. Un `Screen` de Compose muestra estado y convierte gestos del usuario en eventos.
2. El `ViewModel` valida la entrada, coordina el caso de uso y publica estado observable.
3. Los paquetes `domain` y `ui/validation` normalizan unidades y ejecutan reglas o fórmulas puras.
4. Un repositorio de `data` coordina los DAO de Room y traduce fallos a `DataResult`.
5. La pantalla vuelve a renderizarse al recibir el nuevo estado mediante Flow.

## Responsabilidad de cada paquete

- `core/result`: contrato común para éxitos y errores de datos.
- `data`: preferencias, base Room, entidades, DAO y repositorios. No contiene UI.
- `domain`: modelos, unidades y cálculos clínicos puros; debe poder probarse en JVM.
- `ui/screens`: composición de cada pantalla y manejo de eventos visuales.
- `ui/components`: piezas de Compose reutilizables y sin navegación propia.
- `ui/viewmodel`: estado de pantalla y coordinación entre UI, dominio y repositorios.
- `ui/navigation`: destinos y grafo de navegación.
- `ui/validation` y `ui/interpretation`: validación de formularios y presentación clínica.
- `report` y `reporting`: construcción, renderizado y exportación de documentos PDF.
- `workshopsession`: sesión temporal, recuperación y autoguardado de estudios.

## Reglas para cambios seguros

- Mantener fórmulas y conversiones fuera de composables.
- No acceder a DAO directamente desde una pantalla; usar el repositorio y el ViewModel.
- Conservar un único origen para umbrales, unidades y formatos compartidos.
- Añadir KDoc a límites arquitectónicos o decisiones no evidentes, no comentar línea por línea.
- Añadir pruebas JVM para lógica pura y pruebas instrumentadas para Room, navegación y PDF.
- Versionar `app/schemas/` cada vez que cambie una entidad o migración de Room.
