from __future__ import annotations

from dataclasses import dataclass
import os

import pandas as pd
from sqlalchemy import create_engine, text


@dataclass(frozen=True)
class DatabaseConfig:
    host: str = "postgres"
    port: int = 5432
    database: str = "artfetch"
    username: str = "artfetch"
    password: str = "artfetch123"

    @classmethod
    def from_env(cls) -> "DatabaseConfig":
        return cls(
            host=os.getenv("ARTFETCH_DB_HOST", "postgres"),
            port=int(os.getenv("ARTFETCH_DB_PORT", "5432")),
            database=os.getenv("ARTFETCH_DB_NAME", "artfetch"),
            username=os.getenv("ARTFETCH_DB_USER", "artfetch"),
            password=os.getenv("ARTFETCH_DB_PASSWORD", "artfetch123"),
        )

    def sqlalchemy_url(self) -> str:
        return (
            f"postgresql+psycopg2://{self.username}:{self.password}"
            f"@{self.host}:{self.port}/{self.database}"
        )


def create_sqlalchemy_engine(config: DatabaseConfig | None = None):
    cfg = config or DatabaseConfig.from_env()
    return create_engine(cfg.sqlalchemy_url())


def fetch_artwork_training_frame(
    task_id: int | None = None,
    limit: int | None = None,
    config: DatabaseConfig | None = None,
) -> pd.DataFrame:
    engine = create_sqlalchemy_engine(config)

    sql = """
        SELECT
            id,
            task_id,
            external_id,
            title,
            lot_number,
            artist,
            medium,
            format,
            dimensions,
            valuation,
            auction_house,
            auction_name,
            auction_session,
            auction_date,
            auction_location,
            preview_time,
            preview_location,
            extra_data,
            created_at
        FROM artworks
        WHERE (:task_id IS NULL OR task_id = :task_id)
        ORDER BY id DESC
    """

    params: dict[str, int | None] = {"task_id": task_id}
    if limit is not None:
        sql += "\nLIMIT :limit"
        params["limit"] = limit

    with engine.connect() as connection:
        return pd.read_sql_query(text(sql), connection, params=params)
