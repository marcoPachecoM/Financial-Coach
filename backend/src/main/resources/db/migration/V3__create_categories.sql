-- Lookup table with the allowed transaction categories (the Category enum of the API contract).
-- Defined once and referenced by foreign keys. To add or rename a category, write a new migration.

CREATE TABLE categories (
    name varchar(50) PRIMARY KEY
);

INSERT INTO categories (name) VALUES
    ('Utilities'),
    ('Others'),
    ('Household'),
    ('Entertainment'),
    ('Services'),
    ('Electronics'),
    ('Food'),
    ('Education'),
    ('Pet'),
    ('Employer'),
    ('Travel'),
    ('Health'),
    ('Snacks'),
    ('Drink'),
    ('Shopping'),
    ('Personal'),
    ('House'),
    ('Bebida'),
    ('Other'),
    ('Higiene personal'),
    ('Gift'),
    ('Car'),
    ('Aseo personal'),
    ('Currencies'),
    ('Home'),
    ('Salary');
