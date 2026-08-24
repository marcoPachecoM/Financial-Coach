from marshmallow import Schema, fields


class ConnectionSchema(Schema):
    id = fields.Integer(required=True)
    user_id = fields.String(required=True)
    provider = fields.String(required=True)
    name = fields.String(required=True)
    external_id = fields.String(required=True)
    status = fields.String(required=True)
