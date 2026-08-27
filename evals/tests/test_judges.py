from pathlib import Path

from judges.base import CodexJudge


def test_codex_judge_command_keeps_the_lockdown_flags():
    command = CodexJudge(model="judge-model").build_command(
        Path("prompt.txt"), Path("schema.json"), Path("judgment.json"), Path("empty")
    )

    for flag in (
        "--output-schema",
        "--output-last-message",
        "--sandbox",
        "--cd",
        "--skip-git-repo-check",
        "--ephemeral",
        "--ignore-user-config",
        "--ignore-rules",
        "--json",
    ):
        assert flag in command
    assert command[:4] == ["codex", "exec", "--model", "judge-model"]
