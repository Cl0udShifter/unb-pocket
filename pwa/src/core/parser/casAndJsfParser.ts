export interface CasLoginForm {
  readonly action: string;
  readonly lt: string;
  readonly execution: string;
  readonly eventId: string;
}

const JSF_CLJS_OBJECT_REGEX = /jsfcljs\([^,]+,\s*\{([\s\S]*?)\}\s*,?/;
const KEY_VALUE_PAIR_REGEX = /'([^']*)'\s*:\s*'([^']*)'/g;

export function parseHtmlDocument(html: string): Document {
  return new DOMParser().parseFromString(html, 'text/html');
}

export function parseHtmlFragmentText(fragmentHtml: string): string {
  const doc = parseHtmlDocument(`<body>${fragmentHtml}</body>`);
  return (doc.body.textContent ?? '').replace(/\s+/g, ' ').trim();
}

export const CasAndJsfParser = {
  isCasLoginPage(html: string): boolean {
    const doc = parseHtmlDocument(html);
    return (
      doc.querySelector('form#login-form') !== null ||
      (doc.querySelector('input[name="lt"]') !== null &&
        doc.querySelector('input[name="execution"]') !== null)
    );
  },

  parseCasLoginForm(html: string): CasLoginForm | null {
    const doc = parseHtmlDocument(html);
    const form = doc.querySelector('form#login-form');
    if (!form) return null;

    const action = (form.getAttribute('action') ?? '').trim();
    const lt = form.querySelector('input[name="lt"]')?.getAttribute('value')?.trim();
    const execution = form.querySelector('input[name="execution"]')?.getAttribute('value')?.trim();
    const rawEventId = form.querySelector('input[name="_eventId"]')?.getAttribute('value')?.trim() ?? '';
    const eventId = rawEventId.length > 0 ? rawEventId : 'submit';

    if (!action || !lt || !execution) return null;
    return { action, lt, execution, eventId };
  },

  extractViewStateFromDoc(doc: Document): string | null {
    const val = doc
      .querySelector('input[name="javax.faces.ViewState"]')
      ?.getAttribute('value')
      ?.trim();
    return val && val.length > 0 ? val : null;
  },

  extractViewState(html: string): string | null {
    return this.extractViewStateFromDoc(parseHtmlDocument(html));
  },

  extractDiscenteIdFromDoc(doc: Document): string | null {
    const form = doc.getElementById('menu:form_menu_discente');
    const val = form?.querySelector('input[name="id"]')?.getAttribute('value')?.trim();
    return val && val.length > 0 ? val : null;
  },

  extractDiscenteId(html: string): string | null {
    return this.extractDiscenteIdFromDoc(parseHtmlDocument(html));
  },

  extractJsfCljsParams(onClick: string): Record<string, string> {
    const match = JSF_CLJS_OBJECT_REGEX.exec(onClick);
    if (!match) return {};
    const objectBody = match[1];
    const params: Record<string, string> = {};
    KEY_VALUE_PAIR_REGEX.lastIndex = 0;
    let pair: RegExpExecArray | null;
    while ((pair = KEY_VALUE_PAIR_REGEX.exec(objectBody)) !== null) {
      params[pair[1]] = pair[2];
    }
    return params;
  },

  extractExtratoRuFormParamsFromDoc(doc: Document): Record<string, string> | null {
    const form = doc.querySelector('form#formExibirExtrato');
    if (!form) return null;
    const link = form.querySelector('a[onclick]');
    if (!link) return null;
    const viewState = this.extractViewStateFromDoc(doc);
    if (!viewState) return null;

    const formId = (form.getAttribute('id') ?? '').trim() || 'formExibirExtrato';
    const params: Record<string, string> = { [formId]: formId };
    const onclickParams = this.extractJsfCljsParams(link.getAttribute('onclick') ?? '');
    Object.assign(params, onclickParams);
    if (Object.keys(params).length <= 1) return null;
    params['javax.faces.ViewState'] = viewState;
    return params;
  },

  extractExtratoRuFormParams(html: string): Record<string, string> | null {
    return this.extractExtratoRuFormParamsFromDoc(parseHtmlDocument(html));
  },

  buildMenuDiscenteParamsFromDoc(
    doc: Document,
    jscookAction: string,
  ): Record<string, string> | null {
    const idDiscente = this.extractDiscenteIdFromDoc(doc);
    const viewState = this.extractViewStateFromDoc(doc);
    if (!idDiscente || !viewState) return null;
    return {
      'menu:form_menu_discente': 'menu:form_menu_discente',
      id: idDiscente,
      jscook_action: jscookAction,
      'javax.faces.ViewState': viewState,
    };
  },

  buildMenuDiscenteParams(
    html: string,
    jscookAction: string,
  ): Record<string, string> | null {
    return this.buildMenuDiscenteParamsFromDoc(parseHtmlDocument(html), jscookAction);
  },

  normalizeLabel(raw: string): string {
    return raw
      .normalize('NFD')
      .replace(/[\u0300-\u036f]+/g, '')
      .replace(/\s+/g, ' ')
      .trim()
      .toLowerCase();
  },
};
