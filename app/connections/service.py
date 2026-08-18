from repository import ConnectionRepository


class ConnectionService:
    def __init__(self, connection_repository=ConnectionRepository()):
        self.connection_repository = connection_repository

    def create_connection(self, connection_data):
        return self.connection_repository.create(connection_data)

    def get_connection(self, connection_id):
        return self.connection_repository.get_by_id(connection_id)
