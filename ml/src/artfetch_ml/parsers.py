from __future__ import annotations

import json
import math
import re
from typing import Any


AMOUNT_RE = re.compile(r"([0-9]+(?:\.[0-9]+)?)")
YEAR_RE = re.compile(r"(19|20)\d{2}")


def parse_extra_data(value: Any) -> dict[str, Any]:
    if value is None:
        return {}
    if isinstance(value, dict):
        return value
    if isinstance(value, str):
        value = value.strip()
        if not value:
            return {}
        try:
            parsed = json.loads(value)
        except json.JSONDecodeError:
            return {}
        return parsed if isinstance(parsed, dict) else {}
    return {}


def extract_transaction_price(extra_data: Any) -> str | None:
    return parse_extra_data(extra_data).get("transactionPrice")


def extract_extra_value(extra_data: Any, key: str) -> str | None:
    value = parse_extra_data(extra_data).get(key)
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def parse_cn_amount_to_number(text: str | None) -> float | None:
    if text is None:
        return None

    normalized = (
        str(text)
        .strip()
        .replace(",", "")
        .replace("，", "")
        .replace("人民币", "")
        .replace("RMB", "")
        .replace("HKD", "")
        .replace("USD", "")
        .replace("EUR", "")
        .replace("元", "")
        .replace("圆", "")
        .replace("约", "")
    )

    if not normalized:
        return None

    match = AMOUNT_RE.search(normalized)
    if not match:
        return None

    number = float(match.group(1))

    if "亿" in normalized:
        number *= 100_000_000
    elif "万" in normalized:
        number *= 10_000
    elif "千" in normalized:
        number *= 1_000

    return number if math.isfinite(number) and number > 0 else None


def parse_price_range(text: str | None) -> tuple[float | None, float | None, float | None]:
    if text is None:
        return None, None, None

    normalized = (
        str(text)
        .strip()
        .replace(",", "")
        .replace("，", "")
        .replace("人民币", "")
        .replace("RMB", "")
        .replace("HKD", "")
        .replace("USD", "")
        .replace("EUR", "")
        .replace("元", "")
        .replace("圆", "")
        .replace("约", "")
        .replace("估价", "")
        .replace(":", "")
        .replace("：", "")
    )

    if not normalized:
        return None, None, None

    numbers = [float(value) for value in AMOUNT_RE.findall(normalized)]
    if not numbers:
        return None, None, None

    multiplier = 1.0
    if "亿" in normalized:
        multiplier = 100_000_000
    elif "万" in normalized:
        multiplier = 10_000
    elif "千" in normalized:
        multiplier = 1_000

    scaled = [value * multiplier for value in numbers[:2]]
    if len(scaled) == 1:
        return scaled[0], scaled[0], scaled[0]

    low, high = min(scaled), max(scaled)
    return low, high, (low + high) / 2


def parse_dimensions(text: str | None) -> dict[str, float | None]:
    result = {
        "dimension_1": None,
        "dimension_2": None,
        "dimension_3": None,
        "dimension_area": None,
        "dimension_volume": None,
    }
    if text is None:
        return result

    numbers = [float(value) for value in AMOUNT_RE.findall(str(text).replace(",", ""))]
    if not numbers:
        return result

    dims = numbers[:3]
    for index, value in enumerate(dims, start=1):
        result[f"dimension_{index}"] = value

    if len(dims) >= 2:
        result["dimension_area"] = dims[0] * dims[1]
    if len(dims) >= 3:
        result["dimension_volume"] = dims[0] * dims[1] * dims[2]
    return result


def parse_auction_year(text: str | None) -> int | None:
    if text is None:
        return None
    match = YEAR_RE.search(str(text))
    return int(match.group(0)) if match else None
