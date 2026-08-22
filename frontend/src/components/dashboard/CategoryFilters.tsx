import React from 'react';

export interface CategoryFilterItem {
  id: string;
  label: string;
  icon: string;
}

const CATEGORY_FILTERS: CategoryFilterItem[] = [
  { id: 'all', label: 'All', icon: 'apps' },
  { id: 'Groceries', label: 'Groceries', icon: 'shopping_basket' },
  { id: 'Food & Dining', label: 'Food & Dining', icon: 'restaurant' },
  { id: 'Entertainment', label: 'Entertainment', icon: 'movie' },
  { id: 'Subscriptions', label: 'Subscriptions', icon: 'laptop_mac' },
  { id: 'Transport', label: 'Transportation', icon: 'local_taxi' },
  { id: 'Utilities', label: 'Utilities', icon: 'bolt' },
];

interface CategoryFiltersProps {
  selectedCategory: string;
  onSelectCategory: (category: string) => void;
}

export const CategoryFilters: React.FC<CategoryFiltersProps> = ({
  selectedCategory,
  onSelectCategory,
}) => {
  return (
    <div className="flex items-center gap-1.5 overflow-x-auto pb-1 scrollbar-none no-scrollbar">
      {CATEGORY_FILTERS.map((cat) => {
        const isSelected = selectedCategory === cat.id;
        return (
          <button
            key={cat.id}
            onClick={() => onSelectCategory(cat.id)}
            className={`px-3 py-1.5 rounded-xl text-xs font-medium whitespace-nowrap transition-all flex items-center gap-1.5 cursor-pointer border ${
              isSelected
                ? 'bg-slate-900 text-white border-slate-900 shadow-sm'
                : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50 hover:text-slate-900'
            }`}
          >
            <span className="material-symbols-outlined !text-[15px]">{cat.icon}</span>
            <span>{cat.label}</span>
          </button>
        );
      })}
    </div>
  );
};
