import enum

from sqlalchemy import ForeignKey, String
from sqlalchemy.orm import Mapped, mapped_column

from app.extensions.database import Base


class Status(str, enum.Enum):
    ACTIVE = "ACTIVE"
    INACTIVE = "INACTIVE"
    SYNCING = "SYNCING"
    ERROR = "ERROR"


class Connection(Base):
    __tablename__ = "connections"

    id: Mapped[int] = mapped_column(primary_key=True)

    user_id: Mapped[str] = mapped_column(
        ForeignKey("users.public_id"),
        nullable=False,
        index=True,
    )

    provider: Mapped[str] = mapped_column(
        String(64),
        nullable=False,
        index=True,
    )

    name: Mapped[str] = mapped_column(
        String(64),
        nullable=False,
        index=True,
    )

    external_id: Mapped[str] = mapped_column(
        String(64),
        nullable=False,
        index=True,
    )

    status: Mapped[Status] = mapped_column(
        String(16),
        default=Status.ACTIVE,
        nullable=False,
        index=True,
    )
