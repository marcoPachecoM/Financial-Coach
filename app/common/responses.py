from flask import jsonify


def success_response(data: dict, status_code: int = 200):
    return jsonify(data), status_code


def error_response(code: str, message: str, status_code: int):
    return jsonify({"error": {"code": code, "message": message}}), status_code
