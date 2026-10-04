"""Public synthetic fixture/schema hygiene. Private capture fixtures are never inspected or printed by CI."""
import copy
import json
from pathlib import Path
from jsonschema import Draft202012Validator

root = Path(__file__).resolve().parents[1]
directory = root / 'test-fixtures/front'
schema = json.loads((directory / 'schema.json').read_text(encoding='utf-8'))
Draft202012Validator.check_schema(schema)
validator = Draft202012Validator(schema)
fixtures = list(directory.glob('synthetic-*.json'))
assert fixtures
for path in fixtures:
    fixture = json.loads(path.read_text(encoding='utf-8'))
    validator.validate(fixture)
    assert fixture['input']['origin'] == 'SYNTHETIC' and fixture['policy']['syntheticOnly']
    assert fixture['evidenceKind'] == 'SYNTHETIC_FORMULA'
    assert len({x['metricId'] for x in fixture['expected']}) == len(fixture['expected'])
    # Negative schema tests ensure unknown payloads, physical calibration and media cannot creep in.
    for section, key, value in [('input', 'photo', 'private.jpg'), ('input', 'millimetersPerUnit', 1), ('input', 'modelArtifactSha256', 'unknown'), ('policy', 'minimumConfidence', 1.1)]:
        bad = copy.deepcopy(fixture)
        bad[section][key] = value
        assert not validator.is_valid(bad)
    bad = copy.deepcopy(fixture)
    bad['input']['origin'] = 'CONSENTED_LOCAL'
    bad['evidenceKind'] = 'HUMAN_ANNOTATION'
    assert not validator.is_valid(bad)
    bad['consentRecordLocalId'] = 'local-consent-example'
    assert validator.is_valid(bad)
    bad = copy.deepcopy(fixture)
    bad['expected'][0]['failure'] = 'LOW_CONFIDENCE'
    assert not validator.is_valid(bad)
print(f'Front annotation schema and {len(fixtures)} synthetic fixture(s) passed; no capture validation claimed')
