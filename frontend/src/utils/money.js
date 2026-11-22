/**
 * Format a numeric value using the ERP's existing two-decimal display rule.
 * This is display formatting only; it must not be used for financial calculations.
 */
export function money(value) {
  const [whole, fraction = ''] = String(value ?? '0').split('.')
  return whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + '.' + fraction.padEnd(2, '0').slice(0, 2)
}
