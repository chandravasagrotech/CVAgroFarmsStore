# CVAgroFarmsStore — Export to Installable Software

This guide walks you through packaging CVAgroFarmsStore into a native Windows installer
(.exe) using Maven, jlink, and jpackage (all bundled with JDK 21).

---

## Prerequisites

Before starting, make sure you have the following installed:

| Tool | Version | Check Command |
|------|---------|---------------|
| JDK | 21+ | `java -version` |
| Maven | 3.8+ | `mvn -version` |
| WiX Toolset | 3.11+ | Required by jpackage to build `.msi` / `.exe` |

### Install WiX Toolset (required for Windows installer)
1. Download from https://github.com/wixtoolset/wix3/releases
2. Install and make sure `candle.exe` and `light.exe` are on your PATH
3. Verify: `candle /?`

---

## Step 1 — Clean and Build the Fat JAR

Add the `maven-shade-plugin` to your `pom.xml` inside `<plugins>` to produce a single
fat JAR with all dependencies bundled:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.5.2</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
                <createDependencyReducedPom>false</createDependencyReducedPom>
                <shadedArtifactAttached>false</shadedArtifactAttached>
                <filters>
                    <filter>
                        <artifact>*:*</artifact>
                        <excludes>
                            <exclude>META-INF/*.SF</exclude>
                            <exclude>META-INF/*.DSA</exclude>
                            <exclude>META-INF/*.RSA</exclude>
                        </excludes>
                    </filter>
                </filters>
                <transformers>
                    <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                        <mainClass>com.cvagrofarmsstore.Main</mainClass>
                    </transformer>
                    <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
                </transformers>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Then run:

```bash
mvn clean package
```

Output JAR will be at:
```
target/CVAgroFarmsStore-1.0-SNAPSHOT.jar
```

---

## Step 2 — Create a Custom JRE with jlink

JavaFX modules must be included manually. Run this from the project root:

```bash
jlink ^
  --module-path "%JAVA_HOME%\jmods;path\to\javafx-jmods-21" ^
  --add-modules java.base,java.desktop,java.logging,java.prefs,java.xml,javafx.controls,javafx.fxml,javafx.graphics ^
  --output target\runtime ^
  --strip-debug ^
  --compress=2 ^
  --no-header-files ^
  --no-man-pages
```

> Download JavaFX jmods from https://gluonhq.com/products/javafx/ — choose
> **JavaFX 21 SDK for Windows**, extract, and use the `lib` folder path above.

---

## Step 3 — Package with jpackage

Run jpackage to create a Windows installer:

```bash
jpackage ^
  --type exe ^
  --name "CVAgroFarmsStore" ^
  --app-version "1.0.0" ^
  --vendor "CVGroups" ^
  --description "CVAgroFarmsStore Agribusiness Manager" ^
  --input target ^
  --main-jar CVAgroFarmsStore-1.0-SNAPSHOT.jar ^
  --main-class com.cvagrofarmsstore.Main ^
  --runtime-image target\runtime ^
  --dest target\installer ^
  --win-dir-chooser ^
  --win-menu ^
  --win-shortcut ^
  --win-shortcut-prompt ^
  --java-options "--enable-native-access=javafx.graphics" ^
  --java-options "--add-opens=java.base/java.lang=ALL-UNNAMED" ^
  --java-options "--add-opens=java.base/java.util=ALL-UNNAMED" ^
  --java-options "-Dfile.encoding=UTF-8"
```

Output will be at:
```
target\installer\CVAgroFarmsStore-1.0.0.exe
```

---

## Step 4 — (Optional) Add a Custom App Icon

1. Prepare a `.ico` file (256x256 recommended), e.g. `src\main\resources\icon.ico`
2. Add to the jpackage command:

```bash
  --icon src\main\resources\icon.ico ^
```

---

## Step 5 — (Optional) Add a Splash Screen / License Agreement

Add these flags to the jpackage command:

```bash
  --license-file LICENSE.txt ^
  --win-per-user-install ^
```

---

## Step 6 — Run the Installer

1. Go to `target\installer\`
2. Double-click `CVAgroFarmsStore-1.0.0.exe`
3. Follow the setup wizard — it will install the app and create a Start Menu shortcut

---

## Folder Structure After Build

```
target/
├── CVAgroFarmsStore-1.0-SNAPSHOT.jar   ← fat JAR
├── runtime/                             ← custom JRE (jlink output)
└── installer/
    └── CVAgroFarmsStore-1.0.0.exe      ← final installer
```

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `jpackage: command not found` | Make sure JDK 21 bin is on PATH, not just JRE |
| `WiX toolset not found` | Install WiX 3.11 and add to PATH |
| `JavaFX modules not found` | Download JavaFX 21 jmods and set correct path in jlink command |
| App launches but DB not found | The DB is stored in `%APPDATA%\CVGroups\CVAgroFarmsStore\` on first run — this is normal |
| Signed installer needed | Use `--win-sign` with a code-signing certificate for production distribution |

---

## Quick Reference — All Commands in Order

```bash
# 1. Build fat JAR
mvn clean package

# 2. Create custom JRE
jlink --module-path "%JAVA_HOME%\jmods;C:\javafx-jmods-21" --add-modules java.base,java.desktop,java.logging,java.prefs,java.xml,javafx.controls,javafx.fxml,javafx.graphics --output target\runtime --strip-debug --compress=2 --no-header-files --no-man-pages

# 3. Create installer
jpackage --type exe --name "CVAgroFarmsStore" --app-version "1.0.0" --vendor "CVGroups" --input target --main-jar CVAgroFarmsStore-1.0-SNAPSHOT.jar --main-class com.cvagrofarmsstore.Main --runtime-image target\runtime --dest target\installer --win-dir-chooser --win-menu --win-shortcut --java-options "--enable-native-access=javafx.graphics" --java-options "-Dfile.encoding=UTF-8"
```
