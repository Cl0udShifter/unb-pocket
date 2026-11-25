import React, { useEffect, useRef } from 'react';

type DomPurifyModule = typeof import('dompurify');
let domPurifyPromise: Promise<DomPurifyModule['default']> | undefined;

function loadDOMPurify(): Promise<DomPurifyModule['default']> {
  domPurifyPromise ??= import('dompurify').then((mod) => mod.default ?? (mod as unknown as DomPurifyModule['default']));
  return domPurifyPromise;
}

const SAFE_ELEMENTS = [
  'div',
  'section',
  'header',
  'footer',
  'p',
  'br',
  'hr',
  'b',
  'i',
  'strong',
  'em',
  'u',
  'small',
  'ul',
  'ol',
  'li',
  'dl',
  'dt',
  'dd',
  'blockquote',
  'code',
  'pre',
  'span',
  'a',
  'h1',
  'h2',
  'h3',
  'h4',
  'h5',
  'h6',
  'table',
  'caption',
  'thead',
  'tbody',
  'tfoot',
  'tr',
  'th',
  'td',
];

const SAFE_ATTRIBUTES = ['href', 'title', 'rel', 'target', 'colspan', 'rowspan'];

interface ElementWithSetHTML extends HTMLElement {
  setHTML(
    input: string,
    options?: {
      sanitizer?: {
        elements?: string[];
        attributes?: string[];
      };
    },
  ): void;
}

export async function safeSetHTML(el: HTMLElement, html: string): Promise<void> {
  if ('setHTML' in Element.prototype) {
    (el as ElementWithSetHTML).setHTML(html, {
      sanitizer: {
        elements: SAFE_ELEMENTS,
        attributes: SAFE_ATTRIBUTES,
      },
    });
  } else {
    const DOMPurify = await loadDOMPurify();
    el.innerHTML = DOMPurify.sanitize(html, {
      ALLOWED_TAGS: [...SAFE_ELEMENTS, '#text'],
      ALLOWED_ATTR: SAFE_ATTRIBUTES,
      ALLOW_ARIA_ATTR: false,
      ALLOW_DATA_ATTR: false,
      KEEP_CONTENT: false,
    });
  }

  // Enforce noopener noreferrer on sanitized links
  el.querySelectorAll('a[href]').forEach((anchor) => {
    anchor.setAttribute('target', '_blank');
    anchor.setAttribute('rel', 'noopener noreferrer');
  });
}

interface SafeHtmlContentProps {
  readonly html: string;
  readonly className?: string;
}

export const SafeHtmlContent: React.FC<SafeHtmlContentProps> = ({ html, className }) => {
  const containerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    let active = true;
    void safeSetHTML(el, html).then(() => {
      if (!active && el) {
        el.textContent = '';
      }
    });
    return () => {
      active = false;
    };
  }, [html]);

  return <div ref={containerRef} className={className ?? 'ty-body'} />;
};
