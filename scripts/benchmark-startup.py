#!/usr/bin/env python3
"""Measure startup and a fixed UI journey on an owned emulator."""
import sys, time, json, re, statistics, subprocess
from pathlib import Path
import argparse, os, xml.etree.ElementTree as E
parser = argparse.ArgumentParser(description='Disposable-emulator startup and interaction samples; not a device power benchmark')
parser.add_argument('--serial', required=True)
parser.add_argument('--label', required=True)
parser.add_argument('--output', default='artifacts/startup-performance')
parser.add_argument('--fixture-events', type=int, default=0)
args = parser.parse_args()
if not args.serial.startswith('emulator-'):
    parser.error('A disposable emulator serial is required')
adb = Path(os.environ['ANDROID_HOME']) / 'platform-tools/adb'

class Ui:

    @staticmethod
    def adb(*a):
        return subprocess.check_output([str(adb), '-s', args.serial, *a])

    @staticmethod
    def tree():
        result = Ui.adb('shell', 'uiautomator', 'dump', '/sdcard/time-left-benchmark.xml')
        if b'ERROR:' in result:
            raise RuntimeError(result.decode())
        return E.fromstring(Ui.adb('shell', 'cat', '/sdcard/time-left-benchmark.xml'))
ui = Ui
label = args.label
out = Path(args.output)
out.mkdir(parents=True, exist_ok=True)
PKG = 'dev.calendarquiet'

def sh(*a):
    return ui.adb('shell', *a).decode().strip()

def start():
    return sh('am', 'start', '-W', '-a', 'dev.calendarquiet.OPEN_TIMER', '--activity-single-top', '--activity-clear-top', '-n', PKG + '/.MainActivity')
sh('cmd', 'statusbar', 'collapse')
sh('wm', 'size', 'reset')
sh('settings', 'put', 'system', 'font_scale', '1.0')
cold = []
fully_drawn = []
for i in range(8):
    sh('am', 'force-stop', PKG)
    sh('logcat', '-c')
    time.sleep(0.5)
    r = start()
    (out / f'{label}-cold-{i}.txt').write_text(r)
    cold.append(int(re.search('TotalTime: (\\d+)', r).group(1)))
    time.sleep(1)
    log = sh('logcat', '-d', '-s', 'ActivityTaskManager:I')
    (out / f'{label}-cold-{i}-log.txt').write_text(log)
    matches = re.findall('Fully drawn dev\\.calendarquiet/[^:]+: \\+((?:[0-9]+s)?[0-9]+)ms', log)

    def milliseconds(value):
        if 's' in value:
            sec, ms = value.split('s')
            return int(sec) * 1000 + int(ms)
        return int(value)
    fully_drawn.append(milliseconds(matches[-1]) if matches else None)
print('cold_ms', cold, flush=True)
tree = ui.tree()
coords = {}
for n in tree.iter('node'):
    t = n.get('text') or n.get('content-desc', '')
    if t in ('Timer', 'Calendar'):
        a, b, c, d = map(int, re.findall('\\d+', n.get('bounds')))
        coords[t] = [(a + c) // 2, (b + d) // 2]
assert len(coords) == 2, coords
sh('dumpsys', 'gfxinfo', PKG, 'reset')
resumes = []
for i in range(6):
    sh('input', 'tap', *map(str, coords['Calendar']))
    time.sleep(0.55)
    sh('input', 'swipe', '360', '1200', '360', '550', '280')
    time.sleep(0.35)
    sh('input', 'tap', *map(str, coords['Timer']))
    time.sleep(0.55)
    sh('input', 'keyevent', '3')
    time.sleep(0.3)
    r = start()
    resumes.append(int(re.search('WaitTime: (\\d+)', r).group(1)))
    time.sleep(0.6)
gfx = sh('dumpsys', 'gfxinfo', PKG, 'framestats')
(out / f'{label}-frames.txt').write_text(gfx)
mem = sh('dumpsys', 'meminfo', PKG)
(out / f'{label}-memory.txt').write_text(mem)
pss = re.search('TOTAL PSS:\\s+(\\d+)', mem)
result = {'cold_start_ms': cold, 'fully_drawn_ms': fully_drawn, 'cold_median_ms': statistics.median(cold), 'warm_dispatch_wait_ms': resumes, 'warm_dispatch_median_ms': statistics.median(resumes), 'warm_note': 'am start WaitTime measures dispatch, not time to fully drawn UI', 'frames_summary': gfx.split('---PROFILEDATA---')[0], 'total_pss_kb': int(pss.group(1)) if pss else None, 'calendar_fixture_events': args.fixture_events, 'emulator': 'AOSP API35 720x1600 density320 font1.0', 'sample_note': 'Short emulator comparisons; no physical-device smoothness or power claim.'}
(out / f'{label}-benchmark.json').write_text(json.dumps(result, indent=2))
print(json.dumps({k: v for k, v in result.items() if k != 'frames_summary'}, indent=2), flush=True)
