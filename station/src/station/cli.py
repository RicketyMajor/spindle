"""Spindle Station command line."""
import argparse
from importlib.metadata import version


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="station",
        description="Spindle Station: baja, etiqueta y organiza tu música para Spindle.",
    )
    parser.add_argument("--version", action="version", version=f"station {version('spindle-station')}")
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    parser.parse_args(argv)
    parser.print_help()
    return 0
