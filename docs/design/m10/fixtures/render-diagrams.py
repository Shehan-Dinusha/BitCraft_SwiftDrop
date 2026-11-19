"""Render Stage 16 Mermaid sources to SVG, PNG and PDF using an installed Chrome.

Install CLI into the ignored build directory (PowerShell):
  $env:PUPPETEER_SKIP_DOWNLOAD='true'
  npm.cmd install --prefix backend/analytics-ai-service/target/diagram-tools --no-save @mermaid-js/mermaid-cli@12.2.0
Then run: python docs/design/m10/fixtures/render-diagrams.py --chrome <chrome-executable>
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[4]
OUTPUT = ROOT / 'docs/design/m10'
BUILD = ROOT / 'backend/analytics-ai-service/target/diagram-tools'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--chrome', required=True, type=Path)
    args = parser.parse_args()
    chrome = args.chrome.resolve(strict=True)
    cli = BUILD / 'node_modules/@mermaid-js/mermaid-cli/src/cli.js'
    if not cli.is_file():
        raise SystemExit('Install Mermaid CLI as shown in this script docstring first')
    package = json.loads((cli.parents[1] / 'package.json').read_text(encoding='utf-8'))
    config = BUILD / 'puppeteer-stage16.json'
    config.write_text(json.dumps({'executablePath': str(chrome), 'headless': True}), encoding='utf-8')
    outputs = []
    for diagram in ['ai-tool-calling-sequence', 'analytics-erd']:
        for extension in ['svg', 'png', 'pdf']:
            destination = OUTPUT / f'{diagram}.{extension}'
            subprocess.run(['node', str(cli), '-i', str(OUTPUT / f'{diagram}.mmd'), '-o', str(destination),
                            '-c', str(OUTPUT / 'mermaid-config.json'), '-p', str(config),
                            '--no-font-embed', '--size', '5200', '-b', 'white'], check=True, cwd=ROOT)
            assert destination.is_file() and destination.stat().st_size > 1000, f'Empty export: {destination}'
            outputs.append({'file': destination.name, 'bytes': destination.stat().st_size,
                            'sha256': hashlib.sha256(destination.read_bytes()).hexdigest()})
            print('EXPORTED ' + destination.name, flush=True)
    sources = {name: hashlib.sha256((OUTPUT / name).read_bytes().replace(b'\r\n', b'\n')).hexdigest()
               for name in ['ai-tool-calling-sequence.mmd', 'analytics-erd.mmd', 'mermaid-config.json']}
    evidence = ROOT / 'docs/test-reports/m10/stage-16'
    evidence.mkdir(parents=True, exist_ok=True)
    manifest = {'cliVersion': package['version'], 'sourcesSha256': sources, 'outputs': outputs}
    (evidence / 'render-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
