# Protocolo de Reporte Profesional para Cateterismo Derecho

Fecha de revisión: 10 de abril de 2026

## Propósito

Este documento define un protocolo de reporte para cateterismo cardiaco derecho orientado a salida en PDF dentro de `GIPOGORHCTools`.

Objetivos:

- producir reportes claros, clínicamente útiles y auditables;
- mantener una estructura consistente entre operadores y entre estudios;
- separar datos medidos, cálculos, interpretación y plan;
- ofrecer dos formatos:
  - `Completo`: para expediente, segunda lectura y discusión multidisciplinaria;
  - `Compacto`: para consulta rápida, pase de visita o referencia externa.

## Base clínica y normativa

### Fuentes primarias revisadas

1. ESC/ERS 2022 Pulmonary Hypertension Guidelines
   <https://academic.oup.com/eurheartj/article/43/38/3618/6673929>

2. ACC key points sobre guía práctica de evaluación hemodinámica por cateterismo derecho en insuficiencia cardiaca, basado en `JACC Heart Failure 2024;12:1141-1156`
   <https://www.acc.org/Latest-in-Cardiology/ten-points-to-remember/2024/07/02/12/51/practical-guidance-for>

3. European Heart Journal 2025, revisión de estado del arte: `Right heart catheterization in heart failure: indications, interpretation, and pitfalls`
   <https://academic.oup.com/eurheartj/article/46/34/3354/8163687>

4. ACC/AHA/SCAI 2014 Structured Reporting Initiative para laboratorio de cateterismo
   <https://www.acc.org/About-ACC/Press-Releases/2014/03/29/09/53/HP-Statement-Structured-Reporting>

5. IAC Standards & Guidelines for Cardiovascular Catheterization Accreditation, edición publicada en abril de 2025
   <https://intersocietal.org/wp-content/uploads/2025/04/IACCardiovascularCatheterizationStandards2025.pdf>

### Principios extraídos de la literatura

- El reporte debe ser estructurado, claro, conciso y suficientemente completo para responder la pregunta clínica.
- Debe distinguir datos medidos de datos derivados y de la interpretación clínica.
- Debe registrar calidad técnica mínima del estudio y posibles fuentes de error.
- La conclusión final debe resolver la indicación del estudio o indicar qué falta para resolverla.
- Para hemodinámica, la adquisición debe favorecer estandarización:
  - transductor correctamente nivelado;
  - presiones reportadas al final de la espiración;
  - método de gasto cardiaco explicitado;
  - cuando se use Fick, especificar si `VO2` fue medido o estimado.
- En sospecha de hipertensión pulmonar, el lenguaje interpretativo debe ser congruente con definiciones hemodinámicas vigentes.

## Filosofía editorial propuesta para la app

Esto es una propuesta de implementación, inferida a partir de las guías y adaptada a los datos que la app ya maneja.

- El PDF no debe ser un “dump” de números.
- Debe empezar con una respuesta clínica breve.
- Los números deben ir agrupados por dominios fisiológicos:
  - presiones;
  - flujo;
  - resistencias;
  - oxigenación y método;
  - interpretación.
- Todo valor importante debe aparecer una sola vez en el cuerpo principal.
- Las tendencias longitudinales deben ir al final y nunca sustituir el resumen del estudio índice.
- Si un dato clave no existe, mostrar `N/D` o `No disponible`, no simularlo.

## Datos mínimos obligatorios

### 1. Identificación

- institución o app emisora;
- paciente: nombre visible o código interno;
- identificador del estudio;
- fecha y hora del estudio;
- fecha y hora del reporte;
- operador responsable;
- versión del reporte si existiera en el futuro.

### 2. Contexto clínico

- indicación del cateterismo;
- escenario clínico:
  - insuficiencia cardiaca;
  - sospecha de hipertensión pulmonar;
  - evaluación pre-trasplante o pre-LVAD;
  - seguimiento terapéutico;
  - otro.
- estado respiratorio al momento del estudio:
  - aire ambiente;
  - oxígeno suplementario;
  - ventilación mecánica;
  - no documentado.

### 3. Calidad técnica y método

- acceso venoso;
- sitio de punción;
- tipo de sedación o ausencia de sedación;
- si hubo fluoroscopia o no;
- método de gasto cardiaco:
  - termodilución;
  - Fick directo;
  - Fick indirecto/estimado;
  - no documentado.
- observaciones de calidad:
  - curvas adecuadas;
  - wedge dudoso;
  - arritmia;
  - regurgitación significativa;
  - limitación por respiración;
  - otro.

### 4. Hemodinámica central

Medidos:

- RAP media;
- presión pulmonar sistólica;
- presión pulmonar diastólica;
- mPAP;
- PAWP o PCWP;
- saturación venosa mixta si existe;
- saturación arterial si existe;
- hemoglobina si se usó Fick.

Derivados:

- gasto cardiaco;
- índice cardiaco;
- PVR;
- SVR;
- gradiente transpulmonar;
- gradiente diastólico pulmonar;
- CPO;
- PAPi;
- cualquier índice longitudinal que se adopte de forma explícita.

### 5. Interpretación estructurada

- estado de llenado derecho;
- estado de llenado izquierdo;
- perfil de flujo:
  - normal;
  - bajo gasto;
  - alto gasto.
- perfil de resistencias pulmonares;
- si hay o no patrón compatible con hipertensión pulmonar;
- si existe congruencia clínica o alguna limitación de interpretación.

### 6. Conclusión y plan

- respuesta corta a la indicación;
- 2 a 5 conclusiones accionables;
- comparación con estudio previo si existe;
- recomendación siguiente:
  - seguimiento;
  - optimización de volumen;
  - ampliar evaluación;
  - repetir estudio bajo otra condición;
  - referencia a unidad especializada.

## Versión 1: Plantilla completa

Uso recomendado:

- expediente formal;
- referencia a centro de hipertensión pulmonar o insuficiencia cardiaca avanzada;
- comités;
- segunda opinión;
- auditoría interna.

### Estructura de páginas

#### Página 1. Resumen ejecutivo

Bloques:

1. Encabezado institucional
- nombre del documento;
- paciente;
- fecha/hora;
- operador;
- motivo del estudio.

2. Caja de conclusión clínica
- 3 a 5 líneas máximo;
- lenguaje diagnóstico prudente;
- si falta calidad técnica, decirlo aquí.

3. Tabla de hallazgos clave
- RAP;
- mPAP;
- PAWP/PCWP;
- CO;
- CI;
- PVR;
- SVR;
- SvO2;
- CPO o PAPi si están disponibles.

4. Semáforo fisiológico
- congestión derecha;
- congestión izquierda;
- perfusión/flujo;
- componente vascular pulmonar.

#### Página 2. Técnica y calidad

- indicación clínica;
- contexto respiratorio;
- acceso;
- sedación;
- método de medición de CO;
- fluoroscopia/contraste si aplica;
- incidencias;
- limitaciones técnicas.

#### Página 3. Hemodinámica detallada

Tres tablas separadas:

1. Presiones
- RAP
- PAP sistólica
- PAP diastólica
- mPAP
- PAWP/PCWP

2. Flujo y transporte
- CO
- CI
- SvO2
- SaO2
- hemoglobina
- método de CO

3. Resistencias y rendimiento
- PVR
- SVR
- TPG
- DPG
- CPO
- PAPi

Regla editorial:

- columna `valor`;
- columna `unidad`;
- columna `comentario breve` opcional.

#### Página 4. Interpretación clínica

Subsecciones:

- perfil hemodinámico;
- clasificación hemodinámica sugerida;
- correlación con sospecha clínica;
- comparación con estudio previo.

Texto breve, no ensayo libre. Preferir párrafos cortos o bullets clínicos.

#### Página 5. Tendencias y comparativo

Si existen estudios previos:

- línea de tiempo;
- mini tablas delta:
  - RAP;
  - mPAP;
  - PAWP;
  - CI;
  - PVR;
  - CPO.
- gráficos longitudinales al final.

Si no existen estudios previos, omitir esta página.

#### Página final. Plan

- síntesis final;
- recomendaciones;
- firma;
- fecha/hora de validación.

### Texto modelo para la sección de conclusión

```text
Cateterismo derecho técnicamente interpretable. Se documentan presiones de llenado derechas elevadas, presión de enclavamiento elevada y gasto cardiaco reducido, compatibles con perfil congestivo de bajo flujo. La resistencia vascular pulmonar se encuentra aumentada y requiere correlación con el contexto clínico y terapéutico. Hallazgos útiles para ajuste de tratamiento y seguimiento invasivo según evolución.
```

## Versión 2: Plantilla corta y compacta

Uso recomendado:

- pase de visita;
- interconsulta;
- exportación rápida al paciente o médico referente;
- revisión en móvil;
- anexar al alta cuando ya existe una nota extensa en el expediente.

### Objetivo

Responder en una sola página, idealmente A4 o carta, estas preguntas:

1. ¿Por qué se hizo el estudio?
2. ¿Qué mostró?
3. ¿Qué hago con este resultado?

### Estructura

#### Encabezado compacto

- Cateterismo derecho
- paciente
- fecha/hora
- operador
- indicación

#### Franja de resumen clínico

Una sola frase, 180 a 240 caracteres.

Ejemplo:

```text
Hemodinámica compatible con congestión biventricular y gasto cardiaco deprimido; PVR elevada. Resultado útil para ajuste de diurético/inotrópico y reevaluación especializada.
```

#### Tabla 2 x 4 de valores clave

- RAP
- mPAP
- PAWP/PCWP
- CO
- CI
- PVR
- SvO2
- CPO o PAPi

#### Mini interpretación

Cuatro líneas fijas:

- Llenado derecho: normal/elevado
- Llenado izquierdo: normal/elevado
- Flujo: normal/bajo/alto
- Resistencia pulmonar: normal/elevada/no concluyente

#### Plan

Máximo tres bullets:

- comparación con previo;
- sugerencia operativa;
- limitación si existe.

### Reglas de la versión compacta

- una página;
- sin tablas largas;
- sin texto narrativo extenso;
- sin repetir unidades en exceso;
- sin gráficas salvo que reemplacen comparativos complejos.

## Diseño PDF propuesto

Esto es una recomendación editorial, no una cita textual de las guías.

### Formato general

- tamaño: A4 vertical;
- márgenes: 24 a 28 pt;
- tipografía:
  - títulos: sans serif sobria;
  - cuerpo: sans serif altamente legible;
- tamaño:
  - título 17 a 20 pt;
  - subtítulo 11 a 12 pt;
  - cuerpo 10.5 a 11.5 pt;
  - tablas 10 a 11 pt.

### Paleta

- fondo blanco;
- encabezados azul petróleo o grafito;
- texto principal casi negro;
- secundarios gris cálido;
- color de énfasis solo para alertas:
  - verde para normal;
  - ámbar para límite;
  - rojo para alterado.

### Jerarquía visual

- conclusión clínica arriba;
- datos clave en tarjeta o grid;
- detalle debajo;
- tendencias al final.

### Tablas

- no usar bordes pesados;
- usar divisores finos;
- alinear números a la derecha;
- mostrar unidad en columna separada o en encabezado;
- evitar celdas con más de dos líneas.

### Semántica visual

- `Medido` y `Derivado` deben verse distintos.
- Cuando un valor sea no disponible, usar `N/D`, nunca cero.
- Toda alerta visual debe ser secundaria al valor numérico, no sustituirlo.

## Mapeo conservador a datos actuales de la app

Campos ya visibles o plausibles con el modelo actual:

- RAP
- mPAP
- PAWP/PCWP
- CO
- CI
- PVR
- SVR
- CPO
- PAPi
- método de CO
- nombre del paciente o código interno
- fecha del estudio
- comparativo longitudinal básico

Campos que probablemente requieran ampliación de modelo antes de prometerlos en PDF:

- SvO2
- SaO2
- hemoglobina contextual para Fick
- VO2 medido vs estimado
- calidad de wedge
- acceso, sedación y complicaciones
- operador responsable
- estado respiratorio y soporte de oxígeno

Regla conservadora:

- no incluir en la maqueta final nada que la app no pueda poblar de manera confiable;
- si se decide mostrar un bloque “técnica”, hoy debe ser opcional y ocultarse si está incompleto.

## Recomendación de implementación por fases

### Fase A

Implementar primero la `Versión compacta` para estudio individual.

Razón:

- menor riesgo;
- mayor utilidad clínica inmediata;
- usa campos que la app ya tiene;
- cabe bien en el generador PDF actual.

### Fase B

Expandir a `Versión completa` con:

- bloque técnico;
- interpretación estructurada;
- comparativo con estudio previo;
- firma/responsable;
- limitaciones del estudio.

### Fase C

Añadir longitudinal completo con deltas y tendencias pulidas.

## Criterios de calidad antes de liberar

- el resumen clínico responde la indicación;
- no hay contradicciones entre tablas y conclusión;
- los valores medidos y derivados están claramente diferenciados;
- si un valor crítico falta, el reporte lo explicita;
- el PDF de una página compacta sigue siendo legible en móvil;
- el PDF completo puede imprimirse sin cortes, solapes ni texto truncado;
- si existe estudio previo, la comparación usa la misma unidad en ambas mediciones.

## Veredicto práctico

La mejor salida profesional para esta app no es un PDF “bonito” solamente, sino un PDF con tres capas:

1. una respuesta clínica breve;
2. una tabla hemodinámica limpia;
3. una interpretación prudente y accionable.

Si hubiera que escoger hoy una sola implementación inicial, la mejor opción es:

- `PDF compacto de una página` para estudio individual;
- `PDF completo multipágina` como exportación avanzada opcional.
