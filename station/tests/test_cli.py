import pytest

from station.cli import main


def test_version(capsys):
    with pytest.raises(SystemExit) as exit_info:
        main(["--version"])
    assert exit_info.value.code == 0
    assert capsys.readouterr().out.strip() == "station 0.1.0"


def test_no_args_prints_help(capsys):
    assert main([]) == 0
    assert "Spindle Station" in capsys.readouterr().out
