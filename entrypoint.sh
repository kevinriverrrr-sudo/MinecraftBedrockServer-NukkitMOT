#!/bin/bash
# Entrypoint for Wispbyte/Pterodactyl hosting panels
# This script is called by the panel's container entrypoint
# It ensures Java 17+ is available before running the server

set -e

cd "$(dirname "$0")"

echo "[Entrypoint] Starting Minecraft Bedrock Server..."

# ---- Ensure Java 17+ is available ----
check_java() {
    if command -v java &> /dev/null; then
        JAVA_VER=$(java -version 2>&1 | head -1 | grep -oP '"\K[^"]+' | cut -d. -f1)
        if [ "$JAVA_VER" -ge 17 ] 2>/dev/null; then
            return 0
        fi
    fi
    return 1
}

install_java() {
    echo "[Entrypoint] Java 17+ not found, installing..."
    
    ARCH=$(uname -m)
    if [ "$ARCH" = "x86_64" ]; then
        JDK_URL="https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
    elif [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then
        JDK_URL="https://api.adoptium.net/v3/binary/latest/17/ga/linux/aarch64/jdk/hotspot/normal/eclipse"
    else
        echo "[Entrypoint] ERROR: Unsupported architecture: $ARCH"
        exit 1
    fi
    
    INSTALL_DIR="$HOME/java"
    mkdir -p "$INSTALL_DIR"
    
    echo "[Entrypoint] Downloading JDK 17 for $ARCH..."
    curl -fsSL "$JDK_URL" -o /tmp/jdk17.tar.gz || {
        echo "[Entrypoint] ERROR: Failed to download JDK 17"
        exit 1
    }
    
    echo "[Entrypoint] Extracting JDK 17..."
    tar -xzf /tmp/jdk17.tar.gz -C "$INSTALL_DIR"
    rm -f /tmp/jdk17.tar.gz
    
    # Find and export Java
    JDK_DIR=$(ls -d "$INSTALL_DIR"/jdk-17* 2>/dev/null | head -1)
    if [ -z "$JDK_DIR" ]; then
        JDK_DIR=$(ls -d "$INSTALL_DIR"/jdk*17* 2>/dev/null | head -1)
    fi
    
    if [ -n "$JDK_DIR" ] && [ -x "$JDK_DIR/bin/java" ]; then
        export PATH="$JDK_DIR/bin:$PATH"
        export JAVA_HOME="$JDK_DIR"
        echo "[Entrypoint] JDK 17 installed at $JDK_DIR"
    else
        echo "[Entrypoint] ERROR: JDK installation failed"
        exit 1
    fi
}

# Check and install Java if needed
if ! check_java; then
    install_java
fi

echo "[Entrypoint] Java version:"
java -version 2>&1 | head -1

# ---- Create plugins directory if missing ----
mkdir -p plugins

# ---- Run the server ----
echo "[Entrypoint] Launching Nukkit-MOT server..."
exec java \
    -Dterminal.jline=false \
    -Dterminal.ansi=true \
    -Dlog4j.configurationFile=./log4j2.xml \
    -Xms512m -Xmx1024m \
    -jar Nukkit-MOT.jar
