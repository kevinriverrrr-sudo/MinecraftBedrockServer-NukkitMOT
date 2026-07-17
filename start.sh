#!/bin/bash
# Minecraft Bedrock Server - Start Script
# Nukkit-MOT + Custom Plugins + GeyserMC
# Auto-installs Java 17 if not found

set -e

cd "$(dirname "$0")"

echo "============================================"
echo "  Minecraft Bedrock Server"
echo "  Core: Nukkit-MOT"
echo "  With 7 custom plugins"
echo "  + GeyserMC for cross-platform"
echo "============================================"

# ---- Java Auto-Install ----
JAVA_CMD=""

# Try to find Java
if command -v java &> /dev/null; then
    JAVA_CMD="java"
    JAVA_VER=$(java -version 2>&1 | head -1 | grep -oP '"\K[^"]+' | cut -d. -f1)
    echo "[Java] Found java version: $JAVA_VER"
    # Check if version is 17+
    if [ "$JAVA_VER" -lt 17 ] 2>/dev/null; then
        echo "[Java] WARNING: Java $JAVA_VER found, but Nukkit-MOT requires Java 17+"
        echo "[Java] Will try to install Java 17..."
        JAVA_CMD=""
    fi
fi

# If Java not found or too old, install it
if [ -z "$JAVA_CMD" ]; then
    # Check for manually installed JDK
    if [ -x "/opt/java/jdk-17/bin/java" ]; then
        JAVA_CMD="/opt/java/jdk-17/bin/java"
        echo "[Java] Using manually installed JDK 17 at /opt/java/jdk-17"
    elif [ -x "$HOME/jdk-17/bin/java" ]; then
        JAVA_CMD="$HOME/jdk-17/bin/java"
        echo "[Java] Using JDK 17 at $HOME/jdk-17"
    elif [ -x "./jdk-17/bin/java" ]; then
        JAVA_CMD="./jdk-17/bin/java"
        echo "[Java] Using JDK 17 at ./jdk-17"
    else
        echo "[Java] Java 17+ not found. Installing Adoptium JDK 17..."
        
        # Detect architecture
        ARCH=$(uname -m)
        if [ "$ARCH" = "x86_64" ]; then
            JDK_URL="https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
        elif [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then
            JDK_URL="https://api.adoptium.net/v3/binary/latest/17/ga/linux/aarch64/jdk/hotspot/normal/eclipse"
        else
            echo "[Java] ERROR: Unsupported architecture: $ARCH"
            echo "[Java] Please install Java 17 manually"
            exit 1
        fi
        
        echo "[Java] Downloading JDK 17 for $ARCH..."
        mkdir -p /opt/java 2>/dev/null || mkdir -p "$HOME/java"
        
        INSTALL_DIR="/opt/java"
        if [ ! -w "/opt/java" ] 2>/dev/null; then
            INSTALL_DIR="$HOME/java"
        fi
        
        curl -fsSL "$JDK_URL" -o /tmp/jdk17.tar.gz
        if [ $? -ne 0 ]; then
            echo "[Java] ERROR: Failed to download JDK 17"
            echo "[Java] Please install Java 17 manually and ensure 'java' is in PATH"
            exit 1
        fi
        
        echo "[Java] Extracting JDK 17..."
        tar -xzf /tmp/jdk17.tar.gz -C "$INSTALL_DIR"
        rm -f /tmp/jdk17.tar.gz
        
        # Find the extracted directory
        JDK_DIR=$(ls -d "$INSTALL_DIR"/jdk-17* 2>/dev/null | head -1)
        if [ -z "$JDK_DIR" ]; then
            JDK_DIR=$(ls -d "$INSTALL_DIR"/jdk*17* 2>/dev/null | head -1)
        fi
        
        if [ -n "$JDK_DIR" ] && [ -x "$JDK_DIR/bin/java" ]; then
            # Create a symlink for convenience
            ln -sf "$JDK_DIR" "$INSTALL_DIR/jdk-17" 2>/dev/null || true
            JAVA_CMD="$JDK_DIR/bin/java"
            echo "[Java] JDK 17 installed at $JDK_DIR"
        else
            echo "[Java] ERROR: Installation completed but java binary not found"
            echo "[Java] Contents of $INSTALL_DIR:"
            ls -la "$INSTALL_DIR"
            exit 1
        fi
    fi
fi

# Verify Java works
echo "[Java] Using: $JAVA_CMD"
$JAVA_CMD -version 2>&1 | head -1
echo ""

# ---- Create plugins directory if missing ----
mkdir -p plugins

# ---- Start Nukkit-MOT server ----
echo "[1/2] Starting Nukkit-MOT server on port 19132..."
$JAVA_CMD \
    -Dterminal.jline=false \
    -Dterminal.ansi=true \
    -Dlog4j.configurationFile=./log4j2.xml \
    -Xms512m -Xmx1024m \
    -jar Nukkit-MOT.jar &
NUKKIT_PID=$!
echo "  Nukkit PID: $NUKKIT_PID"

# Wait for Nukkit to initialize
sleep 10

# ---- Start GeyserMC proxy ----
if [ -d "geyser" ] && [ -f "geyser/Geyser-Standalone.jar" ]; then
    echo "[2/2] Starting GeyserMC proxy on port 19133..."
    cd geyser
    $JAVA_CMD -Xms256m -Xmx512m -jar Geyser-Standalone.jar &
    GEYSER_PID=$!
    echo "  Geyser PID: $GEYSER_PID"
    cd ..
else
    echo "[2/2] GeyserMC not found, skipping..."
    GEYSER_PID=""
fi

echo ""
echo "============================================"
echo "  Server is running!"
echo ""
echo "  Bedrock players connect to:"
echo "    IP: your-server-ip"
echo "    Port: 19132"
echo ""
echo "  Java Edition players connect via GeyserMC:"
echo "    IP: your-server-ip"
echo "    Port: 19133"
echo ""
echo "  Java: $JAVA_CMD"
echo "  PIDs: Nukkit=$NUKKIT_PID Geyser=$GEYSER_PID"
echo "  To stop: kill $NUKKIT_PID $GEYSER_PID"
echo "============================================"

# Wait for both processes
wait
