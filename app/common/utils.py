from collections.abc import Mapping, Sequence
from typing import Any


def json_to_toon(data: Any) -> str:
    lines: list[str] = []
    _encode(data, lines)
    return "\n".join(lines)


def _encode(
    value: Any,
    lines: list[str],
    key: str | None = None,
) -> None:
    if isinstance(value, Mapping):
        _encode_mapping(value, lines, key)
        return

    if isinstance(value, Sequence) and not isinstance(value, str | bytes):
        _encode_sequence(value, lines, key)
        return

    if key is not None:
        lines.append(f"{key}: {_format_value(value)}")
    else:
        lines.append(_format_value(value))


def _encode_mapping(
    value: Mapping[str, Any],
    lines: list[str],
    key: str | None,
) -> None:
    if key is not None:
        lines.append(f"{key}:")

    for child_key, child_value in value.items():
        _encode(child_value, lines, child_key)


def _encode_sequence(
    value: Sequence[Any],
    lines: list[str],
    key: str | None,
) -> None:
    if not value:
        if key is not None:
            lines.append(f"{key}[0]:")
        return

    if all(isinstance(item, Mapping) for item in value):
        _encode_object_array(value, lines, key)
        return

    if key is not None:
        lines.append(f"{key}[{len(value)}]:")

    for item in value:
        lines.append(_format_value(item))


def _encode_object_array(
    value: Sequence[Mapping[str, Any]],
    lines: list[str],
    key: str | None,
) -> None:
    columns = list(value[0].keys())

    prefix = f"{key}[{len(value)}]" if key else f"[{len(value)}]"
    lines.append(f"{prefix}{{{','.join(columns)}}}:")

    for item in value:
        row = ",".join(_format_value(item.get(column)) for column in columns)
        lines.append(row)


def _format_value(value: Any) -> str:
    if value is None:
        return "null"

    if isinstance(value, bool):
        return "true" if value else "false"

    return str(value)
