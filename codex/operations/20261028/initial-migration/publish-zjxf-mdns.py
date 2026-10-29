from pathlib import Path
import shutil,subprocess,time

hosts=Path('/etc/avahi/hosts')
text=hosts.read_text()
record='192.168.1.245 zjxf.local'
for line in text.splitlines():
    fields=line.split('#',1)[0].split()
    if len(fields)>1 and 'zjxf.local' in fields[1:] and fields[0]!='192.168.1.245':
        raise SystemExit('Conflicting zjxf.local record; no changes made')
text='\n'.join(line for line in text.splitlines() if line.strip() not in ('# ZJERP LAN hostname',record))+'\n'
hosts.write_text(text)
subprocess.run(['systemctl','reload','avahi-daemon'],check=True)
unit=Path('/etc/systemd/system/zjerp-mdns.service')
unit.write_text('''[Unit]
Description=ZJERP LAN mDNS hostname zjxf.local
After=avahi-daemon.service network-online.target
Wants=avahi-daemon.service network-online.target

[Service]
Type=simple
User=admin
Group=Users
ExecStart=/usr/bin/avahi-publish-address --no-reverse --no-fail zjxf.local 192.168.1.245
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
''')
unit.chmod(0o644)
subprocess.run(['systemctl','daemon-reload'],check=True)
subprocess.run(['systemctl','enable','--now','zjerp-mdns.service'],check=True)
print('Persistent mDNS record configured:',record)
