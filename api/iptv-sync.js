// api/iptv-sync.js
// RESTORE NEEDED - use VPS: git show ec5b5c7:api/iptv-sync.js > api/iptv-sync.js
module.exports = async function handler(req, res) {
  res.status(503).json({ error: 'iptv-sync temporariamente offline - restore from git show ec5b5c7:api/iptv-sync.js' });
};
