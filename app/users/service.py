from app.users.repository import UserRepository


class UserService:
    def __init__(self, repository: UserRepository | None = None) -> None:
        self.repository = repository or UserRepository()

    def get_user_by_public_id(self, public_id: str):
        return self.repository.get_by_public_id(public_id)
