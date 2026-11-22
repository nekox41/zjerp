/**
 * @deprecated Compatibility entry point.
 *
 * Prefer importing from the business-domain modules:
 * - @/api/finance/reimbursements
 * - @/api/finance/ledger
 * - @/api/project/documents
 * - @/api/wbcard
 *
 * This barrel remains during gradual migration so existing pages keep working.
 */
export * from './finance/reimbursements'
export * from './finance/ledger'
export * from './project/documents'
export * from './wbcard'
export { money } from '@/utils/money'
export { financeToken } from '@/utils/financeToken'
