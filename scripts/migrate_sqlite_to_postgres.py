#!/usr/bin/env python3
import argparse
from datetime import date, datetime

from sqlalchemy import Boolean, Date, DateTime, MetaData, create_engine, select, text


def convert_value(column, value):
    if value is None:
        return None
    if isinstance(column.type, Boolean):
        return bool(value)
    if isinstance(column.type, DateTime) and isinstance(value, str):
        return datetime.fromisoformat(value)
    if isinstance(column.type, Date) and not isinstance(column.type, DateTime) and isinstance(value, str):
        return date.fromisoformat(value)
    return value


def main():
    parser = argparse.ArgumentParser(description="Migrate the NBYS SQLite database to PostgreSQL.")
    parser.add_argument("--sqlite", required=True, help="Path to the source SQLite database")
    parser.add_argument("--postgres", required=True, help="SQLAlchemy PostgreSQL URL")
    args = parser.parse_args()

    source = create_engine(f"sqlite:///{args.sqlite}")
    target = create_engine(args.postgres)
    source_metadata = MetaData()
    target_metadata = MetaData()
    source_metadata.reflect(bind=source)
    target_metadata.reflect(bind=target)

    with target.begin() as target_conn:
        target_conn.execute(text("SET session_replication_role = replica"))
        for table in reversed(target_metadata.sorted_tables):
            target_conn.execute(table.delete())

        with source.connect() as source_conn:
            for table in source_metadata.sorted_tables:
                target_table = target_metadata.tables.get(table.name)
                if target_table is None:
                    continue
                rows = source_conn.execute(select(table)).mappings().all()
                if not rows:
                    continue
                payload = []
                for row in rows:
                    payload.append({
                        column.name: convert_value(column, row[column.name])
                        for column in target_table.columns
                        if column.name in row
                    })
                target_conn.execute(target_table.insert(), payload)

        target_conn.execute(text("SET session_replication_role = origin"))
        for table in target_metadata.sorted_tables:
            primary_keys = list(table.primary_key.columns)
            if len(primary_keys) != 1:
                continue
            primary_key = primary_keys[0]
            if not primary_key.autoincrement:
                continue
            target_conn.execute(text(
                "SELECT setval(pg_get_serial_sequence(:table_name, :column_name), "
                "COALESCE((SELECT MAX(" + primary_key.name + ") FROM " + table.name + "), 1), "
                "(SELECT COUNT(*) > 0 FROM " + table.name + "))"
            ), {"table_name": table.name, "column_name": primary_key.name})

    print(f"Migrated {len(source_metadata.tables)} tables.")


if __name__ == "__main__":
    main()
