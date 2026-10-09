import importlib.util
import json
import pathlib
import unittest

HERE = pathlib.Path(__file__).resolve().parents[1]
ROOT = HERE.parents[1]
SPEC = importlib.util.spec_from_file_location('guidance_probe', HERE / 'probe.py')
probe = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(probe)


class ResearchGateTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.controls, cls.record = probe.verify_record()
        cls.rows = cls.record['results']

    def row(self, case, strategy, batch='focused'):
        return next(row for row in self.rows if row['probe']['case_id'] == case
                    and row['probe']['strategy'] == strategy and row['probe']['batch'] == batch)

    def test_all_native_observations_have_unique_fixture_provenance(self):
        self.assertEqual(66, len(self.rows))
        self.assertEqual(66, len({row['probe']['probe_id'] for row in self.rows}))
        self.assertEqual({'en-es', 'es-en'}, {row['probe']['direction'] for row in self.rows})

    def test_no_guidance_inputs_are_exactly_the_current_utterance(self):
        basic = [row for row in self.rows if row['probe']['strategy'] == 'baseline']
        self.assertEqual(23, len(basic))
        for row in basic:
            self.assertEqual(row['probe']['current'], row['probe']['effective_source'])

    def test_authored_pass4_cases_remain_authored_controls(self):
        corpus = json.loads((ROOT / 'tools/translation-quality/corpus.json').read_text(encoding='utf8'))
        cases = {case['id']: case for case in corpus['cases']}
        authored = [row for row in self.rows if row['probe'].get('authored_source')]
        self.assertEqual(12, len(authored))
        for row in authored:
            self.assertEqual(cases[row['probe']['case_id']]['source'], row['probe']['current'])
            self.assertEqual(cases[row['probe']['case_id']]['direction'], row['probe']['direction'])

    def test_duplicate_boundary_is_not_recoverable(self):
        row = self.row('clear', 'context-suffix', 'suffix')
        self.assertEqual('MARKER_COUNT_MISMATCH', row['recovery']['status'])
        self.assertNotIn('text', row['recovery'])

    def test_invented_marker_is_not_a_second_translation(self):
        row = self.row('banco', 'sentinel')
        self.assertIn('CTBOUNDARY76322', row['result']['text'])
        self.assertEqual('MARKER_COUNT_MISMATCH', row['recovery']['status'])

    def test_missing_current_segment_is_not_success(self):
        row = self.row('vela', 'context-suffix', 'suffix')
        self.assertEqual('SEGMENT_MISSING', row['recovery']['status'])
        self.assertNotIn('text', row['recovery'])

    def test_marker_count_does_not_certify_the_target_meaning(self):
        row = self.row('cables', 'protected-term')
        self.assertEqual('RECOVERED_NOT_MEANING_VERIFIED', row['recovery']['status'])
        self.assertTrue(row['recovery']['text'].startswith('It brings '))
        self.assertFalse(self.record['production_ready'])

    def test_marker_recovery_can_force_the_wrong_sense(self):
        row = self.row('clear-price', 'protected-term')
        self.assertEqual('Ese precio es feria.', row['recovery']['text'])
        self.assertFalse(self.record['production_ready'])

    def test_token_limit_is_partial_not_completed_translation(self):
        partial = [row for row in self.rows if not row['result']['completed']]
        self.assertEqual(5, len(partial))
        for row in partial:
            self.assertEqual(128, row['result']['tokens'])
            self.assertEqual('INCOMPLETE_OR_NATIVE_FAILURE', row['recovery']['status'])

    def test_known_positive_controls_do_not_hide_failures(self):
        self.assertEqual('Lo dej\u00e9 en la orilla.', self.row('bank', 'context-suffix', 'suffix')['recovery']['text'])
        self.assertEqual('Check the sail.', self.row('sail', 'context-suffix', 'suffix')['recovery']['text'])
        self.assertEqual('I am finishing my degree this year.', self.row('degree', 'context-suffix', 'suffix')['recovery']['text'])
        self.assertEqual('STOP_FOR_GUIDANCE_REVIEW', self.record['decision'])

    def test_record_is_offline_fixture_evidence_not_s25_evidence(self):
        self.assertIn('NOT_PHYSICAL', self.record['scope'])
        self.assertEqual([], self.record['python_network_events'])
        self.assertEqual({'BLOCKED_WINERROR_10013'}, set(self.record['network_probes'].values()))
        self.assertFalse(self.record['new_model_assets_added'])
        self.assertTrue(self.record['model_pack_unchanged'])

    def test_partial_marker_is_not_recovered_into_output(self):
        row = self.row('cables', 'protected-term')
        changed = dict(row['result'], text=row['result']['text'].replace('CTTERM76321', 'CTTERM7632'))
        self.assertEqual('MARKER_COUNT_MISMATCH', probe.recover(row['probe'], changed)['status'])


if __name__ == '__main__':
    unittest.main()
