#!/usr/bin/env bash
# Builds and tests WITHOUT Maven, using JARs already on this machine. Handy when the package
# registries are blocked. On a normal machine just run: mvn test
set -euo pipefail
cd "$(dirname "$0")/.."
GSON=${GSON_JAR:-/opt/gradle-8.14.3/lib/gson-2.10.jar}
JUNIT=${JUNIT_JAR:-/opt/gradle-8.14.3/lib/junit-4.13.2.jar}
HAMCREST=${HAMCREST_JAR:-/opt/gradle-8.14.3/lib/hamcrest-core-1.3.jar}
rm -rf build && mkdir -p build/classes build/test-classes
javac -Xlint:all -cp "$GSON" -d build/classes $(find src/main -name "*.java")
javac -Xlint:all -cp "build/classes:$GSON:$JUNIT:$HAMCREST" -d build/test-classes $(find src/test -name "*.java")
CLASSES=$(cd build/test-classes && find . -name "*Test.class" | sed 's|^\./||; s|\.class$||; s|/|.|g')
java -cp "build/classes:build/test-classes:$GSON:$JUNIT:$HAMCREST" org.junit.runner.JUnitCore $CLASSES
