from marshmallow import Schema, ValidationError, fields, validate


class LoginSchema(Schema):
    email = fields.Email(required=True, validate=validate.Length(min=1, max=255))
    password = fields.Str(required=True, validate=validate.Length(min=1, max=1024))


class RefreshSchema(Schema):
    refresh_token = fields.Str(required=True, validate=validate.Length(min=20, max=4096))


class UserOutputSchema(Schema):
    id = fields.UUID(attribute="public_id", dump_only=True)
    username = fields.Str(dump_only=True)
    email = fields.Email(dump_only=True)
    role = fields.Str(dump_only=True)
    is_active = fields.Bool(dump_only=True)


def validate_no_unknown_fields(schema: Schema, payload: dict) -> None:
    unknown = set(payload.keys()) - set(schema.fields.keys())
    if unknown:
        raise ValidationError({"unknown_fields": sorted(unknown)})
