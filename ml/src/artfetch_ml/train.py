from __future__ import annotations

from dataclasses import dataclass

import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.dummy import DummyRegressor
from sklearn.impute import SimpleImputer
from sklearn.metrics import mean_absolute_error, r2_score, root_mean_squared_error
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder
from xgboost import XGBRegressor

from .features import TARGET_COLUMN, get_feature_columns, prepare_training_frame


@dataclass
class TrainingResult:
    prepared_frame: pd.DataFrame
    predictions: pd.DataFrame
    feature_importance: pd.DataFrame
    baseline_metrics: dict[str, float]
    model_metrics: dict[str, float]
    pipeline: Pipeline

    def metrics_frame(self) -> pd.DataFrame:
        return pd.DataFrame(
            [
                {"model": "baseline_mean", **self.baseline_metrics},
                {"model": "xgboost", **self.model_metrics},
            ]
        )


def build_default_xgb_params() -> dict[str, float | int]:
    return {
        "objective": "reg:squarederror",
        "n_estimators": 400,
        "max_depth": 6,
        "learning_rate": 0.05,
        "subsample": 0.9,
        "colsample_bytree": 0.8,
        "reg_lambda": 1.0,
        "random_state": 42,
        "n_jobs": 4,
        "tree_method": "hist",
    }


def train_price_model(
    raw_df: pd.DataFrame,
    test_size: float = 0.2,
    random_state: int = 42,
    xgb_params: dict[str, float | int] | None = None,
) -> TrainingResult:
    prepared = prepare_training_frame(raw_df)
    filtered = prepared.loc[prepared[TARGET_COLUMN].notna()].copy()

    if len(filtered) < 30:
        raise ValueError("可用于训练的成交价样本不足，至少需要 30 条。")

    numeric_features, categorical_features = get_feature_columns()
    feature_columns = numeric_features + categorical_features

    X = filtered[feature_columns].copy()
    y = filtered[TARGET_COLUMN].copy()
    metadata = filtered[["id", "title", "artist", "auction_house", "auction_date"]].copy()

    X_train, X_test, y_train, y_test, meta_train, meta_test = train_test_split(
        X,
        y,
        metadata,
        test_size=test_size,
        random_state=random_state,
    )

    baseline = DummyRegressor(strategy="mean")
    baseline_pipeline = Pipeline(
        steps=[
            ("preprocessor", _build_preprocessor(numeric_features, categorical_features)),
            ("model", baseline),
        ]
    )
    baseline_pipeline.fit(X_train, y_train)
    baseline_pred = baseline_pipeline.predict(X_test)

    model = XGBRegressor(**(xgb_params or build_default_xgb_params()))
    pipeline = Pipeline(
        steps=[
            ("preprocessor", _build_preprocessor(numeric_features, categorical_features)),
            ("model", model),
        ]
    )
    pipeline.fit(X_train, y_train)
    pred = pipeline.predict(X_test)

    predictions = meta_test.copy()
    predictions["actual_price"] = y_test.values
    predictions["predicted_price"] = pred
    predictions["absolute_error"] = (predictions["actual_price"] - predictions["predicted_price"]).abs()
    predictions = predictions.sort_values("absolute_error", ascending=False).reset_index(drop=True)

    feature_names = pipeline.named_steps["preprocessor"].get_feature_names_out()
    importance_values = pipeline.named_steps["model"].feature_importances_
    feature_importance = (
        pd.DataFrame({"feature": feature_names, "importance": importance_values})
        .sort_values("importance", ascending=False)
        .reset_index(drop=True)
    )

    return TrainingResult(
        prepared_frame=filtered.reset_index(drop=True),
        predictions=predictions,
        feature_importance=feature_importance,
        baseline_metrics=_regression_metrics(y_test, baseline_pred),
        model_metrics=_regression_metrics(y_test, pred),
        pipeline=pipeline,
    )


def _regression_metrics(y_true, y_pred) -> dict[str, float]:
    return {
        "mae": float(mean_absolute_error(y_true, y_pred)),
        "rmse": float(root_mean_squared_error(y_true, y_pred)),
        "r2": float(r2_score(y_true, y_pred)),
    }


def _build_preprocessor(
    numeric_features: list[str],
    categorical_features: list[str],
) -> ColumnTransformer:
    numeric_transformer = Pipeline(
        steps=[
            ("imputer", SimpleImputer(strategy="median")),
        ]
    )
    categorical_transformer = Pipeline(
        steps=[
            ("imputer", SimpleImputer(strategy="constant", fill_value="__missing__")),
            ("onehot", OneHotEncoder(handle_unknown="ignore", sparse_output=False)),
        ]
    )
    return ColumnTransformer(
        transformers=[
            ("num", numeric_transformer, numeric_features),
            ("cat", categorical_transformer, categorical_features),
        ]
    )
