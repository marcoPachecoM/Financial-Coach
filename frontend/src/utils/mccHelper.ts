import type { MCCCategoryInfo } from '../types/transaction';

export const MCC_DICTIONARY: Record<string, MCCCategoryInfo> = {
  // Groceries & Supermarkets
  '5411': {
    mcc: '5411',
    category: 'Groceries',
    icon: 'shopping_basket',
    badgeBg: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    badgeText: 'text-emerald-700',
    description: 'Grocery Stores, Supermarkets',
  },
  '5499': {
    mcc: '5499',
    category: 'Groceries',
    icon: 'store',
    badgeBg: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    badgeText: 'text-emerald-700',
    description: 'Misc. Food Stores & Convenience',
  },
  // Entertainment & Streaming
  '4899': {
    mcc: '4899',
    category: 'Entertainment',
    icon: 'movie',
    badgeBg: 'bg-purple-50 text-purple-700 border-purple-200',
    badgeText: 'text-purple-700',
    description: 'Cable, Satellite & Streaming Services',
  },
  '7832': {
    mcc: '7832',
    category: 'Entertainment',
    icon: 'theaters',
    badgeBg: 'bg-purple-50 text-purple-700 border-purple-200',
    badgeText: 'text-purple-700',
    description: 'Motion Picture Theaters',
  },
  '7999': {
    mcc: '7999',
    category: 'Entertainment',
    icon: 'sports_esports',
    badgeBg: 'bg-purple-50 text-purple-700 border-purple-200',
    badgeText: 'text-purple-700',
    description: 'Recreation & Gaming Services',
  },
  // Transportation & Rideshare
  '4121': {
    mcc: '4121',
    category: 'Transport',
    icon: 'local_taxi',
    badgeBg: 'bg-amber-50 text-amber-700 border-amber-200',
    badgeText: 'text-amber-700',
    description: 'Taxicabs, Rideshares & Limousines',
  },
  '4111': {
    mcc: '4111',
    category: 'Transport',
    icon: 'train',
    badgeBg: 'bg-amber-50 text-amber-700 border-amber-200',
    badgeText: 'text-amber-700',
    description: 'Local Commuter Passenger Transportation',
  },
  '5541': {
    mcc: '5541',
    category: 'Transport',
    icon: 'local_gas_station',
    badgeBg: 'bg-amber-50 text-amber-700 border-amber-200',
    badgeText: 'text-amber-700',
    description: 'Service Stations / Gas',
  },
  // Food & Dining / Restaurants
  '5812': {
    mcc: '5812',
    category: 'Food & Dining',
    icon: 'restaurant',
    badgeBg: 'bg-orange-50 text-orange-700 border-orange-200',
    badgeText: 'text-orange-700',
    description: 'Eating Places, Restaurants',
  },
  '5814': {
    mcc: '5814',
    category: 'Food & Dining',
    icon: 'fastfood',
    badgeBg: 'bg-orange-50 text-orange-700 border-orange-200',
    badgeText: 'text-orange-700',
    description: 'Fast Food Restaurants & Cafes',
  },
  // Subscriptions & SaaS
  '5734': {
    mcc: '5734',
    category: 'Subscriptions',
    icon: 'laptop_mac',
    badgeBg: 'bg-blue-50 text-blue-700 border-blue-200',
    badgeText: 'text-blue-700',
    description: 'Computer Software Stores & SaaS',
  },
  // Utilities
  '4900': {
    mcc: '4900',
    category: 'Utilities',
    icon: 'bolt',
    badgeBg: 'bg-cyan-50 text-cyan-700 border-cyan-200',
    badgeText: 'text-cyan-700',
    description: 'Electric, Gas, Sanitary & Water Utilities',
  },
  // Shopping
  '5311': {
    mcc: '5311',
    category: 'Shopping',
    icon: 'shopping_bag',
    badgeBg: 'bg-pink-50 text-pink-700 border-pink-200',
    badgeText: 'text-pink-700',
    description: 'Department Stores & Retail Goods',
  },
  '5999': {
    mcc: '5999',
    category: 'Shopping',
    icon: 'storefront',
    badgeBg: 'bg-pink-50 text-pink-700 border-pink-200',
    badgeText: 'text-pink-700',
    description: 'Miscellaneous & Specialty Retail',
  },
};

const DEFAULT_MCC_INFO: MCCCategoryInfo = {
  mcc: '0000',
  category: 'General',
  icon: 'receipt_long',
  badgeBg: 'bg-slate-100 text-slate-700 border-slate-200',
  badgeText: 'text-slate-700',
  description: 'General Merchandise / Services',
};

export function getCategoryFromMCC(mcc: string): MCCCategoryInfo {
  const cleanMcc = mcc ? String(mcc).trim() : '';
  return MCC_DICTIONARY[cleanMcc] || {
    ...DEFAULT_MCC_INFO,
    mcc: cleanMcc || '0000',
  };
}
