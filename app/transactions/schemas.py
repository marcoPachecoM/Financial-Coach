from marshmallow import Schema, ValidationError, fields, validate


class TransactionSchema(Schema):
    id = fields.UUID(dump_only=True)

    amount = fields.Decimal(required=True)

    description = fields.Str(
        required=False,
        validate=validate.Length(min=1, max=255),
    )

    date = fields.DateTime(required=True)

    category = fields.Str(
        required=True,
        validate=validate.Length(min=1, max=100),
    )

    super_category = fields.Str(
        required=False,
        validate=validate.Length(min=1, max=100),
    )


class TransactionQuerySchema(Schema):
    connection_id = fields.Integer(
        required=True,
        validate=validate.Range(min=1),
    )

    page = fields.Integer(
        load_default=1,
        validate=validate.Range(min=1),
    )

    size = fields.Integer(
        load_default=10,
        validate=validate.Range(min=1),
    )

    super_category = fields.Str(
        allow_none=True,
        validate=validate.Length(min=1, max=100),
    )

    start_date = fields.Date(
        allow_none=True,
    )

    end_date = fields.Date(
        allow_none=True,
    )


class PaginatedTransactionSchema(Schema):
    content = fields.List(
        fields.Nested(TransactionSchema),
        required=True,
    )

    page = fields.Integer(required=True)

    size = fields.Integer(required=True)

    total_elements = fields.Integer(required=True)

    total_pages = fields.Integer(required=True)

    has_more = fields.Boolean(required=True)


def validate_no_unknown_fields(schema: Schema, payload: dict) -> None:
    unknown = set(payload.keys()) - set(schema.fields.keys())
    if unknown:
        raise ValidationError({"unknown_fields": sorted(unknown)})
