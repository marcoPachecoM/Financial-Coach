import type { CategoryStyle } from '../types/transaction';

export const CATEGORY_STYLES: Record<string, CategoryStyle> = {
  Groceries: {
    icon: 'shopping_basket',
    badgeBg: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    description: 'Grocery stores, supermarkets, and convenience stores',
  },

  Entertainment: {
    icon: 'movie',
    badgeBg: 'bg-purple-50 text-purple-700 border-purple-200',
    description: 'Streaming, movies, recreation, and gaming services',
  },

  Transport: {
    icon: 'local_taxi',
    badgeBg: 'bg-amber-50 text-amber-700 border-amber-200',
    description: 'Public transportation, rideshares, taxis, and fuel',
  },

  'Food & Dining': {
    icon: 'restaurant',
    badgeBg: 'bg-orange-50 text-orange-700 border-orange-200',
    description: 'Restaurants, cafes, and fast food establishments',
  },

  Subscriptions: {
    icon: 'laptop_mac',
    badgeBg: 'bg-blue-50 text-blue-700 border-blue-200',
    description: 'Software, SaaS, and recurring subscription services',
  },

  Utilities: {
    icon: 'bolt',
    badgeBg: 'bg-cyan-50 text-cyan-700 border-cyan-200',
    description: 'Electricity, gas, water, and other utility services',
  },

  Shopping: {
    icon: 'shopping_bag',
    badgeBg: 'bg-pink-50 text-pink-700 border-pink-200',
    description: 'Department stores, retail goods, and specialty shops',
  },

  Healthcare: {
    icon: 'medical_services',
    badgeBg: 'bg-red-50 text-red-700 border-red-200',
    description: 'Healthcare providers, pharmacies, and medical services',
  },

  Insurance: {
    icon: 'security',
    badgeBg: 'bg-indigo-50 text-indigo-700 border-indigo-200',
    description: 'Insurance providers and related services',
  },

  'Home & Hardware': {
    icon: 'home_repair_service',
    badgeBg: 'bg-amber-50 text-amber-700 border-amber-200',
    description: 'Home improvement, hardware, and household services',
  },
};

const DEFAULT_CATEGORY_STYLE: CategoryStyle = {
  icon: 'receipt_long',
  badgeBg: 'bg-slate-100 text-slate-700 border-slate-200',
  description: 'General merchandise and services',
};

export function getCategoryStyle(category: string): CategoryStyle {
  return CATEGORY_STYLES[category] ?? DEFAULT_CATEGORY_STYLE;
}