from flask import Blueprint, jsonify, request
from flask_jwt_extended import get_jwt_identity, jwt_required

from app.analytics.schemas import (
    AnalyticsCategoryBreakdownSchema,
    AnalyticsQuerySchema,
    AnalyticsSummarySchema,
)
from app.analytics.service import AnalyticsService
from app.common.exceptions import ValidationError
from app.transactions.schemas import validate_no_unknown_fields

analytics_bp = Blueprint(
    "analytics",
    __name__,
    url_prefix="/api/v1/analytics",
)

_analytics_service = AnalyticsService()
_query_schema = AnalyticsQuerySchema()
_summary_schema = AnalyticsSummarySchema()
_category_breakdown_schema = AnalyticsCategoryBreakdownSchema()


@analytics_bp.get("/summary")
@jwt_required()
def get_summary():
    payload = request.args.to_dict()

    validate_no_unknown_fields(
        _query_schema,
        payload,
    )

    params = _query_schema.load(payload)

    if not isinstance(params, dict):
        raise ValidationError("Invalid query parameters.")

    result = _analytics_service.get_summary(
        user_id=get_jwt_identity(),
        **params,
    )

    return jsonify(_summary_schema.dump(result)), 200


@analytics_bp.get("/by-category")
@jwt_required()
async def get_category_breakdown():
    payload = request.args.to_dict()

    validate_no_unknown_fields(
        _query_schema,
        payload,
    )

    params = _query_schema.load(payload)

    if not isinstance(params, dict):
        raise ValidationError("Invalid query parameters.")

    result = await _analytics_service.get_category_breakdown(
        user_id=get_jwt_identity(),
        **params,
    )

    return jsonify([_category_breakdown_schema.dump(item) for item in result]), 200
