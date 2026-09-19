/*
 * Cliente asíncrono de la aplicación.
 *
 * El servidor Java es secuencial: atiende una conexión a la vez. Este archivo existe para mostrar
 * que eso es independiente del cliente. El navegador lanza la petición con fetch y sigue
 * respondiendo a la persona que lo usa mientras espera; cuando la respuesta llega, solo se
 * actualiza el panel de resultado. Nunca se recarga la página.
 */
(function () {
  'use strict';

  const $ = (id) => document.getElementById(id);

  const ui = {
    estado: $('estado'),
    exito: $('exito'),
    exitoMensaje: $('exito-mensaje'),
    exitoDetalle: $('exito-detalle'),
    error: $('error'),
    errorMensaje: $('error-mensaje'),
    errorDetalle: $('error-detalle'),
    crudo: $('crudo-contenido')
  };

  /* ------------------------------------------------------------------ *
   * Panel de resultado                                                  *
   * ------------------------------------------------------------------ */

  function ocultarPaneles() {
    ui.exito.hidden = true;
    ui.error.hidden = true;
  }

  /** Estado "cargando": visible, pero sin bloquear el resto de la interfaz. */
  function mostrarCargando(texto) {
    ocultarPaneles();
    ui.estado.hidden = false;
    ui.estado.textContent = texto;
  }

  function ocultarCargando() {
    ui.estado.hidden = true;
    ui.estado.textContent = '';
  }

  /**
   * Pinta un resultado correcto.
   *
   * Los valores se asignan con textContent y no con innerHTML: lo que devuelve el servidor se
   * muestra como texto, nunca se interpreta como marcado.
   */
  function mostrarExito(mensaje, detalle) {
    ocultarCargando();
    ui.error.hidden = true;
    ui.exitoMensaje.textContent = mensaje;
    ui.exitoDetalle.replaceChildren();
    Object.entries(detalle || {}).forEach(([clave, valor]) => {
      const dt = document.createElement('dt');
      dt.textContent = clave;
      const dd = document.createElement('dd');
      dd.textContent = String(valor);
      ui.exitoDetalle.append(dt, dd);
    });
    ui.exito.hidden = false;
  }

  /** Pinta un error. El detalle es informativo; nunca expone interioridades del servidor. */
  function mostrarError(mensaje, detalle) {
    ocultarCargando();
    ui.exito.hidden = true;
    ui.errorMensaje.textContent = mensaje;
    ui.errorDetalle.textContent = detalle || '';
    ui.error.hidden = false;
  }

  function registrarCrudo(metodo, url, respuesta, cuerpo, milisegundos) {
    const lineas = [`${metodo} ${url}`];
    if (respuesta) {
      lineas.push(`HTTP ${respuesta.status} ${respuesta.statusText}`);
      lineas.push(`Content-Type: ${respuesta.headers.get('Content-Type') || '(sin declarar)'}`);
      const longitud = respuesta.headers.get('Content-Length');
      if (longitud) {
        lineas.push(`Content-Length: ${longitud}`);
      }
    } else {
      lineas.push('(la respuesta nunca llegó)');
    }
    lineas.push(`tiempo total: ${milisegundos} ms`, '', cuerpo);
    ui.crudo.textContent = lineas.join('\n');
  }

  /* ------------------------------------------------------------------ *
   * Llamada a los servicios                                             *
   * ------------------------------------------------------------------ */

  /**
   * Hace la petición y deja el panel de resultado actualizado.
   *
   * Distingue tres desenlaces, que es justo lo que pide el laboratorio:
   *   1. Falla de red: el fetch se rechaza y nunca hubo respuesta HTTP.
   *   2. Respuesta HTTP de error (status >= 400): sí hubo respuesta, y trae su propio mensaje.
   *   3. Respuesta correcta: se interpreta el JSON y se actualiza la interfaz.
   *
   * @param {string} url        URL ya construida con el input validado
   * @param {object} opciones   { metodo, textoCargando, alExito }
   */
  async function llamarServicio(url, opciones) {
    const metodo = opciones.metodo || 'GET';
    const inicio = performance.now();
    mostrarCargando(opciones.textoCargando || 'Consultando al servidor…');

    let respuesta;
    try {
      // cache: 'no-store' evita que el navegador responda /time desde su propia caché: la hora
      // tiene que venir del servidor en cada llamada.
      respuesta = await fetch(url, { method: metodo, cache: 'no-store' });
    } catch (fallaDeRed) {
      const transcurrido = Math.round(performance.now() - inicio);
      registrarCrudo(metodo, url, null, String(fallaDeRed), transcurrido);
      mostrarError(
        'No se pudo contactar al servidor.',
        'La petición ni siquiera llegó a obtener una respuesta HTTP. Revisa que el servidor ' +
        'siga en ejecución y que la red permita alcanzar su puerto.'
      );
      return null;
    }

    // El status se revisa ANTES de interpretar el cuerpo: un 404 también trae cuerpo, pero no
    // contiene los datos que la interfaz espera.
    const texto = await respuesta.text();
    const transcurrido = Math.round(performance.now() - inicio);
    const tipo = respuesta.headers.get('Content-Type') || '';
    const esJson = tipo.includes('application/json');
    registrarCrudo(metodo, url, respuesta, texto, transcurrido);

    let datos = null;
    if (esJson) {
      try {
        datos = JSON.parse(texto);
      } catch (jsonInvalido) {
        mostrarError('El servidor devolvió una respuesta que no se pudo interpretar.',
          `Se esperaba JSON en ${url}.`);
        return null;
      }
    }

    if (!respuesta.ok) {
      const mensaje = datos && datos.message
        ? datos.message
        : `El servidor respondió ${respuesta.status} ${respuesta.statusText}.`;
      mostrarError(mensaje,
        `${metodo} ${url} → HTTP ${respuesta.status} (${transcurrido} ms)`);
      return null;
    }

    if (typeof opciones.alExito === 'function') {
      opciones.alExito(datos, { transcurrido, url, respuesta });
    }
    return datos;
  }

  /** Deshabilita solo el botón que disparó la acción, para no bloquear el resto de la interfaz. */
  async function conBotonOcupado(boton, accion) {
    if (!boton) {
      return accion();
    }
    const original = boton.textContent;
    boton.disabled = true;
    boton.textContent = 'Esperando…';
    try {
      return await accion();
    } finally {
      boton.disabled = false;
      boton.textContent = original;
    }
  }

  /* ------------------------------------------------------------------ *
   * Formularios                                                         *
   * ------------------------------------------------------------------ */

  // Saludo -------------------------------------------------------------
  $('form-saludo').addEventListener('submit', (evento) => {
    // Sin esto el navegador enviaría el formulario y recargaría la página completa.
    evento.preventDefault();

    const nombre = $('input-nombre').value.trim();
    if (nombre === '') {
      mostrarError('Escribe un nombre antes de pedir el saludo.',
        'La validación ocurrió en el navegador: no se envió ninguna petición.');
      return;
    }

    // La URL se arma con el valor ya validado y codificado: un nombre con espacios, acentos o
    // un & no puede alterar la estructura de la query.
    const url = `/hello?name=${encodeURIComponent(nombre)}`;
    conBotonOcupado(evento.submitter, () => llamarServicio(url, {
      textoCargando: `Pidiendo el saludo para "${nombre}"…`,
      alExito: (datos, meta) => mostrarExito(datos.message, {
        'Servicio': datos.service,
        'Nombre recibido': datos.name,
        'Hora del servidor': datos.timestamp,
        'Tiempo de respuesta': `${meta.transcurrido} ms`
      })
    }));
  });

  // Cuadrado -----------------------------------------------------------
  $('form-cuadrado').addEventListener('submit', (evento) => {
    evento.preventDefault();

    const crudo = $('input-numero').value.trim();
    if (crudo === '') {
      mostrarError('Escribe un número antes de calcular el cuadrado.',
        'La validación ocurrió en el navegador: no se envió ninguna petición.');
      return;
    }
    if (!Number.isFinite(Number(crudo))) {
      mostrarError(`"${crudo}" no es un número válido.`,
        'La validación ocurrió en el navegador: no se envió ninguna petición.');
      return;
    }

    const url = `/square?number=${encodeURIComponent(crudo)}`;
    conBotonOcupado(evento.submitter, () => llamarServicio(url, {
      textoCargando: `Calculando el cuadrado de ${crudo}…`,
      alExito: (datos, meta) => mostrarExito(
        `El cuadrado de ${datos.input} es ${datos.result}.`, {
          'Servicio': datos.service,
          'Entrada': datos.input,
          'Resultado': datos.result,
          'Tiempo de respuesta': `${meta.transcurrido} ms`
        })
    }));
  });

  // Hora del servidor y salud ------------------------------------------
  $('form-hora').addEventListener('submit', (evento) => {
    evento.preventDefault();
    conBotonOcupado(evento.submitter, () => llamarServicio('/time', {
      textoCargando: 'Consultando la hora del servidor…',
      alExito: (datos, meta) => mostrarExito(
        `El servidor dice que son las ${datos.iso8601}.`, {
          'Zona horaria del servidor': datos.zone,
          'Epoch (ms)': datos.epochMillis,
          'Hora de este navegador': new Date().toISOString(),
          'Tiempo de respuesta': `${meta.transcurrido} ms`
        })
    }));
  });

  $('boton-salud').addEventListener('click', (evento) => {
    conBotonOcupado(evento.currentTarget, () => llamarServicio('/health', {
      textoCargando: 'Comprobando el estado del servidor…',
      alExito: (datos, meta) => mostrarExito('El servidor responde correctamente.', {
        'Estado': datos.status,
        'Tiempo en línea': `${datos.uptimeSeconds} s`,
        'Tiempo de respuesta': `${meta.transcurrido} ms`
      })
    }));
  });

  // Servicio lento -----------------------------------------------------
  $('form-lento').addEventListener('submit', (evento) => {
    evento.preventDefault();

    const segundos = $('input-segundos').value.trim();
    const url = `/slow?seconds=${encodeURIComponent(segundos)}`;
    conBotonOcupado(evento.submitter, () => llamarServicio(url, {
      textoCargando: `El servidor estará ocupado ${segundos} s. La página sigue respondiendo…`,
      alExito: (datos, meta) => mostrarExito(
        `El servidor estuvo ocupado ${datos.seconds} s.`, {
          'Inicio en el servidor': datos.startedAt,
          'Fin en el servidor': datos.finishedAt,
          'Tiempo total visto por el navegador': `${meta.transcurrido} ms`
        })
    }));
  });

  /* ------------------------------------------------------------------ *
   * Errores controlados                                                 *
   * ------------------------------------------------------------------ */

  const PRUEBAS = {
    // Un archivo que no está en el directorio público.
    '404': { url: '/no-existe.html', metodo: 'GET', texto: 'Pidiendo un archivo inexistente…' },
    // El servicio de saludo sin su parámetro obligatorio.
    '400': { url: '/hello', metodo: 'GET', texto: 'Pidiendo un saludo sin nombre…' },
    // Un valor que no se puede convertir a número.
    '400-numero': { url: '/square?number=hola', metodo: 'GET', texto: 'Pidiendo un cuadrado imposible…' },
    // La barra va percent-encoded (%2f) a propósito. Escrita como "/../pom.xml" —o incluso como
    // "/%2e%2e/pom.xml"— el propio navegador resolvería los dos puntos antes de enviar la
    // petición y el servidor nunca llegaría a ver el intento. Con %2f el navegador la manda tal
    // cual y el rechazo lo hace el servidor, que es lo que se quiere demostrar.
    '403': { url: '/..%2fpom.xml', metodo: 'GET', texto: 'Intentando salir del directorio público…' },
    // Un método que esta aplicación no soporta.
    '405': { url: '/index.html', metodo: 'POST', texto: 'Enviando un método no soportado…' }
  };

  document.querySelectorAll('[data-prueba]').forEach((boton) => {
    boton.addEventListener('click', (evento) => {
      const prueba = PRUEBAS[evento.currentTarget.dataset.prueba];
      conBotonOcupado(evento.currentTarget, () => llamarServicio(prueba.url, {
        metodo: prueba.metodo,
        textoCargando: prueba.texto,
        alExito: (datos) => mostrarExito(
          'La petición no falló: revisa la respuesta cruda.', datos || {})
      }));
    });
  });
})();
