import React from 'react';
import { getCategoryStyle } from '../../utils/categoryHelper';

interface CategoryBadgeProps {
  /** Category group (one of the 26); colors and icon depend on it. */
  group: string;
}

export const CategoryBadge: React.FC<CategoryBadgeProps> = ({ group }) => {
  const style = getCategoryStyle(group);

  return (
    <span
      className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium border ${style.badgeBg}`}
      title={style.description}
    >
      <span className="material-symbols-outlined !text-[13px]">{style.icon}</span>
      <span>{group}</span>
    </span>
  );
};
