from enum import StrEnum

from sqlalchemy.orm import Mapped, mapped_column

from app.extensions.database import Base


class Source(StrEnum):
    RULE = "rule"
    LLM = "llm"


class CategoriesCache(Base):
    __tablename__ = "categories_cache"

    id: Mapped[int] = mapped_column(primary_key=True)
    category: Mapped[str] = mapped_column(nullable=False, index=True)
    provider_name: Mapped[str] = mapped_column(unique=True, nullable=False, index=True)
    source: Mapped[Source] = mapped_column(nullable=False, index=True)
