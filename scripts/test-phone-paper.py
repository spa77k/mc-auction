#!/usr/bin/env python3
"""隔離Paperでスマホからの出品選択と安全確認を検証する。"""
import os
import pathlib
import shutil
import subprocess
import zipfile

root = pathlib.Path(__file__).resolve().parents[1]
source = root.parent / 'mc-ecolife/server-data'
work = root / 'target/auction-phone-smoke'
java = os.environ.get('JAVA_BIN', '/opt/homebrew/opt/openjdk@25/bin/java')
subprocess.run(['mvn', '-B', 'package'], cwd=root, check=True)
if work.exists():
    shutil.rmtree(work)
(work / 'plugins').mkdir(parents=True)
for name in ('libraries', 'cache', 'versions'):
    if (source / name).exists():
        shutil.copytree(source / name, work / name)
shutil.copy2(source / 'paper-26.1.2-74.jar', work / 'paper.jar')
shutil.copy2(source / 'eula.txt', work / 'eula.txt')
shutil.copy2(root / 'server-data-26.1.2/plugins/Vault.jar', work / 'plugins/Vault.jar')
shutil.copy2(root / 'target/auctionhouse-1.0.0.jar', work / 'plugins/AuctionHouse.jar')
(work / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25584\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nlevel-type=minecraft:flat\ngenerate-structures=false\n')
with zipfile.ZipFile(work / 'plugins/AuctionPhoneProbe.jar', 'w') as jar:
    jar.writestr('plugin.yml', 'name: AuctionPhoneProbe\nversion: 1\nmain: net.mcauction.auctionhouse.PaperAuctionPhoneProbe\napi-version: "26.1.2"\ndepend: [AuctionHouse]\n')
    jar.write(root / 'target/test-classes/net/mcauction/auctionhouse/PaperAuctionPhoneProbe.class',
              'net/mcauction/auctionhouse/PaperAuctionPhoneProbe.class')
with (work / 'smoke.log').open('w') as log:
    result = subprocess.run([java, '-Dterminal.jline=false', '-Dterminal.ansi=false', '-Xms256M', '-Xmx1G', '-jar', 'paper.jar', '--nogui'],
                            cwd=work, stdout=log, stderr=subprocess.STDOUT, timeout=180)
output = (work / 'smoke.log').read_text()
for line in output.splitlines():
    if any(word in line for word in ('AUCTION_PHONE_PROBE', 'Exception', 'AssertionError', 'Caused by:')):
        print(line, flush=True)
if result.returncode or 'AUCTION_PHONE_PROBE_PASS' not in output or 'AUCTION_PHONE_PROBE_FAIL' in output:
    raise SystemExit('FAILED: ' + str(work / 'smoke.log'))
print('PASS: phone excluded, inventory item selected, changed item rejected. Logs: ' + str(work))
