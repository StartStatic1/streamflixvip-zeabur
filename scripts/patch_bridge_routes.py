from pathlib import Path

s = Path('server.js')
t = s.read_text()
if "admin-bridge" not in t:
    t = t.replace(
        "const adminAddons = require('./api/admin-addons.js');",
        "const adminAddons = require('./api/admin-addons.js');\nconst adminBridge = require('./api/admin-bridge.js');\nconst bridgeApi = require('./api/bridge.js');",
    )
    t = t.replace(
        "app.all('/api/admin-addons', wrap(adminAddons));",
        "app.all('/api/admin-addons', wrap(adminAddons));\napp.all('/api/admin-bridge', wrap(adminBridge));\napp.all('/api/bridge/*', wrap(bridgeApi));",
    )
    s.write_text(t)
    print('server.js rotas ok')
else:
    print('server.js ja tinha bridge')

a = Path('Public/admin-addons.html')
h = a.read_text()
if 'admin-bridge.html' not in h:
    h = h.replace(
        '<a href="/admin-api.html">API Parceiros</a>',
        '<a href="/admin-bridge.html">Bridge IPTV</a>\n      <a href="/admin-api.html">API Parceiros</a>',
    )
    a.write_text(h)
    print('link addons ok')
else:
    print('link ja existe')
