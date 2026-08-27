from __future__ import annotations

import re
from typing import Any

from common import REFUSAL_MARKER


LABEL_PATTERN = re.compile(r"\[(?P<label>[^\]]+)\]")


def extract_labels(text: str) -> list[str]:
    return [match.group("label") for match in LABEL_PATTERN.finditer(text)]


def score_case(
    case: dict[str, Any],
    answer: str | dict[str, Any],
    available_labels: set[str] | dict[str, dict[str, Any]],
) -> dict[str, Any]:
    if case.get("verificationStatus") != "verified":
        return {"id": case["id"], "graded": False, "passed": None, "reason": "case is not verified"}

    structured = answer if isinstance(answer, dict) else None
    answer_text = str(answer.get("answer", "") if structured else answer).strip()
    answerable_value = answer.get("answerable") if structured else None
    cited_labels = (
        [str(citation.get("sourceId", "")) for citation in answer.get("citations", [])]
        if structured
        else extract_labels(answer_text)
    )
    inline_labels = extract_labels(answer_text)
    available_ids = set(available_labels)
    unresolved_labels = sorted(label for label in cited_labels if label not in available_ids)
    missing_inline_citations = bool(case["answerable"] and available_ids and not inline_labels)
    citation_contract_mismatch = bool(structured and set(inline_labels) != set(cited_labels))

    refusal_expected = not case["answerable"]
    refusal_ok = answer_text == REFUSAL_MARKER if refusal_expected else answer_text != REFUSAL_MARKER
    answerable_ok = answerable_value is None or bool(answerable_value) == bool(case["answerable"])

    missing_required: list[str] = []
    ungradable_required: list[str] = []
    for fact in case.get("requiredFacts", []):
        if fact.get("status", "verified") != "verified":
            continue
        patterns = fact.get("acceptedPatterns", [])
        value = fact.get("value")
        if value is not None:
            patterns = [re.escape(str(value)), *patterns]
        if not patterns:
            ungradable_required.append(str(fact["id"]))
        elif not any(re.search(pattern, answer_text, re.IGNORECASE) for pattern in patterns):
            missing_required.append(str(fact["id"]))

    forbidden_matches: list[str] = []
    for claim in case.get("forbiddenClaims", []):
        patterns = claim.get("patterns", [])
        if any(re.search(pattern, answer_text, re.IGNORECASE) for pattern in patterns):
            forbidden_matches.append(str(claim["id"]))

    cited_evidence = []
    if isinstance(available_labels, dict):
        cited_evidence = [available_labels[label] for label in cited_labels if label in available_labels]
    cited_accessions = {str(evidence.get("accession")) for evidence in cited_evidence if evidence.get("accession")}
    cited_sections = {str(evidence.get("sectionItem")) for evidence in cited_evidence if evidence.get("sectionItem")}
    missing_accessions = sorted(set(case.get("expectedAccessions", [])) - cited_accessions)
    missing_sections = sorted(set(case.get("expectedSections", [])) - cited_sections)

    passed = all(
        (
            refusal_ok,
            answerable_ok,
            not unresolved_labels,
            not missing_inline_citations,
            not citation_contract_mismatch,
            not missing_required,
            not ungradable_required,
            not forbidden_matches,
            not missing_accessions,
            not missing_sections,
        )
    )
    return {
        "id": case["id"],
        "graded": True,
        "passed": passed,
        "refusalExpected": refusal_expected,
        "refusalSatisfied": refusal_ok,
        "answerableFlagSatisfied": answerable_ok,
        "unresolvedLabels": unresolved_labels,
        "missingInlineCitations": missing_inline_citations,
        "citationContractMismatch": citation_contract_mismatch,
        "missingVerifiedRequiredFacts": missing_required,
        "ungradableVerifiedRequiredFacts": ungradable_required,
        "forbiddenClaimMatches": forbidden_matches,
        "missingExpectedAccessions": missing_accessions,
        "missingExpectedSections": missing_sections,
    }
