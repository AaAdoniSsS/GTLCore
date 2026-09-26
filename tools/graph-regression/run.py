"""Dependency-free regression entry point for the graph planner (Python 3 + JDK 17)."""
from pathlib import Path
import argparse
import json
import os
import random
import re
import shutil
import struct
import subprocess

BASE = Path(__file__).resolve().parent
ROOT = BASE.parent.parent


def write_cases(path, cases):
    with path.open('wb') as stream:
        def number(value):
            stream.write(struct.pack('>i', value))
        def string(value):
            encoded = value.encode('utf-8')
            number(len(encoded))
            stream.write(encoded)
        def amounts(value):
            number(len(value))
            for key, amount in value.items():
                string(key)
                stream.write(struct.pack('>q', amount))
        number(len(cases))
        for case in cases:
            string(case['name'])
            string(case['target'])
            stream.write(struct.pack('>q', case['amount']))
            truth = case.get('truth', 'UNKNOWN')
            try:
                truth = json.loads(case.get('detail', '{}')).get('truth', truth)
            except (TypeError, ValueError):
                pass
            string(truth)
            amounts(case['stock'])
            number(len(case['recipes']))
            for recipe in case['recipes']:
                string(recipe['id'])
                amounts(recipe['inputs'])
                amounts(recipe['outputs'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--suite', choices=['all', 'fixtures', 'oracles'], default='all')
    parser.add_argument('--permutations', type=int, default=3)
    parser.add_argument('--milliseconds', type=int, default=3000)
    parser.add_argument('--work', type=int, default=20000000)
    parser.add_argument('--case', default='.*', help='Full-match regex for fixture names')
    parser.add_argument('--proofs', action='store_true', help='Export and independently check scoped certificates')
    parser.add_argument('--output', type=Path, default=ROOT / 'build/graph-regression')
    args = parser.parse_args()
    if args.permutations <= 0 or args.milliseconds < 0 or args.work <= 0:
        parser.error('Require positive permutations/work and nonnegative milliseconds')
    out = args.output.resolve()
    classes = out / 'classes'
    classes.mkdir(parents=True, exist_ok=True)
    def java(name):
        if args.java_home:
            return str(Path(args.java_home) / 'bin' / (name + ('.exe' if os.name == 'nt' else '')))
        found = shutil.which(name)
        if not found:
            parser.error('Cannot find ' + name + '; specify --java-home')
        return found
    sources = sorted((ROOT / 'src/main/java/org/gtlcore/gtlcore/integration/ae2/graph/core').glob('*.java'))
    sources += sorted((BASE / 'java').glob('*.java'))
    argfile = out / 'sources.args'
    argfile.write_text('\n'.join('"' + f.as_posix() + '"' for f in sources), encoding='utf-8')
    subprocess.run([java('javac'), '--release', '17', '-encoding', 'UTF-8', '-d', str(classes), '@' + str(argfile)], check=True)
    def run(name, arguments=()):
        with (out / (name + '-' + str(len(list(out.glob(name + '-*.log')))) + '.log')).open('w', encoding='utf-8') as log:
            completed = subprocess.run([java('java'), '-ea', '-Xmx2g', '-cp', str(classes),
                                        'org.gtlcore.gtlcore.integration.ae2.graph.core.' + name, *map(str, arguments)],
                                       stdout=log, stderr=subprocess.STDOUT)
        if completed.returncode:
            details = Path(log.name).read_text(encoding='utf-8')
            print(details)
            if os.environ.get('GITHUB_ACTIONS') == 'true':
                tail = details[-6000:].replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
                print('::error title=Graph regression failed::' + name + ': ' + tail)
            raise SystemExit(completed.returncode)
        print(name + ': PASS (' + Path(log.name).name + ')', flush=True)
    if args.suite != 'oracles':
        cases = [json.loads(f.read_text(encoding='utf-8')) for f in sorted((BASE / 'fixtures').rglob('*.json'))]
        cases = [case for case in cases if re.fullmatch(args.case, case['name'])]
        if not cases:
            parser.error('No fixtures match --case')
        for permutation in range(args.permutations):
            rng = random.Random(20260926 + permutation)
            copied = json.loads(json.dumps(cases))
            for case in copied:
                rng.shuffle(case['recipes'])
                for recipe in case['recipes']:
                    for field in ['inputs', 'outputs']:
                        items = list(recipe[field].items())
                        rng.shuffle(items)
                        recipe[field] = dict(items)
            path = out / ('fixtures-' + str(permutation) + '.bin')
            write_cases(path, copied)
            extra = []
            if args.proofs:
                proof_dir = out / ('proofs-' + str(permutation))
                proof_dir.mkdir(exist_ok=True)
                extra.append(proof_dir)
            run('GraphFixtureRegression', [path, args.milliseconds, args.work, *extra])
    if args.suite != 'fixtures':
        for source in sorted((BASE / 'java').glob('*.java')):
            if source.stem != 'GraphFixtureRegression':
                run(source.stem)


if __name__ == '__main__':
    main()
