from __future__ import annotations

import pandas as pd

from .parsers import (
    extract_extra_value,
    extract_transaction_price,
    parse_auction_year,
    parse_cn_amount_to_number,
    parse_dimensions,
    parse_price_range,
)


NUMERIC_FEATURES = [
    "title_length",
    "auction_year",
    "valuation_low",
    "valuation_high",
    "valuation_mean",
    "dimension_1",
    "dimension_2",
    "dimension_3",
    "dimension_area",
    "dimension_volume",
]

CATEGORICAL_FEATURES = [
    "artist",
    "medium",
    "format",
    "auction_house",
    "auction_name",
    "auction_session",
    "auction_location",
    "creation_era",
    "category_level1",
    "category_level2",
    "currency",
]

TARGET_COLUMN = "target_price"


def prepare_training_frame(raw_df: pd.DataFrame) -> pd.DataFrame:
    df = raw_df.copy()

    df["transaction_price_raw"] = df["extra_data"].apply(extract_transaction_price)
    df[TARGET_COLUMN] = df["transaction_price_raw"].apply(parse_cn_amount_to_number)

    valuation_features = df["valuation"].apply(parse_price_range)
    df["valuation_low"] = valuation_features.apply(lambda item: item[0])
    df["valuation_high"] = valuation_features.apply(lambda item: item[1])
    df["valuation_mean"] = valuation_features.apply(lambda item: item[2])

    dimension_features = df["dimensions"].apply(parse_dimensions).apply(pd.Series)
    for column in dimension_features.columns:
        df[column] = dimension_features[column]

    df["auction_year"] = df["auction_date"].apply(parse_auction_year)
    df["title_length"] = df["title"].fillna("").str.len()

    df["creation_era"] = df["extra_data"].apply(lambda value: extract_extra_value(value, "creationEra"))
    df["category_level1"] = df["extra_data"].apply(lambda value: extract_extra_value(value, "categoryLevel1"))
    df["category_level2"] = df["extra_data"].apply(lambda value: extract_extra_value(value, "categoryLevel2"))
    df["currency"] = df["extra_data"].apply(lambda value: extract_extra_value(value, "currency"))

    return df


def get_feature_columns() -> tuple[list[str], list[str]]:
    return NUMERIC_FEATURES.copy(), CATEGORICAL_FEATURES.copy()
