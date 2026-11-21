import React from 'react';
import { getCategoryStyle } from '../../utils/categoryHelper';

interface CategoryFiltersProps {
  /** Category groups with spending in the period, largest first (from /analytics/by-category-group). */
  categories: string[];
  selectedCategory: string;
  onSelectCategory: (category: string) => void;
}

const MAX_FILTERS = 8;

export const CategoryFilters: React.FC<CategoryFiltersProps> = ({
  categories,
  selectedCategory,
  onSelectCategory,
}) => {
  const filters = [
    { id: 'all', label: 'All', icon: 'apps' },
    ...categories.slice(0, MAX_FILTERS).map((category) => ({
      id: category,
      label: category,
      icon: getCategoryStyle(category).icon,
    })),
  ];

  return (
    <div className="flex items-center gap-1.5 overflow-x-auto pb-1 scrollbar-none no-scrollbar">
      {filters.map((cat) => {
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
