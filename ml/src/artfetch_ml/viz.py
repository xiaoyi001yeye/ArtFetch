from __future__ import annotations

from matplotlib import font_manager, pyplot as plt
import seaborn as sns


CANDIDATE_CHINESE_FONTS = [
    "Noto Sans CJK SC",
    "Noto Serif CJK SC",
    "Noto Sans CJK JP",
    "Noto Serif CJK JP",
    "Microsoft YaHei",
    "PingFang SC",
    "SimHei",
]


def configure_matplotlib_for_chinese() -> str | None:
    available_fonts = {font.name for font in font_manager.fontManager.ttflist}
    selected_font = next(
        (font_name for font_name in CANDIDATE_CHINESE_FONTS if font_name in available_fonts),
        None,
    )

    rc = {
        "axes.unicode_minus": False,
    }
    if selected_font is not None:
        rc["font.family"] = "sans-serif"
        rc["font.sans-serif"] = [selected_font, "DejaVu Sans"]

    sns.set_theme(style="whitegrid", rc=rc)
    return selected_font
