// api/admin-vip.js — temporary safe stub while full file is restored
module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  if (req.method === 'OPTIONS') { res.status(200).end(); return; }
  res.status(503).json({
    error: 'admin-vip em restauracao. Abra o arquivo admin-vip-COMPLETE.js dos artifacts e faca push para api/admin-vip.js',
  });
};
