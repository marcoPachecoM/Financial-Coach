package com.financialcoach.categorization;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores {@link Category} by its label, which is the primary key of the categories table.
 * {@code autoApply} makes every Category attribute of every entity use it.
 */
@Converter(autoApply = true)
public class CategoryConverter implements AttributeConverter<Category, String> {

    @Override
    public String convertToDatabaseColumn(Category category) {
        return category == null ? null : category.label();
    }

    @Override
    public Category convertToEntityAttribute(String label) {
        return label == null ? null : Category.fromLabel(label);
    }
}
