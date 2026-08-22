from flask import Flask

from app.analytics.routes import analytics_bp
from app.auth.routes import auth_bp, health_bp
from app.copilot.routes import copilot_bp
from app.transactions.routes import transactions_bp

blueprints = [
    auth_bp,
    health_bp,
    transactions_bp,
    analytics_bp,
    copilot_bp,
]


def register_blueprints(app: Flask) -> None:
    for bp in blueprints:
        app.register_blueprint(bp)
