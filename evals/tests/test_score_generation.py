from common import REFUSAL_MARKER
from score_generation import score_case


def test_pending_case_is_not_graded() -> None:
    case = {
        "id": "pending",
        "answerable": True,
        "verificationStatus": "pending",
        "requiredFacts": [],
    }

    result = score_case(case, REFUSAL_MARKER, available_labels=set())

    assert result["graded"] is False
    assert result["passed"] is None


def test_unknown_citation_label_fails() -> None:
    case = {
        "id": "verified",
        "answerable": True,
        "verificationStatus": "verified",
        "requiredFacts": [],
    }

    result = score_case(case, "Answer [missing-label]", available_labels=set())

    assert result["passed"] is False
    assert result["unresolvedLabels"] == ["missing-label"]


def test_structured_refusal_must_be_exact() -> None:
    case = {
        "id": "unanswerable",
        "answerable": False,
        "verificationStatus": "verified",
        "requiredFacts": [],
        "expectedAccessions": [],
        "expectedSections": [],
    }

    result = score_case(
        case,
        {"answerable": False, "answer": REFUSAL_MARKER, "citations": []},
        available_labels=set(),
    )

    assert result["passed"] is True


def test_structured_citations_use_request_source_ids() -> None:
    case = {
        "id": "answerable",
        "answerable": True,
        "verificationStatus": "verified",
        "requiredFacts": [],
        "expectedAccessions": [],
        "expectedSections": [],
    }

    result = score_case(
        case,
        {"answerable": True, "answer": "Supported.[S1]", "citations": [{"sourceId": "S1"}]},
        available_labels={"S1"},
    )

    assert result["passed"] is True


def test_structured_citation_must_also_appear_inline() -> None:
    case = {
        "id": "answerable",
        "answerable": True,
        "verificationStatus": "verified",
        "requiredFacts": [],
        "expectedAccessions": [],
        "expectedSections": [],
    }

    result = score_case(
        case,
        {"answerable": True, "answer": "Supported.", "citations": [{"sourceId": "S1"}]},
        available_labels={"S1"},
    )

    assert result["passed"] is False
    assert result["missingInlineCitations"] is True
    assert result["citationContractMismatch"] is True
