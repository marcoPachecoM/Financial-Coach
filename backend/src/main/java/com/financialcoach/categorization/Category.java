package com.financialcoach.categorization;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Allowed transaction categories: the {@code Category} enum of openapi.yaml and the rows of the
 * {@code categories} table (V3 migration). CategoryTableTest fails if the two drift apart.
 *
 * <p>The label (e.g. "Higiene personal") is what the API and the database use; the constant
 * name is only for Java code.
 */
public enum Category {
    UTILITIES("Utilities"),
    OTHERS("Others"),
    HOUSEHOLD("Household"),
    ENTERTAINMENT("Entertainment"),
    SERVICES("Services"),
    ELECTRONICS("Electronics"),
    FOOD("Food"),
    EDUCATION("Education"),
    PET("Pet"),
    EMPLOYER("Employer"),
    TRAVEL("Travel"),
    HEALTH("Health"),
    SNACKS("Snacks"),
    DRINK("Drink"),
    SHOPPING("Shopping"),
    PERSONAL("Personal"),
    HOUSE("House"),
    BEBIDA("Bebida"),
    OTHER("Other"),
    HIGIENE_PERSONAL("Higiene personal"),
    GIFT("Gift"),
    CAR("Car"),
    ASEO_PERSONAL("Aseo personal"),
    CURRENCIES("Currencies"),
    HOME("Home"),
    SALARY("Salary");

    private static final Map<String, Category> BY_LABEL = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(Category::label, Function.identity()));

    private final String label;

    Category(String label) {
        this.label = label;
    }

    /** Used by Jackson to write and read the category ("Higiene personal", not HIGIENE_PERSONAL). */
    @JsonValue
    public String label() {
        return label;
    }

    /** Exact match on the label. */
    public static Category fromLabel(String label) {
        Category category = BY_LABEL.get(label);
        if (category == null) {
            throw new IllegalArgumentException("Unknown category: " + label);
        }
        return category;
    }
}
