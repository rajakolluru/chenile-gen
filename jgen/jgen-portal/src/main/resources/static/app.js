const state = {
  sessionId: null,
  generators: [],
  selected: null,
  schema: null,
  currentFile: null,
  lastLogOperation: null,
  lastTestsOperation: null,
  lastBuildOperation: null
};

const el = id => document.getElementById(id);
const storage = {
  sessionId: 'jgen.portal.sessionId',
  selected: 'jgen.portal.selectedGenerator',
  activeTab: 'jgen.portal.activeTab',
  generationOperation: 'jgen.portal.generationOperation',
  testsOperation: 'jgen.portal.testsOperation',
  buildOperation: 'jgen.portal.buildOperation'
};

async function api(path, options = {}) {
  const response = await fetch(path, options);
  if (!response.ok) {
    const error = await response.json().catch(() => ({ error: response.statusText }));
    throw new Error(error.error || response.statusText);
  }
  return response.json();
}

async function init() {
  bindChrome();
  await restoreSession();
  state.generators = await api('/api/generators');
  renderGenerators();
  const selected = localStorage.getItem(storage.selected);
  if (selected && state.generators.some(generator => generator.name === selected)) {
    await selectGenerator(selected);
  }
  const activeTab = localStorage.getItem(storage.activeTab);
  if (activeTab) switchTab(activeTab);
  restoreOperations();
}

function bindChrome() {
  el('generatorSearch').addEventListener('input', renderGenerators);
  el('generateBtn').addEventListener('click', generate);
  el('refreshTreeBtn').addEventListener('click', refreshTree);
  el('downloadProjectBtn').href = `/api/workspaces/${state.sessionId}/download`;
  el('copyLogBtn').addEventListener('click', () => navigator.clipboard.writeText(el('logViewer').innerText));
  el('runTestsBtn').addEventListener('click', runTests);
  el('rerunTestsBtn').addEventListener('click', runTests);
  el('runBuildBtn').addEventListener('click', runBuild);
  el('rerunBuildBtn').addEventListener('click', runBuild);
  el('logSearch').addEventListener('input', () => filterTerminal(el('logViewer'), el('logSearch').value));
  el('fileSearch').addEventListener('input', () => filterCode(el('fileViewer'), el('fileSearch').value));
  document.querySelectorAll('.tab').forEach(tab => tab.addEventListener('click', () => switchTab(tab.dataset.tab)));
  document.querySelectorAll('.activity-item').forEach(button => button.addEventListener('click', () => switchActivity(button.dataset.activity)));
}

async function restoreSession() {
  const saved = localStorage.getItem(storage.sessionId);
  if (saved) {
    try {
      const existing = await api(`/api/sessions/${encodeURIComponent(saved)}`);
      if (existing.exists) {
        setSession(existing.sessionId);
        return;
      }
    } catch (ignored) {
    }
  }
  const session = await api('/api/sessions', { method: 'POST' });
  setSession(session.sessionId);
}

function setSession(sessionId) {
  state.sessionId = sessionId;
  localStorage.setItem(storage.sessionId, sessionId);
  el('sessionStatus').textContent = `Session ${sessionId.slice(0, 8)}`;
  el('downloadProjectBtn').href = `/api/workspaces/${sessionId}/download`;
}

function restoreOperations() {
  state.lastLogOperation = localStorage.getItem(storage.generationOperation);
  state.lastTestsOperation = localStorage.getItem(storage.testsOperation);
  state.lastBuildOperation = localStorage.getItem(storage.buildOperation);
  if (state.lastLogOperation) {
    el('downloadLogBtn').href = `/api/operations/${state.lastLogOperation}/logs/download`;
    el('downloadLogBtn').classList.remove('disabled');
    watchOperation(state.lastLogOperation, el('logViewer'), el('operationStatus'), refreshTree, { append: false });
  }
}

function renderGenerators() {
  const query = el('generatorSearch').value.toLowerCase();
  el('generatorList').innerHTML = '';
  state.generators
    .filter(g => `${g.name} ${g.description}`.toLowerCase().includes(query))
    .forEach(generator => {
      const button = document.createElement('button');
      button.className = `generator ${state.selected === generator.name ? 'active' : ''}`;
      button.innerHTML = [
        `<span class="generator-main">`,
        `<strong>${escapeHtml(generator.name)}</strong>`,
        `<span class="generator-description">${escapeHtml(generator.description || '')}</span>`,
        `</span>`,
        `<span class="generator-meta">${escapeHtml(generator.version)}</span>`
      ].join('');
      button.title = generator.description || generator.name;
      button.addEventListener('click', () => selectGenerator(generator.name));
      el('generatorList').appendChild(button);
    });
}

async function selectGenerator(name) {
  state.selected = name;
  localStorage.setItem(storage.selected, name);
  state.schema = await api(`/api/generators/${name}`);
  el('generatorTitle').textContent = state.schema.name;
  el('generatorDescription').textContent = state.schema.description || '';
  el('generateBtn').disabled = false;
  renderGenerators();
  renderForm();
}

function renderForm() {
  const form = el('generatorForm');
  form.innerHTML = '';
  for (const field of state.schema.fields) {
    const wrapper = document.createElement('div');
    wrapper.className = 'field';
    wrapper.dataset.field = field.name;
    const label = document.createElement('label');
    label.textContent = `${field.description || field.name}${field.required ? ' *' : ''}`;
    wrapper.appendChild(label);
    wrapper.appendChild(inputFor(field));
    const help = document.createElement('div');
    help.className = 'help';
    help.textContent = field.helpText || field.name;
    wrapper.appendChild(help);
    const error = document.createElement('div');
    error.className = 'error';
    wrapper.appendChild(error);
    form.appendChild(wrapper);
  }
}

function inputFor(field) {
  if (field.type === 'BOOLEAN') {
    const select = document.createElement('select');
    select.name = field.name;
    select.innerHTML = '<option value="y">Yes</option><option value="n">No</option>';
    select.value = field.defaultValue === true || field.defaultValue === 'y' || field.defaultValue === 'true' ? 'y' : 'n';
    return select;
  }
  if (field.type === 'DROPDOWN') {
    const select = document.createElement('select');
    select.name = field.name;
    for (const value of field.validValues || []) select.add(new Option(value, value));
    if (field.defaultValue) select.value = field.defaultValue;
    return select;
  }
  if (field.type === 'MULTI_SELECT') {
    const select = document.createElement('select');
    select.name = field.name;
    select.multiple = true;
    for (const value of field.validValues || []) select.add(new Option(value, value));
    return select;
  }
  if (field.type === 'FILE') {
    const input = document.createElement('input');
    input.type = 'file';
    input.name = field.name;
    if (field.accept) input.accept = field.accept;
    input.multiple = field.multiple === true;
    return input;
  }
  if (field.type === 'RECORD_ARRAY') {
    const textarea = document.createElement('textarea');
    textarea.name = field.name;
    textarea.placeholder = 'JSON array';
    textarea.value = '[]';
    return textarea;
  }
  const input = document.createElement('input');
  input.name = field.name;
  input.type = field.type === 'NUMBER' ? 'number' : field.type === 'DATE' ? 'date' : 'text';
  if (field.defaultValue != null) input.value = field.defaultValue;
  return input;
}

async function collectAnswers() {
  const answers = {};
  for (const field of state.schema.fields) {
    const input = el('generatorForm').elements[field.name];
    if (!input) continue;
    if (field.type === 'FILE') {
      if (input.files.length === 0) continue;
      const paths = [];
      for (const file of input.files) {
        const form = new FormData();
        form.append('sessionId', state.sessionId);
        form.append('fieldName', field.name);
        form.append('generator', state.selected);
        form.append('file', file);
        const upload = await api('/api/uploads', { method: 'POST', body: form });
        paths.push(upload.path);
      }
      answers[field.name] = field.multiple ? paths : paths[0];
    } else if (field.type === 'MULTI_SELECT') {
      answers[field.name] = Array.from(input.selectedOptions).map(option => option.value);
    } else if (field.type === 'RECORD_ARRAY') {
      answers[field.name] = JSON.parse(input.value || '[]');
    } else {
      answers[field.name] = input.value;
    }
  }
  return answers;
}

async function generate(event) {
  event.preventDefault();
  clearErrors();
  try {
    const answers = await collectAnswers();
    const operation = await api('/api/generations', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sessionId: state.sessionId, generator: state.selected, answers })
    });
    state.lastLogOperation = operation.id;
    localStorage.setItem(storage.generationOperation, operation.id);
    el('downloadLogBtn').href = `/api/operations/${operation.id}/logs/download`;
    el('downloadLogBtn').classList.remove('disabled');
    el('downloadProjectBtn').classList.remove('disabled');
    watchOperation(operation.id, el('logViewer'), el('operationStatus'), refreshTree);
  } catch (error) {
    el('operationStatus').textContent = error.message;
    setStatus(el('operationStatus'), 'FAILED');
  }
}

async function runTests() {
  const pattern = el('testFilter').value.trim();
  const body = pattern ? { tests: [pattern] } : {};
  const operation = await api(`/api/workspaces/${state.sessionId}/tests`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  state.lastTestsOperation = operation.id;
  localStorage.setItem(storage.testsOperation, operation.id);
  watchOperation(operation.id, el('testConsole'), el('testStatus'));
}

async function runBuild() {
  const operation = await api(`/api/workspaces/${state.sessionId}/builds`, { method: 'POST' });
  state.lastBuildOperation = operation.id;
  localStorage.setItem(storage.buildOperation, operation.id);
  watchOperation(operation.id, el('buildConsole'), el('buildStatus'));
}

function watchOperation(id, target, statusTarget, onDone, options = {}) {
  if (options.append !== true) target.innerHTML = '';
  if (statusTarget) setStatus(statusTarget, 'RUNNING');
  const source = new EventSource(`/api/operations/${id}/logs/stream`);
  source.addEventListener('log', event => {
    appendLog(target, event.data);
  });
  source.addEventListener('status', event => {
    if (statusTarget) setStatus(statusTarget, event.data);
    source.close();
    if (event.data === 'COMPLETED' && onDone) onDone();
  });
  source.onerror = () => {
    source.close();
  };
}

function appendLog(target, line) {
  const parsed = parseLogLine(line);
  const row = document.createElement('div');
  row.className = `terminal-line ${logClass(parsed)}`;
  row.dataset.raw = line.toLowerCase();
  row.innerHTML = [
    `<span class="terminal-time">${escapeHtml(parsed.time)}</span>`,
    `<span class="terminal-level">${escapeHtml(parsed.level)}</span>`,
    `<span class="terminal-message">${escapeHtml(parsed.message)}</span>`
  ].join('');
  target.appendChild(row);
  target.scrollTop = target.scrollHeight;
}

function parseLogLine(line) {
  const match = String(line).match(/^(\S+)\s+\[(\w+)]\s+(.*)$/);
  if (!match) {
    return { time: '', level: 'OUT', message: line };
  }
  return {
    time: match[1].replace('T', ' ').replace('Z', ''),
    level: match[2],
    message: match[3]
  };
}

function logClass(parsed) {
  const text = `${parsed.level} ${parsed.message}`.toLowerCase();
  if (text.includes('error') || text.includes('failure') || text.includes('failed')) return 'error';
  if (text.includes('warn')) return 'warn';
  if (text.includes('success') || text.includes('completed')) return 'success';
  if (text.includes('[info]') || text.includes('--- ') || text.includes('reactor summary')) return 'maven';
  if (text.includes('running mvn') || text.includes('generating blueprint')) return 'command';
  return '';
}

function setStatus(node, status) {
  const normalized = String(status || 'IDLE').toLowerCase();
  node.textContent = normalized.charAt(0).toUpperCase() + normalized.slice(1);
  node.classList.remove('idle', 'running', 'completed', 'failed');
  if (normalized.includes('run')) node.classList.add('running');
  else if (normalized.includes('complete')) node.classList.add('completed');
  else if (normalized.includes('fail') || normalized.includes('error')) node.classList.add('failed');
  else node.classList.add('idle');
}

async function refreshTree() {
  try {
    const tree = await api(`/api/workspaces/${state.sessionId}/tree`);
    el('fileTree').innerHTML = '';
    if (!tree.children || tree.children.length === 0) {
      el('fileTree').innerHTML = '<div class="empty-state">No generated files yet.</div>';
      return;
    }
    renderNode(tree, el('fileTree'), 0);
    el('downloadProjectBtn').classList.remove('disabled');
  } catch (error) {
    el('fileTree').innerHTML = `<div class="empty-state">${escapeHtml(error.message)}</div>`;
  }
}

function renderNode(node, parent, depth) {
  const button = document.createElement('button');
  button.className = node.directory ? 'node-dir' : 'node-file';
  button.style.paddingLeft = `${8 + depth * 16}px`;
  button.innerHTML = `<span class="node-icon">${node.directory ? '[+]' : fileIcon(node.name)}</span><span class="node-name">${escapeHtml(node.name)}</span>`;
  if (!node.directory) button.addEventListener('click', () => openFile(node.path, node.name));
  parent.appendChild(button);
  if (node.directory) {
    for (const child of node.children || []) renderNode(child, parent, depth + 1);
  }
}

async function openFile(path, name) {
  const file = await api(`/api/workspaces/${state.sessionId}/files?path=${encodeURIComponent(path)}`);
  state.currentFile = { path, name, content: file.content };
  el('fileName').textContent = name;
  renderCode(file.content, el('fileSearch').value, name);
}

function renderCode(content, query = '', fileName = '') {
  const q = query.trim();
  const highlighter = codeHighlighter(fileName);
  const html = content.split('\n').map(line => {
    const highlighted = highlighter(line);
    const matched = q && line.toLowerCase().includes(q.toLowerCase());
    return `<span class="line${matched ? ' highlight' : ''}">${highlighted}</span>`;
  }).join('');
  el('fileViewer').classList.remove('editor-empty');
  el('fileViewer').innerHTML = html || '<span class="line"></span>';
}

function switchTab(tab) {
  document.querySelectorAll('.tab').forEach(button => button.classList.toggle('active', button.dataset.tab === tab));
  document.querySelectorAll('.tab-panel').forEach(panel => panel.classList.remove('active'));
  el(`${tab}Panel`).classList.add('active');
  localStorage.setItem(storage.activeTab, tab);
  syncActivityForTab(tab);
  if (tab === 'files') refreshTree();
}

function switchActivity(activity) {
  document.querySelectorAll('.activity-item').forEach(button => button.classList.toggle('active', button.dataset.activity === activity));
  if (activity === 'generators') {
    return;
  }
  if (activity === 'workspace') {
    switchTab('files');
    return;
  }
  if (activity === 'terminal') {
    switchTab('logs');
  }
}

function syncActivityForTab(tab) {
  const activity = tab === 'files' ? 'workspace' : (tab === 'logs' || tab === 'tests' || tab === 'build') ? 'terminal' : 'generators';
  document.querySelectorAll('.activity-item').forEach(button => button.classList.toggle('active', button.dataset.activity === activity));
}

function filterTerminal(container, query) {
  const q = query.trim().toLowerCase();
  container.querySelectorAll('.terminal-line').forEach(row => row.classList.remove('highlight'));
  if (!q) return;
  const match = Array.from(container.querySelectorAll('.terminal-line')).find(row => row.dataset.raw.includes(q));
  if (match) {
    match.classList.add('highlight');
    match.scrollIntoView({ block: 'center' });
  }
}

function filterCode(container, query) {
  const q = query.trim();
  if (state.currentFile) {
    renderCode(state.currentFile.content, q, state.currentFile.name);
  }
  if (!q) return;
  const match = Array.from(container.querySelectorAll('.line')).find(line => line.innerText.toLowerCase().includes(q.toLowerCase()));
  if (match) {
    match.classList.add('highlight');
    match.scrollIntoView({ block: 'center' });
  }
}

function fileIcon(name) {
  const lower = String(name).toLowerCase();
  if (lower.endsWith('.java')) return 'J';
  if (lower.endsWith('.xml')) return 'X';
  if (lower.endsWith('.json')) return '{}';
  if (lower.endsWith('.yml') || lower.endsWith('.yaml')) return 'Y';
  if (lower.endsWith('.md')) return 'M';
  if (lower.endsWith('.properties')) return 'P';
  return '-';
}

function highlight(safeHtml, query) {
  const index = safeHtml.toLowerCase().indexOf(escapeHtml(query).toLowerCase());
  if (index < 0) return safeHtml;
  return `${safeHtml.slice(0, index)}<mark>${safeHtml.slice(index, index + query.length)}</mark>${safeHtml.slice(index + query.length)}`;
}

function highlightText(html, query) {
  return html;
}

function codeHighlighter(fileName) {
  const lower = String(fileName || '').toLowerCase();
  if (lower.endsWith('.java')) return highlightJava;
  if (lower.endsWith('.xml')) return highlightXml;
  if (lower.endsWith('.json')) return highlightJson;
  if (lower.endsWith('.yml') || lower.endsWith('.yaml')) return highlightYaml;
  if (lower.endsWith('.properties')) return highlightProperties;
  if (lower.endsWith('.md')) return highlightMarkdown;
  return line => escapeHtml(line);
}

function highlightJava(line) {
  return highlightByMatchers(line, [
    { regex: /\/\/.*/, className: 'token-comment' },
    { regex: /"(?:\\.|[^"\\])*"/, className: 'token-string' },
    { regex: /@[A-Za-z_][A-Za-z0-9_]*/, className: 'token-annotation' },
    { regex: /\b(package|import|public|private|protected|class|interface|enum|extends|implements|static|final|void|new|return|if|else|for|while|try|catch|throw|throws|switch|case|default|this|super)\b/, className: 'token-keyword' },
    { regex: /\b(true|false|null)\b/, className: 'token-bool' },
    { regex: /\b[A-Z][A-Za-z0-9_]*\b/, className: 'token-type' }
  ]);
}

function highlightXml(line) {
  return highlightByMatchers(line, [
    { regex: /<!--.*?-->/, className: 'token-comment' },
    { regex: /<\/?[A-Za-z0-9:._-]+/, className: 'token-tag' },
    { regex: /[A-Za-z_:.-]+(?==)/, className: 'token-attr-name' },
    { regex: /"(?:\\.|[^"\\])*"/, className: 'token-attr-value' }
  ]);
}

function highlightJson(line) {
  return highlightByMatchers(line, [
    { regex: /"(?:\\.|[^"\\])*"(?=\s*:)/, className: 'token-property' },
    { regex: /"(?:\\.|[^"\\])*"/, className: 'token-string' },
    { regex: /\b-?\d+(?:\.\d+)?\b/, className: 'token-number' },
    { regex: /\b(true|false|null)\b/, className: 'token-bool' }
  ]);
}

function highlightYaml(line) {
  return highlightByMatchers(line, [
    { regex: /^[\s-]*[A-Za-z0-9_.-]+(?=\s*:)/, className: 'token-property' },
    { regex: /"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'/, className: 'token-string' },
    { regex: /\b(true|false|null)\b/, className: 'token-bool' }
  ]);
}

function highlightProperties(line) {
  return highlightByMatchers(line, [
    { regex: /^[#!].*/, className: 'token-comment' },
    { regex: /^[^=:\s]+(?=\s*[=:])/, className: 'token-property' }
  ]);
}

function highlightMarkdown(line) {
  return highlightByMatchers(line, [
    { regex: /^#{1,6}\s.*/, className: 'token-heading' },
    { regex: /`[^`]+`/, className: 'token-string' }
  ]);
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function highlightByMatchers(line, matchers) {
  const ranges = [];
  for (const matcher of matchers) {
    const regex = new RegExp(matcher.regex.source, matcher.regex.flags.includes('g') ? matcher.regex.flags : `${matcher.regex.flags}g`);
    let match;
    while ((match = regex.exec(line)) !== null) {
      if (match[0].length === 0) break;
      ranges.push({
        start: match.index,
        end: match.index + match[0].length,
        className: matcher.className
      });
    }
  }
  ranges.sort((a, b) => a.start - b.start || (b.end - b.start) - (a.end - a.start));

  const filtered = [];
  for (const range of ranges) {
    if (filtered.some(existing => range.start < existing.end && range.end > existing.start)) continue;
    filtered.push(range);
  }
  filtered.sort((a, b) => a.start - b.start);

  let cursor = 0;
  let output = '';
  for (const range of filtered) {
    if (cursor < range.start) output += escapeHtml(line.slice(cursor, range.start));
    output += `<span class="${range.className}">${escapeHtml(line.slice(range.start, range.end))}</span>`;
    cursor = range.end;
  }
  if (cursor < line.length) output += escapeHtml(line.slice(cursor));
  return output || escapeHtml(line);
}

function clearErrors() {
  document.querySelectorAll('.error').forEach(node => node.textContent = '');
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

init().catch(error => {
  el('operationStatus').textContent = error.message;
});
