from marshmallow import Schema, ValidationError, fields, validate


class ChatBodySchema(Schema):
    message = fields.Str(
        required=True,
        validate=validate.Length(min=1, max=1000),
    )
    connection_id = fields.Integer(
        required=True,
        validate=validate.Range(min=1),
    )


class ChatResponseSchema(Schema):
    response = fields.Str(required=True)
    provider = fields.Str(required=True)
    model = fields.Str(required=True)


def validate_no_unknown_fields(schema: Schema, payload: dict) -> None:
    unknown = set(payload.keys()) - set(schema.fields.keys())
    if unknown:
        raise ValidationError({"unknown_fields": sorted(unknown)})
