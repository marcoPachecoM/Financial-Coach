from marshmallow import Schema, fields, validate


class TransactionSchema(Schema):
    id = fields.UUID(dump_only=True)
    amount = fields.Float(required=True)
    currency = fields.Str(required=True, validate=validate.Length(min=1, max=3))
    description = fields.Str(required=True, validate=validate.Length(min=1, max=255))
    date = fields.DateTime(required=True)
    category = fields.Str(required=True, validate=validate.Length(min=1, max=100))
    account_id = fields.UUID(required=True)
