#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

pick_java_home() {
  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/javac" ]]; then
    echo "$JAVA_HOME"
    return
  fi
  if [[ -x "$ROOT/.jdk21/bin/javac" ]]; then
    echo "$ROOT/.jdk21"
    return
  fi
  if [[ -x /usr/lib/jvm/java-21-openjdk-amd64/bin/javac ]]; then
    echo /usr/lib/jvm/java-21-openjdk-amd64
    return
  fi
  echo "找不到带 javac 的 JDK 21。请安装: sudo apt install openjdk-21-jdk" >&2
  echo "或将 Temurin JDK 21 解压到 $ROOT/.jdk21" >&2
  exit 1
}

JAVA_HOME="$(pick_java_home)"
export JAVA_HOME
export PATH="${JAVA_HOME}/bin:${PATH}"

./gradlew clean --no-daemon
echo "Remote debug: localhost:5005（启动后挂起至调试器连接）" >&2
./gradlew :desktop:debug --no-daemon
