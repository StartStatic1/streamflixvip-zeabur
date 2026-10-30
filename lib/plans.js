// lib/plans.js — preços e planos VIP num lugar só (painel, estimativa de receita, /api/plans).
// Os apps ainda mostram os preços fixos deles; mudar aqui NÃO muda o app sozinho.
const PLANS = [
  { id: '1m', label: '30 dias', hours: 720, price: 9.9 },
  { id: '3m', label: '3 meses', hours: 2160, price: 24.9 },
  { id: '6m', label: '6 meses', hours: 4320, price: 49.9 },
  { id: '12m', label: 'Anual', hours: 8760, price: 89.9 },
  { id: 'teste', label: 'Teste 1h', hours: 1, price: 0 },
];

/** Preço estimado a partir do rótulo salvo no código/conta (aceita rótulos antigos). */
function priceForLabel(label) {
  const p = String(label || '');
  if (/30\s*Dias|1\s*Mes/i.test(p)) return 9.9;
  if (/90\s*Dias|3\s*Mes/i.test(p)) return 24.9;
  if (/180\s*Dias|6\s*Mes/i.test(p)) return 49.9;
  if (/Anual|365\s*Dias|12\s*Mes/i.test(p)) return 89.9;
  if (/Vital/i.test(p)) return 149.9; // códigos antigos
  return null;
}

module.exports = { PLANS, priceForLabel };
