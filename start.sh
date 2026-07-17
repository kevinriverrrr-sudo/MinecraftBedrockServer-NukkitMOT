#!/bin/bash
# Minecraft Bedrock Server - Start Script
# Nukkit-MOT + Custom Plugins + GeyserMC

cd "$(dirname "$0")"

echo "============================================"
echo "  Minecraft Bedrock Server"
echo "  Core: Nukkit-MOT"
echo "  With 7 custom plugins"
echo "  + GeyserMC for cross-platform"
echo "============================================"

# Start Nukkit-MOT server
echo "[1/2] Starting Nukkit-MOT server on port 19132..."
java -Xms512m -Xmx1024m \
    -Dlog4j.configurationFile=./log4j2.xml \
    -jar Nukkit-MOT.jar &
NUKKIT_PID=$!
echo "  Nukkit PID: $NUKKIT_PID"

# Wait for Nukkit to start
sleep 10

# Start GeyserMC proxy (allows Java Edition players to connect)
echo "[2/2] Starting GeyserMC proxy on port 19133..."
cd geyser
java -Xms256m -Xmx512m -jar Geyser-Standalone.jar &
GEYSER_PID=$!
echo "  Geyser PID: $GEYSER_PID"

cd ..

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
echo "  PIDs: Nukkit=$NUKKIT_PID Geyser=$GEYSER_PID"
echo "  To stop: kill $NUKKIT_PID $GEYSER_PID"
echo "============================================"

# Wait for both processes
wait
