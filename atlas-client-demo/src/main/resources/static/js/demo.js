// Demo UI: tabs, confirmations, the help panel toggle and Monaco code views/editors.
//
// <pre class="code" data-lang="json">...</pre>  read-only Monaco view, created when it becomes visible
// <textarea data-editor="yaml">...</textarea>   Monaco editor that writes back to the textarea (form field)
// Without Monaco (still loading or failed), the plain <pre>/<textarea> stays usable.
(() => {
    const LINE_HEIGHT = 19;
    const MAX_VIEW_HEIGHT = 480;
    const LANGUAGES = {json: 'json', yaml: 'yaml', yml: 'yaml', java: 'java', csv: 'plaintext', text: 'plaintext', plaintext: 'plaintext'};

    let monacoReady = null;

    function loadMonaco() {
        if (monacoReady) return monacoReady;
        const base = document.querySelector('meta[name="monaco-base"]').content;
        monacoReady = new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = base + '/loader.js';
            script.onload = () => {
                window.require.config({paths: {vs: base}});
                window.require(['vs/editor/editor.main'], () => resolve(window.monaco), reject);
            };
            script.onerror = reject;
            document.head.appendChild(script);
        });
        return monacoReady;
    }

    function language(name) {
        return LANGUAGES[(name || '').toLowerCase()] || 'plaintext';
    }

    /**
     * Pretty prints compact JSON (request and response bodies). Atlas audit details put a label in front
     * ("Created: {...}"), so the label is kept on its own line. Other text is kept as is.
     */
    function prettyJson(text) {
        const trimmed = text.trim();
        const start = trimmed.search(/[{[]/);
        if (start < 0) return text;
        try {
            const json = JSON.stringify(JSON.parse(trimmed.substring(start)), null, 2);
            const label = trimmed.substring(0, start).trim();
            return label ? label + '\n' + json : json;
        } catch (e) {
            return text;
        }
    }

    const baseOptions = {
        minimap: {enabled: false},
        scrollBeyondLastLine: false,
        automaticLayout: true,
        fontSize: 12.5,
        lineHeight: LINE_HEIGHT,
        tabSize: 2,
        wordWrap: 'on',
        renderLineHighlight: 'none',
        scrollbar: {alwaysConsumeMouseWheel: false},
        fixedOverflowWidgets: true,
    };

    function createView(monaco, pre) {
        const lang = language(pre.dataset.lang);
        const raw = pre.textContent.replace(/\s+$/, '');
        const text = lang === 'json' ? prettyJson(raw) : raw;
        const lines = text.split('\n').length;
        const max = Number(pre.dataset.maxHeight) || MAX_VIEW_HEIGHT;
        const inHelp = !!pre.closest('.help');
        const host = document.createElement('div');
        host.className = 'monaco-host';
        host.style.height = Math.min(lines * LINE_HEIGHT + 12, max) + 'px';
        pre.replaceWith(host);
        const editor = monaco.editor.create(host, {
            ...baseOptions,
            value: text,
            language: lang,
            readOnly: true,
            domReadOnly: true,
            lineNumbers: lines > 3 && !inHelp ? 'on' : 'off',
            folding: lines > 3 && !inHelp,
            lineDecorationsWidth: inHelp ? 6 : 10,
            // The help column is narrow: scroll sideways instead of breaking aligned comments.
            wordWrap: inHelp ? 'off' : 'on',
            padding: {top: 6, bottom: 6},
        });
        // Wrapped lines make the content taller than the line count: follow the real height.
        const fit = () => host.style.height = Math.min(editor.getContentHeight(), max) + 'px';
        editor.onDidContentSizeChange(fit);
        fit();
    }

    function createEditor(monaco, textarea) {
        const host = document.createElement('div');
        host.className = 'monaco-host editor';
        host.style.height = (Number(textarea.getAttribute('rows')) || 10) * LINE_HEIGHT + 12 + 'px';
        textarea.style.display = 'none';
        textarea.after(host);
        const editor = monaco.editor.create(host, {
            ...baseOptions,
            value: textarea.value,
            language: language(textarea.dataset.editor),
        });
        editor.onDidChangeModelContent(() => textarea.value = editor.getValue());
        // <textarea data-editor-language-from="format"> follows the select named "format" of its form.
        const selectName = textarea.dataset.editorLanguageFrom;
        const select = selectName && textarea.form && textarea.form.elements[selectName];
        if (select) {
            const apply = () => monaco.editor.setModelLanguage(editor.getModel(), language(select.value));
            select.addEventListener('change', apply);
            apply();
        }
    }

    // Created only when visible: pages have many bodies inside closed <details> and hidden tabs.
    const observer = 'IntersectionObserver' in window ? new IntersectionObserver(entries => {
        entries.filter(e => e.isIntersecting).forEach(e => {
            observer.unobserve(e.target);
            build(e.target);
        });
    }, {rootMargin: '200px'}) : null;

    function build(element) {
        loadMonaco().then(monaco => {
            if (!element.isConnected) return;
            if (element.tagName === 'TEXTAREA') createEditor(monaco, element);
            else createView(monaco, element);
        }).catch(e => console.warn('Monaco editor not available; showing plain text', e));
    }

    /** Turns code blocks and editors under root into Monaco instances. Also used for rows added later. */
    function enhance(root) {
        root.querySelectorAll('pre.code, textarea[data-editor]').forEach(element => {
            if (observer) observer.observe(element);
            else build(element);
        });
    }

    window.demoCode = {enhance};

    function setUpHelp() {
        const KEY = 'atlas-client-demo.help-hidden';
        const apply = hidden => document.body.classList.toggle('help-hidden', hidden);
        let hidden = false;
        try {
            hidden = localStorage.getItem(KEY) === 'true';
        } catch (e) {
            // storage unavailable: help stays visible
        }
        apply(hidden);
        document.querySelectorAll('[data-help-toggle]').forEach(button => button.addEventListener('click', () => {
            hidden = !document.body.classList.contains('help-hidden');
            apply(hidden);
            try {
                localStorage.setItem(KEY, String(hidden));
            } catch (e) {
                // ignore
            }
        }));
    }

    document.addEventListener('DOMContentLoaded', () => {
        // Tabs: <div data-tabs> with buttons data-tab="id" and panels id="id".
        document.querySelectorAll('[data-tabs]').forEach(tabs => {
            const buttons = tabs.querySelectorAll('[data-tab]');
            const show = id => buttons.forEach(b => {
                b.classList.toggle('active', b.dataset.tab === id);
                document.getElementById(b.dataset.tab).classList.toggle('active', b.dataset.tab === id);
            });
            buttons.forEach(b => b.addEventListener('click', () => show(b.dataset.tab)));
            if (buttons.length) show(buttons[0].dataset.tab);
        });
        // Forms with data-confirm ask first.
        document.querySelectorAll('form[data-confirm]').forEach(f => f.addEventListener('submit', e => {
            if (!confirm(f.dataset.confirm)) e.preventDefault();
        }));
        setUpHelp();
        enhance(document);
    });
})();
