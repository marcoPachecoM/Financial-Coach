import type { CategoryStyle } from '../types/transaction';

/**
 * The 26 category groups of the API (CategoryGroup enum in docs/openapi.yaml). Class names are written
 * out in full because Tailwind only generates classes it finds literally in the source.
 */
export const CATEGORY_STYLES: Record<string, CategoryStyle> = {
  Food: { icon: 'restaurant', badgeBg: 'bg-orange-50 text-orange-700 border-orange-200', description: 'Groceries, restaurants and meals' },
  Snacks: { icon: 'cookie', badgeBg: 'bg-amber-50 text-amber-700 border-amber-200', description: 'Snacks and convenience stores' },
  Drink: { icon: 'local_cafe', badgeBg: 'bg-yellow-50 text-yellow-700 border-yellow-200', description: 'Coffee and drinks' },
  Bebida: { icon: 'local_bar', badgeBg: 'bg-yellow-50 text-yellow-700 border-yellow-200', description: 'Bebidas' },
  Shopping: { icon: 'shopping_bag', badgeBg: 'bg-pink-50 text-pink-700 border-pink-200', description: 'Retail and online shopping' },
  Electronics: { icon: 'devices', badgeBg: 'bg-blue-50 text-blue-700 border-blue-200', description: 'Electronics and gadgets' },
  Entertainment: { icon: 'movie', badgeBg: 'bg-purple-50 text-purple-700 border-purple-200', description: 'Streaming, movies and leisure' },
  Travel: { icon: 'flight', badgeBg: 'bg-sky-50 text-sky-700 border-sky-200', description: 'Trips, hotels and transport' },
  Car: { icon: 'directions_car', badgeBg: 'bg-red-50 text-red-700 border-red-200', description: 'Fuel, maintenance and parking' },
  Utilities: { icon: 'bolt', badgeBg: 'bg-cyan-50 text-cyan-700 border-cyan-200', description: 'Electricity, water, gas and internet' },
  Services: { icon: 'build', badgeBg: 'bg-slate-100 text-slate-700 border-slate-200', description: 'Professional and subscription services' },
  House: { icon: 'house', badgeBg: 'bg-violet-50 text-violet-700 border-violet-200', description: 'Rent and housing' },
  Home: { icon: 'home', badgeBg: 'bg-violet-50 text-violet-700 border-violet-200', description: 'Home expenses' },
  Household: { icon: 'cleaning_services', badgeBg: 'bg-indigo-50 text-indigo-700 border-indigo-200', description: 'Household supplies' },
  Health: { icon: 'medical_services', badgeBg: 'bg-rose-50 text-rose-700 border-rose-200', description: 'Doctors, pharmacy and insurance' },
  'Higiene personal': { icon: 'soap', badgeBg: 'bg-teal-50 text-teal-700 border-teal-200', description: 'Higiene personal' },
  'Aseo personal': { icon: 'sanitizer', badgeBg: 'bg-teal-50 text-teal-700 border-teal-200', description: 'Aseo personal' },
  Personal: { icon: 'person', badgeBg: 'bg-fuchsia-50 text-fuchsia-700 border-fuchsia-200', description: 'Personal expenses' },
  Education: { icon: 'school', badgeBg: 'bg-blue-50 text-blue-700 border-blue-200', description: 'Courses, books and tuition' },
  Pet: { icon: 'pets', badgeBg: 'bg-lime-50 text-lime-700 border-lime-200', description: 'Pet food and vet' },
  Gift: { icon: 'redeem', badgeBg: 'bg-pink-50 text-pink-700 border-pink-200', description: 'Gifts' },
  Currencies: { icon: 'currency_exchange', badgeBg: 'bg-green-50 text-green-700 border-green-200', description: 'Currency exchange' },
  Salary: { icon: 'payments', badgeBg: 'bg-emerald-50 text-emerald-700 border-emerald-200', description: 'Salary and wages' },
  Employer: { icon: 'work', badgeBg: 'bg-emerald-50 text-emerald-700 border-emerald-200', description: 'Income from an employer' },
  Others: { icon: 'category', badgeBg: 'bg-slate-100 text-slate-700 border-slate-200', description: 'Other expenses' },
  Other: { icon: 'receipt_long', badgeBg: 'bg-slate-100 text-slate-700 border-slate-200', description: 'Not categorized yet' },
};

const DEFAULT_CATEGORY_STYLE: CategoryStyle = {
  icon: 'receipt_long',
  badgeBg: 'bg-slate-100 text-slate-700 border-slate-200',
  description: 'General merchandise and services',
};

export function getCategoryStyle(group: string): CategoryStyle {
  return CATEGORY_STYLES[group] ?? DEFAULT_CATEGORY_STYLE;
}
