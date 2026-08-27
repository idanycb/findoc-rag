import json
import urllib.error

from common import load_dataset
from run_eval import gemini_request, render_context


def test_all_verified_cases_render_corpus_checked_context():
    cases = load_dataset("tesla-2025-v1")

    rendered = [render_context(case, "tesla-2025") for case in cases if case["verificationStatus"] == "verified"]

    assert len(rendered) == 12
    assert any("amend Part III, Items 10, 11, 12, 13 and 14" in context for context, _ in rendered)
    assert all("embeddingId" not in context for context, _ in rendered)
    assert all(set(sources).issubset({"S1", "S2"}) for _, sources in rendered)


def test_gemini_request_uses_structured_production_contract_without_key_in_url():
    captured = {}

    class Response:
        def __enter__(self):
            return self

        def __exit__(self, *_):
            return None

        def read(self):
            return json.dumps(
                {
                    "candidates": [
                        {
                            "content": {
                                "parts": [
                                    {
                                        "text": json.dumps(
                                            {
                                                "answerable": True,
                                                "answer": "Supported [S1]",
                                                "citations": [{"sourceId": "S1"}],
                                            }
                                        )
                                    }
                                ]
                            },
                            "finishReason": "STOP",
                        }
                    ],
                    "usageMetadata": {"totalTokenCount": 42},
                }
            ).encode()

    def opener(request, timeout):
        captured["request"] = request
        captured["timeout"] = timeout
        return Response()

    result = gemini_request(
        {"systemInstruction": "system", "question": "question"},
        "secret-key",
        opener=opener,
    )

    request = captured["request"]
    payload = json.loads(request.data)
    assert "secret-key" not in request.full_url
    assert request.headers["X-goog-api-key"] == "secret-key"
    assert payload["generationConfig"]["temperature"] == 0
    assert payload["generationConfig"]["seed"] == 42
    assert payload["generationConfig"]["responseMimeType"] == "application/json"
    assert result["structured"]["citations"] == [{"sourceId": "S1"}]
    assert result["usageMetadata"]["totalTokenCount"] == 42


def test_gemini_request_honors_retry_after():
    calls = 0
    sleeps = []

    class Response:
        def __enter__(self):
            return self

        def __exit__(self, *_):
            return None

        def read(self):
            answer = {"answerable": False, "answer": "No evidence", "citations": []}
            return json.dumps({"candidates": [{"content": {"parts": [{"text": json.dumps(answer)}]}}]}).encode()

    def opener(request, timeout):
        nonlocal calls
        calls += 1
        if calls == 1:
            raise urllib.error.HTTPError(request.full_url, 429, "limited", {"Retry-After": "0.01"}, None)
        return Response()

    gemini_request(
        {"systemInstruction": "system", "question": "question"},
        "secret-key",
        opener=opener,
        sleeper=sleeps.append,
    )

    assert calls == 2
    assert sleeps == [0.01]
