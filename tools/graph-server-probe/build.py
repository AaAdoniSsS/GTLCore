"""Build the opt-in Forge probe, entirely inside this checkout. Stop the server first."""
from pathlib import Path
import argparse
import json
import os
import subprocess
import tempfile
import zipfile

root = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--server", type=Path, default=root / ".local/graph-server")
parser.add_argument("--java-home", type=Path, default=os.environ.get("JAVA_HOME"))
parser.add_argument("--core-jar", type=Path, required=True)
options = parser.parse_args()
server = options.server.resolve()
if not server.is_relative_to(root) or not (server / "mods").is_dir():
    parser.error("Use an isolated server directory inside this checkout")
if options.java_home is None:
    parser.error("Set JAVA_HOME or pass --java-home (JDK 17 or later)")
core = options.core_jar.resolve()
if not core.is_file():
    parser.error("Core JAR does not exist")
installed = list((server / "mods").glob("gtlcore-*.jar"))
if len(installed) != 1:
    parser.error("Keep exactly one Core JAR in the isolated server mods folder before compiling the probe")
import hashlib
if hashlib.sha256(installed[0].read_bytes()).digest() != hashlib.sha256(core.read_bytes()).digest():
    parser.error("Installed Core differs from --core-jar; install the intended build first")
classpath = [core] + [p for p in (server / "mods").glob("*.jar")
                      if not p.name.startswith("gtlcore-") and p.name != "graph-server-probe.jar"]
classpath += list((server / "libraries").rglob("*.jar"))
workspace = root / "build/graph-server-probe"
workspace.mkdir(parents=True, exist_ok=True)
with tempfile.TemporaryDirectory(dir=workspace) as temporary:
    temporary = Path(temporary)
    classes = temporary / "classes"
    classes.mkdir()
    arguments = temporary / "javac.args"
    arguments.write_text('--release 17\n-proc:none\n-encoding UTF-8\n-cp\n"' +
                         os.pathsep.join(p.as_posix() for p in classpath) + '"\n-d\n"' +
                         classes.as_posix() + '"\n' + '\n'.join('"' + p.as_posix() + '"'
                         for p in Path(__file__).parent.glob("*.java")), encoding="utf-8")
    javac = options.java_home / "bin" / ("javac.exe" if os.name == "nt" else "javac")
    subprocess.run([str(javac), "@" + str(arguments)], check=True)
    output = temporary / "graph-server-probe.jar"
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as jar:
        for file in classes.rglob("*.class"):
            jar.write(file, file.relative_to(classes).as_posix())
        jar.writestr("META-INF/mods.toml", 'modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n'
                     '[[mods]]\nmodId="gtlgraphprobe"\nversion="1"\ndisplayName="Local graph validation fixture"\n')
        jar.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nMixinConfigs: graph-probe.mixins.json\n\n")
        jar.writestr("graph-probe.mixins.json", json.dumps({"required": True, "minVersion": "0.8",
                     "package": "org.gtlcore.test.mixin", "compatibilityLevel": "JAVA_17",
                     "mixins": ["LegacyPlannerProbe", "LegacyExecutorProbe", "MaxFastProbe", "AssemblerDispatchProbe"],
                     "injectors": {"defaultRequire": 1}}))
    output.replace(server / "mods/graph-server-probe.jar")
print("Installed isolated-server probe. Never distribute this test mod with Core.")
