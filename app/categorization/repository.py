from sqlalchemy import select

from app.categorization.model import CategoriesCache
from app.extensions.database import db


class CategoriesCacheRepository:
    @staticmethod
    def get_category_by_provider(provider_name: str) -> CategoriesCache | None:
        stmt = select(CategoriesCache).where(CategoriesCache.provider_name == provider_name)
        return db.session.scalar(stmt)

    @staticmethod
    def create(category: CategoriesCache) -> CategoriesCache:
        db.session.add(category)
        db.session.commit()
        return category

    @staticmethod
    def save_many(
        categories: list[CategoriesCache],
    ) -> None:
        if not categories:
            return

        unique_categories = {
            category.provider_name: category for category in categories if category.provider_name
        }

        if not unique_categories:
            return

        provider_names = set(unique_categories)

        existing = db.session.scalars(
            select(CategoriesCache).where(CategoriesCache.provider_name.in_(provider_names))
        ).all()

        existing_by_provider = {category.provider_name: category for category in existing}

        for provider_name, category in unique_categories.items():
            existing_category = existing_by_provider.get(provider_name)

            if existing_category is None:
                db.session.add(category)
                continue

            existing_category.category = category.category
            existing_category.source = category.source

        db.session.commit()
