/* Offline assertions over real HTTP responses recorded by ArchitectureBrowserServerTest.
 * Does not drive the browser, issue HTTP requests, or open a database. No fixture inputs.
 * Run after the documented browser scenario: node src/test/js/architecture-e2e-evidence.cjs
 */
const fs = require('node:fs');
const assert = require('node:assert/strict');
const source = process.argv[2] || 'tmp/PM-003C.4-http.jsonl';
const entries = fs.readFileSync(source, 'utf8').trim().split(/\r?\n/).map((line, index) => {
  const e = JSON.parse(line);
  return {sequence:index + 1, ...e, response:e.response ? JSON.parse(e.response) : null};
});
const root = '/api/projects/zero/architecture';
const select = (method, path, status) => entries.filter(e => e.method === method && e.path === path && e.status === status);
const post = select('POST', root, 201).map(e => e.response);
assert.equal(post.length, 2);
assert.equal(post[0].passport.version, 0);
assert.equal(post[0].passport.architect, null);
assert.equal(post[0].passport.documentVersion, '1.0');
assert.notEqual(post[0].passport.instanceId, post[1].passport.instanceId);
assert.equal(post[1].passport.version, 0);
assert.equal(post[1].project.version, 3);
const passportPatches = select('PATCH', root, 200).map(e => e.response);
assert.equal(passportPatches.length, 4);
assert.equal(passportPatches[0].passport.architect, 'Fictional Architect');
assert.equal(passportPatches[1].passport.architect, null);
assert.deepEqual(passportPatches[1].dataClasses, []);
assert.deepEqual(passportPatches[1].environments, []);
const components = select('POST', root + '/components', 201);
assert.equal(components.length, 2);
const component = entries.find(e => e.method === 'PATCH' && e.path.startsWith(root + '/components/') && e.status === 200);
const changed = component.response.components.find(c => component.path.endsWith('/' + c.id));
assert.equal(changed.name, 'Atlas queue');
assert.equal(changed.externalComponent, false);
assert.equal(changed.position, 0);
const integration = entries.find(e => e.method === 'PATCH' && e.path.startsWith(root + '/integrations/') && e.status === 200);
assert.deepEqual(integration.response.integrations[0].dataClasses, ['INTERNAL']);
const integrationDelete = entries.find(e => e.method === 'DELETE' && e.path.startsWith(root + '/integrations/') && e.status === 200);
assert.equal(integrationDelete.response.integrations.length, 1);
assert.equal(integrationDelete.response.integrations[0].name, 'Atlas retained exchange');
assert.deepEqual(integrationDelete.response.integrations[0].dataClasses, ['PERSONAL']);
const initialized = select('POST', root + '/checks/initialize', 201)[0].response;
assert.equal(initialized.checks.length, 10);
assert.equal(new Set(initialized.checks.map(c => c.checkType)).size, 10);
for (const c of initialized.checks) {
  assert.equal(c.required, true); assert.equal(c.status, 'NOT_STARTED');
  for (const f of ['owner', 'plannedDate', 'actualDate', 'comment']) assert.equal(c[f], null);
}
assert.equal(select('POST', root + '/checks/initialize', 409)[0].response.code, 'CHECKS_ALREADY_EXIST');
const checkPatches = entries.filter(e => e.method === 'PATCH' && e.path.startsWith(root + '/checks/') && e.status === 200);
assert(checkPatches.some(e => e.response.checks.some(c => c.status === 'PASSED' && c.actualDate === '2026-10-24')));
assert(checkPatches.some(e => e.response.checks.some(c => !c.required && c.status === 'NOT_REQUIRED' && c.comment)));
const risk = select('POST', root + '/risks', 201)[0].response;
assert.equal(risk.risks[0].level, 'CRITICAL');
assert.equal(risk.assessment.status, 'ATTENTION_REQUIRED');
assert.equal(risk.assessment.officialGoLiveApproval, false);
assert.equal(risk.assessment.acceptedHighRisks[0].acceptanceVerification, 'UNVERIFIED');
assert(risk.assessment.reasons.some(r => r.code === 'ACCEPTED_HIGH_RISK'));
const riskPatches = entries.filter(e => e.method === 'PATCH' && e.path.startsWith(root + '/risks/') && e.status === 200);
assert(riskPatches.some(e => e.response.risks.some(r => r.status === 'CLOSED' && r.closedDate === '2026-10-24' && r.acceptanceJustification)));
assert(entries.some(e => e.status === 409 && e.response?.code === 'RISK_VERSION_CONFLICT' && e.response.currentVersion === 2));
assert.equal(select('PATCH', root, 409)[0].response.code, 'PASSPORT_INSTANCE_CONFLICT');
const impact = select('GET', root + '/deletion-impact', 200)[0].response;
assert.equal(impact.deletionAllowed, false);
assert.deepEqual(['componentCount','integrationCount','checkCount','securityRiskCount','dataClassCount','environmentCount'].map(k => impact[k]), [2,1,10,1,1,1]);
assert.equal(impact.blockers.length, 6);
for (const kind of ['components','integrations','checks','risks']) {
  const deletes = entries.filter(e => e.method === 'DELETE' && e.path.startsWith(root + '/' + kind + '/') && e.status === 200);
  assert.equal(deletes.length, {components:2, integrations:2, checks:10, risks:1}[kind]);
  for (const e of deletes) {
    const id = e.path.split('/').at(-1);
    assert(!e.response[kind].some(row => row.id === id));
    const query = new URLSearchParams(e.query);
    assert.equal(query.size, 4); assert.equal(query.get('confirmed'), 'true');
    for (const k of ['expectedVersion','expectedPassportVersion','expectedPassportInstanceId']) assert(query.has(k));
  }
}
assert(entries.some(e => e.method === 'GET' && e.path === root && e.response.checks.length === 9));
const deleted = select('DELETE', root, 200)[0];
assert.equal(new URLSearchParams(deleted.query).size, 3);
assert.equal(deleted.response.passport, null);
assert(deleted.response.assessment.reasons.some(r => r.code === 'PASSPORT_MISSING'));
const final = select('GET', root, 200).at(-1).response;
assert.equal(final.passport.instanceId, post[1].passport.instanceId);
assert.equal(final.passport.architect, null); assert.equal(final.passport.version, 0);
for (const k of ['components','integrations','checks','risks','dataClasses','environments']) assert.deepEqual(final[k], []);
for (const path of ['/api/projects', '/api/release-trains/snapshot']) {
  const reads = select('GET', path, 200).map(e => structuredClone(e.response));
  assert(reads.length > 1);
  if (path === '/api/projects') for (const rows of reads) for (const row of rows) if (row.id === 'zero') delete row.version;
  assert.deepEqual(reads[0], reads.at(-1));
}
assert(!entries.some(e => e.method !== 'GET' && !e.path.startsWith(root)));
const evidence = entries.filter(e => e.path.startsWith(root));
fs.mkdirSync('docs/review/PM-003C.4', {recursive:true});
fs.writeFileSync('docs/review/PM-003C.4/http-evidence.json', JSON.stringify({
  source:'Real Spring HTTP responses; test-only response observer; no mocked API',
  baseSha:'9858fffe05be880aecd138911e8cbf5a7166144d',
  checks:'All offline assertions passed', entries:evidence
}, null, 2) + '\n');
console.log(`PASS: ${entries.length} real HTTP responses; ${evidence.length} architecture responses exported`);
