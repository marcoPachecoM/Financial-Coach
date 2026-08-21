from marshmallow import Schema, ValidationError, fields, validate, validates_schema


class AnalyticsQuerySchema(Schema):
    connection_id = fields.Integer(
        required=True,
        validate=validate.Range(min=1),
    )

    group_by = fields.Str(
        required=False,
        validate=validate.OneOf(["super_category", "category"]),
    )

    start_date = fields.Date(
        allow_none=True,
        load_default=None,
    )

    end_date = fields.Date(
        allow_none=True,
        load_default=None,
    )

    @validates_schema
    def validate_dates(self, data, **kwargs):
        start_date = data.get("start_date")
        end_date = data.get("end_date")

        if start_date and end_date and start_date > end_date:
            raise ValidationError(
                {
                    "start_date": ["start_date must be before or equal to end_date."],
                    "end_date": ["end_date must be after or equal to start_date."],
                },
            )


class AnalyticsSummarySchema(Schema):
    total_spent = fields.Decimal(
        required=True,
        places=2,
    )

    average_transaction = fields.Decimal(
        required=True,
        places=2,
    )

    largest_transaction = fields.Decimal(
        required=True,
        places=2,
    )

    transaction_count = fields.Integer(required=True)


class AnalyticsCategoryBreakdownSchema(Schema):
    category_name = fields.String(required=True)
    total_amount = fields.Decimal(
        required=True,
        places=2,
    )
    percentage = fields.Decimal(
        required=True,
        places=2,
    )
