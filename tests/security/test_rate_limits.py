def test_login_rate_limit_enabled_in_non_testing_config():
    from app import create_app

    app = create_app("development")
    assert app.config["RATELIMIT_STORAGE_URI"]
