#!/usr/bin/env bash
set -e

JAR_NAME="yucli-19.0.0.jar"
INSTALL_DIR="$HOME/.YuCLI/bin"
SCRIPT_NAME="yucli"

echo "=== YuCLI Installer ==="

# 1. Check JDK 17+
if ! command -v java &>/dev/null; then
    echo "Error: java not found. Please install JDK 17+ first."
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -1 | sed 's/.*"\([0-9]*\).*/\1/')
if [ "$JAVA_VERSION" -lt 17 ] 2>/dev/null; then
    echo "Error: JDK 17+ required, found version $JAVA_VERSION"
    exit 1
fi

# 2. Build if jar not found
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_PATH="$SCRIPT_DIR/target/$JAR_NAME"

if [ ! -f "$JAR_PATH" ]; then
    echo "Jar not found, building with Maven..."
    if ! command -v mvn &>/dev/null; then
        echo "Error: mvn not found. Please install Maven or build manually: mvn clean package -DskipTests"
        exit 1
    fi
    cd "$SCRIPT_DIR"
    mvn clean package -DskipTests -q
    echo "Build complete."
fi

# 3. Install
mkdir -p "$INSTALL_DIR"
cp "$JAR_PATH" "$INSTALL_DIR/$JAR_NAME"

# 4. Create wrapper script
cat > "$INSTALL_DIR/$SCRIPT_NAME" <<WRAPPER
#!/usr/bin/env bash
exec java -jar "$INSTALL_DIR/$JAR_NAME" "\$@"
WRAPPER
chmod +x "$INSTALL_DIR/$SCRIPT_NAME"

# 5. Add to PATH if needed
if ! echo "$PATH" | tr ':' '\n' | grep -qx "$INSTALL_DIR"; then
    SHELL_RC=""
    if [ -f "$HOME/.zshrc" ]; then
        SHELL_RC="$HOME/.zshrc"
    elif [ -f "$HOME/.bashrc" ]; then
        SHELL_RC="$HOME/.bashrc"
    elif [ -f "$HOME/.bash_profile" ]; then
        SHELL_RC="$HOME/.bash_profile"
    fi

    if [ -n "$SHELL_RC" ]; then
        echo "export PATH=\"$INSTALL_DIR:\$PATH\"" >> "$SHELL_RC"
        echo "Added $INSTALL_DIR to PATH in $SHELL_RC"
        echo "Run 'source $SHELL_RC' or restart your terminal."
    else
        echo "Please add $INSTALL_DIR to your PATH manually."
    fi
fi

echo ""
echo "=== Installation complete ==="
echo "Run: yucli"
echo "Install location: $INSTALL_DIR"
