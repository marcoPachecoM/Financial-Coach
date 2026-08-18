from flask import Flask

from app.auth.routes import auth_bp, health_bp
from app.transactions.routes import transactions_bp


def register_blueprints(app: Flask) -> None:
    app.register_blueprint(auth_bp)
    app.register_blueprint(health_bp)
    app.register_blueprint(transactions_bp)
