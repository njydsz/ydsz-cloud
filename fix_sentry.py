import pathlib

base = pathlib.Path(r'd:\Code\ydsz\udsz]project\\dsz-pmis\backend\ydsz-pmis-common\ydsz-pmis-common-sentry\\src\main\java\com\\njydsz\\tmis\\tcommon\sentry')

# P0-1: MicrometerMetricsCollector setGauge fix
p = base / 'metrics' / 'MicrometerMetricsCollector.java'
c = p.read_text(encoding='utf-8')

print('P0-1 before fix', 'gaugeRefCache' in c, 'registry(' in c)