#!/data/data/com.termux/files/usr/bin/bash
#
# start_downloader.sh
#
# Script UNICO que voce (ou o Termux:Boot) precisa rodar. Ele:
#   1. Segura o processo vivo (wake-lock) automaticamente
#   2. Carrega as variaveis do .env
#   3. Roda o downloader_termux.py em loop, reiniciando sozinho se
#      ele cair por qualquer motivo (rede caiu, erro inesperado, etc)
#   4. Registra tudo em downloader_termux.log, que o painel web pode
#      ler para mostrar o status "rodando / parado" (heartbeat)
#
# Uso manual (so precisa rodar 1 vez para testar):
#   bash start_downloader.sh
#
# Uso automatico: ver instrucoes no final deste arquivo sobre
# Termux:Boot, para isso rodar sozinho toda vez que o celular ligar.

set -u

PROJECT_DIR="$HOME/streamflixvip-zeabur/movie-fetcher"
LOG_FILE="$PROJECT_DIR/downloader_termux.log"
HEARTBEAT_FILE="$PROJECT_DIR/.heartbeat"

cd "$PROJECT_DIR" || { echo "ERRO: pasta do projeto nao encontrada em $PROJECT_DIR"; exit 1; }

# Mantem o processo vivo mesmo com a tela apagada
termux-wake-lock 2>/dev/null || true

# Carrega as variaveis do .env
if [ ! -f .env ]; then
    echo "ERRO: arquivo .env nao encontrado em $PROJECT_DIR"
    exit 1
fi
export $(grep -v '^#' .env | xargs)

echo "$(date -Iseconds) [start_downloader] iniciando supervisor" >> "$LOG_FILE"

# Loop de supervisao: se o downloader cair por qualquer motivo,
# espera 10s e reinicia sozinho, sem precisar de intervencao manual.
while true; do
    echo "$(date -Iseconds) >>>>" > "$HEARTBEAT_FILE"
    python3 downloader_termux.py >> "$LOG_FILE" 2>&1

    exit_code=$?
    echo "$(date -Iseconds) [start_downloader] downloader encerrou (codigo $exit_code), reiniciando em 10s..." >> "$LOG_FILE"
    echo "parado" > "$HEARTBEAT_FILE"
    sleep 10
done
