from unittest.mock import MagicMock, patch

from tools.record_corpus import fetch_json


def test_fetch_json_uses_sidecar_query_contract() -> None:
    response = MagicMock()
    response.__enter__.return_value.read.return_value = b"{}"

    with patch("tools.record_corpus.urllib.request.urlopen", return_value=response) as urlopen:
        assert fetch_json(
            "http://localhost:8100/filings/sections",
            "TSLA",
            "0001318605-25-000001",
        ) == b"{}"

    urlopen.assert_called_once_with(
        "http://localhost:8100/filings/sections?ticker=TSLA&accession=0001318605-25-000001"
    )
