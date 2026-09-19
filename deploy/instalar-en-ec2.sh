#!/usr/bin/env bash
#
# Instala la aplicacion como servicio administrado en una instancia EC2 con Amazon Linux 2023.
#
# Se ejecuta DENTRO de la instancia, despues de haber copiado el jar y este script:
#
#   scp -i llave.pem target/taller-networking.jar deploy/webapp.service \
#       deploy/instalar-en-ec2.sh ec2-user@<DNS-PUBLICO>:/tmp/
#   ssh -i llave.pem ec2-user@<DNS-PUBLICO>
#   sudo bash /tmp/instalar-en-ec2.sh 8080
#
# El unico argumento opcional es el puerto de la aplicacion (por defecto 8080). Ese mismo puerto
# tiene que estar abierto en el security group de la instancia.

set -euo pipefail

PUERTO="${1:-8080}"
DESTINO=/opt/webapp
JAR=taller-networking.jar
UNIDAD=webapp.service

if [[ "${EUID}" -ne 0 ]]; then
  echo "Ejecutalo con sudo: sudo bash $0 ${PUERTO}" >&2
  exit 1
fi

echo "==> 1/5 Instalando el runtime de Java"
# Amazon Linux 2023 trae dnf; Amazon Linux 2 trae yum. Ambos sirven para Corretto.
if command -v dnf >/dev/null 2>&1; then
  dnf install -y java-21-amazon-corretto-headless
else
  yum install -y java-21-amazon-corretto-headless
fi
java -version

echo "==> 2/5 Creando el usuario de servicio y el directorio de la aplicacion"
# Usuario del sistema, sin shell de acceso: la aplicacion no necesita iniciar sesion.
id -u webapp >/dev/null 2>&1 || useradd --system --shell /sbin/nologin --home-dir "${DESTINO}" webapp
install -d -o webapp -g webapp -m 0755 "${DESTINO}"

echo "==> 3/5 Copiando el artefacto"
install -o webapp -g webapp -m 0644 "/tmp/${JAR}" "${DESTINO}/${JAR}"

echo "==> 4/5 Instalando la unidad de systemd con PORT=${PUERTO}"
install -m 0644 "/tmp/${UNIDAD}" "/etc/systemd/system/${UNIDAD}"
# Se ajusta el puerto declarado en la unidad al que se paso por argumento.
sed -i "s/^Environment=PORT=.*/Environment=PORT=${PUERTO}/" "/etc/systemd/system/${UNIDAD}"
systemctl daemon-reload
systemctl enable --now webapp

echo "==> 5/5 Verificando desde dentro de la instancia"
# Se le da un momento al proceso para abrir el socket antes de consultarlo.
for intento in 1 2 3 4 5; do
  if curl -fsS "http://localhost:${PUERTO}/health" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

echo
systemctl --no-pager --lines=5 status webapp || true
echo
echo "Respuesta de /health desde la propia instancia:"
curl -fsS "http://localhost:${PUERTO}/health"
echo
echo
echo "Listo. Prueba desde tu navegador con la IP o el DNS publico de la instancia:"
echo "    http://<IP-PUBLICA>:${PUERTO}/"
echo "Logs en vivo:   journalctl -u webapp -f"
