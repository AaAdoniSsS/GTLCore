"""Build and boot the native Forge server in a fresh local world, then verify shutdown."""

import hashlib
import json
import os
from pathlib import Path
import queue
import shutil
import socket
import subprocess
import tempfile
import threading
import time
import tomllib
import urllib.request
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[1]
FORGE = ROOT / '.gradle/forge-server'
FORGE_VERSION = '1.20.1-47.4.16'
INSTALLER_SHA256 = '29e3d45362e7b373f9022c8b3a512e50acfc3bbe5c76be48dedc753450b6008f'


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def prepare_runtime():
    source = os.environ.get('GTL_MODS_DIR')
    target = ROOT / '.gradle/dev-mods'
    target.mkdir(parents=True, exist_ok=True)
    manifest = json.loads((ROOT / 'scripts/dev-runtime.json').read_text())
    for name, expected in manifest.items():
        artifact = target / name
        if not artifact.exists():
            if not source:
                raise RuntimeError(f'Missing runtime dependency {name}; set GTL_MODS_DIR to its source directory')
            shutil.copy2(Path(source) / name, artifact)
        actual = sha256(artifact)
        if actual != expected:
            raise RuntimeError(f'SHA-256 mismatch: {artifact}; expected {expected}, got {actual}')
        print(f'Runtime: {name} SHA-256 {actual}', flush=True)
    return [target / name for name in manifest]


def prepare_forge():
    args_file = FORGE / f'libraries/net/minecraftforge/forge/{FORGE_VERSION}/unix_args.txt'
    if args_file.is_file():
        return args_file
    FORGE.mkdir(parents=True, exist_ok=True)
    installer = FORGE / 'forge-installer.jar'
    if not installer.exists():
        url = f'https://maven.minecraftforge.net/net/minecraftforge/forge/{FORGE_VERSION}/forge-{FORGE_VERSION}-installer.jar'
        with urllib.request.urlopen(url, timeout=60) as response, installer.with_suffix('.part').open('wb') as out:
            shutil.copyfileobj(response, out)
        installer.with_suffix('.part').replace(installer)
    if sha256(installer) != INSTALLER_SHA256:
        raise RuntimeError(f'Forge installer SHA-256 mismatch: {installer}')
    log = ROOT / 'build/dev/logs/forge-server-install.log'
    log.parent.mkdir(parents=True, exist_ok=True)
    print(f'Installing Forge {FORGE_VERSION}; log: {log}', flush=True)
    with log.open('w') as out:
        subprocess.run([str(Path(os.environ['JAVA_HOME']) / 'bin/java'), '-jar', str(installer),
                        '--installServer', '.'], cwd=FORGE, stdout=out, stderr=subprocess.STDOUT,
                       timeout=600, check=True)
    if not args_file.is_file():
        raise RuntimeError(f'Forge installation did not produce {args_file}')
    return args_file


def copy_mods(run_dir, additions, *, include_wildcard=True, artifact=None, extra_mods=()):
    exported = json.loads((ROOT / 'build/dev/native-runtime.json').read_text())
    mods = run_dir / 'mods'
    mods.mkdir()
    # Replace the two outdated development dependencies and omit physical-client mods.
    excluded = {'ldlib', 'ae2wtlib', 'embeddium', 'embeddium_extra', 'jecharacters', 'modernui', 'jei'}
    artifacts = []
    for name in exported['dependencies']:
        path = Path(name)
        if path.suffix != '.jar':
            continue
        with ZipFile(path) as jar:
            if 'META-INF/mods.toml' in jar.namelist():
                metadata = tomllib.loads(jar.read('META-INF/mods.toml').decode())
                ids = {mod['modId'] for mod in metadata.get('mods', [])}
                if not ids.intersection(excluded):
                    artifacts.append(path)
            elif 'META-INF/MANIFEST.MF' in jar.namelist():
                manifest = jar.read('META-INF/MANIFEST.MF').decode()
                if 'FMLModType: GAMELIBRARY' in manifest or 'FMLModType: LIBRARY' in manifest:
                    artifacts.append(path)
    wildcard = ROOT / 'libs/wildcard_pattern-0.1.2-gtl.jar'
    if sha256(wildcard) != 'a18745ebd18e4c54f0fa4fc1c97d9ff7973ec9d1a84e413843164282d2061d34':
        raise RuntimeError('Wildcard Pattern dependency differs from the verified baseline')
    if include_wildcard:
        artifacts.append(wildcard)
    artifacts.extend([*additions, ROOT / 'libs/Re-Avaritia-forged-1.20.1-1.3.8.3.jar',
                      artifact or Path(exported['artifact']), *extra_mods])
    inventory = []
    for path in artifacts:
        shutil.copy2(path, mods / path.name)
        inventory.append({'file': path.name, 'sha256': sha256(path), 'source': str(path)})
    (run_dir / 'mod-manifest.json').write_text(json.dumps(inventory, indent=2) + '\n')
    print(f'Installed {len(inventory)} runtime JARs; manifest: {run_dir / "mod-manifest.json"}', flush=True)


def prepare_instance(additions, *, prefix='dev-smoke-', **mod_options):
    if os.environ.get('GTL_ACCEPT_EULA') != 'true':
        raise RuntimeError('Read https://aka.ms/MinecraftEULA and set GTL_ACCEPT_EULA=true to run server tests')
    (ROOT / 'run').mkdir(exist_ok=True)
    run_dir = Path(tempfile.mkdtemp(prefix=prefix, dir=ROOT / 'run'))
    (run_dir / 'libraries').symlink_to(FORGE / 'libraries', target_is_directory=True)
    copy_mods(run_dir, additions, **mod_options)
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        port = listener.getsockname()[1]
    # The caller explicitly opted in to running a Minecraft test server.
    (run_dir / 'eula.txt').write_text('eula=true\n')
    (run_dir / 'server.properties').write_text(
        f'server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\n'
        'level-name=dev-world\nlevel-type=minecraft:flat\ngenerate-structures=false\n'
        'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},'
        '{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],'
        '"biome":"minecraft:plains","structure_overrides":[]}\n'
        'view-distance=2\nsimulation-distance=2\nmax-players=1\n'
        'enable-rcon=false\nenable-query=false\nspawn-protection=0\n'
    )
    print(f'Isolated server: {run_dir}, 127.0.0.1:{port}', flush=True)
    return run_dir


def main():
    additions = prepare_runtime()
    args_file = prepare_forge()
    subprocess.run([str(ROOT / 'dev'), 'build', 'devExportRuntime'], cwd=ROOT, check=True)
    run_dir = prepare_instance(additions)
    process = subprocess.Popen(
        [str(Path(os.environ['JAVA_HOME']) / 'bin/java'), '-Xms1G', '-Xmx4G', f'@{args_file}', 'nogui'],
        cwd=run_dir, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT, text=True, bufsize=1,
    )
    output = queue.Queue()

    def read_output():
        for line in process.stdout:
            output.put(line)
        output.put(None)

    threading.Thread(target=read_output, daemon=True).start()
    ready = False
    stopped = False
    stop_at = None
    deadline = time.monotonic() + 600
    console_log = run_dir / 'console.log'
    try:
        with console_log.open('w') as log:
            while True:
                now = time.monotonic()
                if now > deadline:
                    raise TimeoutError(f'Server startup/shutdown timed out; {console_log}')
                if stop_at is not None and now >= stop_at:
                    process.stdin.write('stop\n')
                    process.stdin.flush()
                    stop_at = None
                    deadline = now + 90
                try:
                    line = output.get(timeout=0.5)
                except queue.Empty:
                    continue
                if line is None:
                    break
                log.write(line)
                log.flush()
                print(line, end='', flush=True)
                if 'Failed to start the minecraft server' in line:
                    raise RuntimeError(f'Server startup failed; {console_log}')
                if 'Done (' in line and 'For help, type "help"' in line:
                    ready = True
                    stop_at = time.monotonic() + 5
                if 'Stopping server' in line:
                    stopped = True
        code = process.wait(timeout=10)
        if code != 0 or not ready or not stopped:
            raise RuntimeError(f'Server smoke failed: exit={code}, ready={ready}, stopped={stopped}; {console_log}')
        if not (run_dir / 'dev-world/level.dat').is_file():
            raise RuntimeError(f'Server did not save level.dat: {run_dir}')
        debug_log = (run_dir / 'logs/debug.log').read_text()
        for mod in ('gtlcore', 'wildcard_pattern', 'mae2'):
            if f'{{{mod}}} mods' not in debug_log:
                raise RuntimeError(f'Expected mod was not discovered: {mod}')
        print(f'PASS: server ready, expected mods present, normal stop, level.dat saved; {run_dir}')
    finally:
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()


if __name__ == '__main__':
    main()
