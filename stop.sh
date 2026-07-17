#!/bin/bash
# Stop the Minecraft Bedrock Server
echo "Stopping Minecraft Bedrock Server..."
pkill -f "Nukkit-MOT.jar" 2>/dev/null
pkill -f "Geyser-Standalone.jar" 2>/dev/null
echo "Server stopped."
