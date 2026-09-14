from pathlib import Path

p = Path('api/live-tv.js')
if not p.exists():
    raise SystemExit('api/live-tv.js ausente')
t = p.read_text()

t = t.replace('const MAX_SOURCES = 5;', 'const MAX_SOURCES = 3;', 1)
t = t.replace(
    "const timeoutId = setTimeout(() => controller.abort(), 25000);",
    "const timeoutId = setTimeout(() => controller.abort(), 8000);",
    1,
)

if 'function settleWithBudget' not in t:
    helper = '''
function settleWithBudget(promises, budgetMs) {
  if (!promises.length) return Promise.resolve([]);
  return new Promise((resolve) => {
    const collected = new Array(promises.length);
    let done = 0;
    const finish = () => resolve(collected.filter(Boolean));
    const timer = setTimeout(finish, budgetMs);
    promises.forEach((p, i) => {
      Promise.resolve(p)
        .then((v) => { collected[i] = v; })
        .catch((err) => {
          collected[i] = {
            sourceName: 'fonte',
            priority: 100,
            categories: [],
            streams: [],
            skipReason: (err && err.message) || 'timeout',
          };
        })
        .finally(() => {
          done += 1;
          if (done >= promises.length) {
            clearTimeout(timer);
            finish();
          }
        });
    });
  });
}

'''
    t = t.replace('function hasXtreamCreds(source) {', helper + 'function hasXtreamCreds(source) {', 1)

t = t.replace('await Promise.all(\n          withCreds.map((s) =>', 'await settleWithBudget(\n          withCreds.map((s) =>', 1)
# close Promise.all extra paren vs settle budget ms
if 'settleWithBudget' in t and '          10000,' not in t:
    old_end = '''            }),
          ),
        )
      : [];'''
    new_end = '''            }),
          ),
          10000,
        )
      : [];'''
    if old_end in t:
        t = t.replace(old_end, new_end, 1)

t = t.replace('streams: streams.map(({ url, label, priority, quality, leg }) => ({',
              'streams: streams.slice(0, 2).map(({ url, label, priority, quality, leg }) => ({', 1)

if "[live-tv] warmup" not in t:
    t = t.rstrip() + '''

try {
  setTimeout(() => {
    const fakeReq = { method: 'GET', query: {}, headers: {} };
    const fakeRes = {
      headersSent: false,
      setHeader() {},
      status() { return this; },
      json() {},
    };
    handler(fakeReq, fakeRes).catch((e) => console.warn('[live-tv] warmup', e && e.message));
  }, 1200);
} catch (_) {}
'''

p.write_text(t)
print('live-tv speed patch ok', p.stat().st_size)
