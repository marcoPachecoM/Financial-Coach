package com.financialcoach.categorization;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores {@link CategoryGroup} by its label, which is the primary key of the category_groups table.
 * {@code autoApply} makes every CategoryGroup attribute of every entity use it.
 */
@Converter(autoApply = true)
public class CategoryGroupConverter implements AttributeConverter<CategoryGroup, String> {

    @Override
    public String convertToDatabaseColumn(CategoryGroup group) {
        return group == null ? null : group.label();
    }

    @Override
    public CategoryGroup convertToEntityAttribute(String label) {
        return label == null ? null : CategoryGroup.fromLabel(label);
    }
}
