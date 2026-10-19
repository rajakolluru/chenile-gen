const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

test('conditional choices hide inactive inputs and skip their invalid JSON when submitting', async () => {
  const fields = [
    { name: 'registryMode', type: 'DROPDOWN' },
    { name: 'registryPlacement', type: 'DROPDOWN', visibleWhen: { registryMode: 'create' } },
    { name: 'registryUrl', type: 'STRING', visibleWhen: { registryMode: 'create', registryPlacement: 'separate' } },
    { name: 'existingUrl', type: 'STRING', visibleWhen: { registryMode: 'existing' } },
    { name: 'optional', type: 'BOOLEAN' },
    { name: 'dependencies', type: 'POM_Dependencies', visibleWhen: { optional: true } }
  ];
  const wrappers = Object.fromEntries(fields.map(field => [field.name, {}]));
  const elements = Object.fromEntries(fields.map(field => [field.name, { value: '' }]));
  Object.assign(elements.registryMode, { value: 'create' });
  Object.assign(elements.registryPlacement, { value: 'server' });
  Object.assign(elements.optional, { value: 'n' });
  Object.assign(elements.dependencies, { value: 'malformed JSON should never be parsed while hidden' });
  const form = { elements, querySelector: selector => wrappers[selector.match(/data-field="(.*?)"/)[1]] };
  const context = vm.createContext({ document: { getElementById: () => form } });
  const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/app.js'), 'utf8').replace(/init\(\)\.catch\(error => \{[\s\S]*$/, '');
  vm.runInContext(source, context);
  vm.runInContext(`state.schema = ${JSON.stringify({ fields })}`, context);
  vm.runInContext('updateFieldVisibility()', context);
  assert.equal(wrappers.registryPlacement.hidden, false);
  assert.equal(wrappers.registryUrl.hidden, true);
  assert.equal(elements.dependencies.disabled, true);
  let answers = await vm.runInContext('collectAnswers()', context);
  assert.equal('dependencies' in answers, false);
  elements.registryPlacement.value = 'separate';
  vm.runInContext('updateFieldVisibility()', context);
  assert.equal(wrappers.registryUrl.hidden, false);
  elements.registryMode.value = 'existing';
  vm.runInContext('updateFieldVisibility()', context);
  answers = await vm.runInContext('collectAnswers()', context);
  assert.equal('registryPlacement' in answers, false);
  assert.equal('registryUrl' in answers, false);
  assert.equal('existingUrl' in answers, true);
  elements.optional.value = 'y'; elements.dependencies.value = '[]';
  vm.runInContext('updateFieldVisibility()', context);
  assert.equal(elements.dependencies.disabled, false);
  answers = await vm.runInContext('collectAnswers()', context);
  assert.equal(Array.isArray(answers.dependencies), true);
});
