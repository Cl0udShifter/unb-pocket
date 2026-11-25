import { expect, test, type Page, type Route } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import QRCode from 'qrcode';

const SCREENSHOT_DIR = path.resolve(process.cwd(), 'test-results', 'screenshots');
fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });

const FE_CIC0004 = '0123456789ABCDEF0123456789ABCDEF01234567';
const QR_PAYLOAD_16 = '2210998872026204';

function encodeLatin1Buffer(str: string): Buffer {
  return Buffer.from(str, 'latin1');
}

function encodeCookiesBase64(): string {
  const cookies = [
    {
      name: 'JSESSIONID',
      value: 'SIGAA_E2E_1',
      domain: 'sigaa.unb.br',
      hostOnly: true,
      path: '/sigaa',
      secure: true,
      expiresAt: null,
    },
  ];
  return Buffer.from(JSON.stringify(cookies), 'utf8').toString('base64');
}

function escapePdfString(text: string): string {
  return text.replace(/\\/g, '\\\\').replace(/\(/g, '\\(').replace(/\)/g, '\\)');
}

/**
 * Generates a valid multi-object PDF 1.4 from object body buffers.
 */
function assemblePdf(objects: readonly Buffer[]): Buffer {
  const header = Buffer.from('%PDF-1.4\n%\xE2\xE3\xCF\xD3\n', 'binary');
  const parts: Buffer[] = [header];
  const offsets: number[] = [0];
  let cursor = header.length;

  objects.forEach((body, idx) => {
    const objNum = idx + 1;
    offsets.push(cursor);
    const prefix = Buffer.from(`${objNum} 0 obj\n`, 'ascii');
    const suffix = Buffer.from('\nendobj\n', 'ascii');
    parts.push(prefix, body, suffix);
    cursor += prefix.length + body.length + suffix.length;
  });

  const xrefStart = cursor;
  const xrefLines = [`xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`];
  for (let i = 1; i <= objects.length; i += 1) {
    xrefLines.push(`${String(offsets[i]).padStart(10, '0')} 00000 n \n`);
  }
  const trailer = `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xrefStart}\n%%EOF\n`;
  parts.push(Buffer.from(xrefLines.join(''), 'ascii'), Buffer.from(trailer, 'ascii'));
  return Buffer.concat(parts);
}

/**
 * Builds a real 3-page landscape RU Menu PDF (Cafe, Almoco, Jantar) around the current week
 * so WorkerPdfExtractor + pdfjs-dist + RuMenuPdfParser parse all 7 days end-to-end.
 */
function buildRuWeeklyMenuPdf(): Buffer {
  const now = new Date();
  // Find Monday of current week in UTC-3
  const brNow = new Date(now.getTime() - 3 * 3600 * 1000);
  const dow = brNow.getUTCDay(); // 0=Sun..6=Sat
  const diffToMon = dow === 0 ? -6 : 1 - dow;
  const monday = new Date(brNow.getTime() + diffToMon * 86400 * 1000);

  const dayNames = [
    'Segunda-feira',
    'Terca-feira',
    'Quarta-feira',
    'Quinta-feira',
    'Sexta-feira',
    'Sabado',
    'Domingo',
  ];

  const dayHeaders = dayNames.map((name, idx) => {
    const d = new Date(monday.getTime() + idx * 86400 * 1000);
    const dd = String(d.getUTCDate()).padStart(2, '0');
    const mm = String(d.getUTCMonth() + 1).padStart(2, '0');
    const yyyy = d.getUTCFullYear();
    return { name, dateStr: `${dd}/${mm}/${yyyy}` };
  });

  const colXs = [20, 128, 226, 324, 422, 520, 618, 716, 814];
  // PDF y-coords (page height = 595, top = 595 - y)
  const rowYs = [500, 455, 415, 375, 335, 295, 255, 215];

  const mealRowsByPage: readonly (readonly [string, string][])[] = [
    // Page 1: Cafe da Manha
    [
      ['Panificacao', 'Pao Frances Integral'],
      ['Complemento Padrao', 'Ovos Mexidos'],
      ['Complemento Vegano', 'Homus de Grao-de-Bico'],
      ['Fruta', 'Mamao Formosa'],
      ['Bebida', 'Cafe e Leite Quente'],
      ['Extra', 'Geleia de Goiaba'],
    ],
    // Page 2: Almoco
    [
      ['Prato Principal Padrao', 'Frango Grelhado ao Limao'],
      ['Ovolactovegetariano', 'Quiche de Queijo Minas'],
      ['Vegetariano Estrito', 'Moqueca de Banana-da-Terra'],
      ['Guarnicao', 'Farofa de Cenoura e Arroz'],
      ['Salada', 'Alface Crespa e Tomate'],
      ['Sobremesa', 'Laranja Pera'],
    ],
    // Page 3: Jantar
    [
      ['Prato Principal Padrao', 'Iscas Bovinas Aceboladas'],
      ['Ovolactovegetariano', 'Omelete de Espinafre'],
      ['Vegetariano Estrito', 'Curry de Lentilha Vermelha'],
      ['Sopa', 'Creme de Abobora com Gengibre'],
      ['Salada', 'Rucula com Beterraba'],
      ['Sobremesa', 'Melancia em Cubos'],
    ],
  ];

  const buildPageContent = (rows: readonly [string, string][]): Buffer => {
    const ops: string[] = ['0.5 w'];
    // Draw horizontal grid lines
    for (const y of rowYs) {
      ops.push(`${colXs[0]} ${y} m ${colXs[colXs.length - 1]} ${y} l S`);
    }
    // Draw vertical grid lines
    const topY = rowYs[0];
    const botY = rowYs[rowYs.length - 1];
    for (const x of colXs) {
      ops.push(`${x} ${botY} m ${x} ${topY} l S`);
    }

    // Header row (between rowYs[0]=500 and rowYs[1]=455)
    ops.push(`BT /F1 8 Tf ${colXs[0] + 4} 475 Td (Categoria) Tj ET`);
    dayHeaders.forEach((dh, i) => {
      const cx = colXs[i + 1] + 4;
      ops.push(`BT /F1 7 Tf ${cx} 482 Td (${escapePdfString(dh.name)}) Tj ET`);
      ops.push(`BT /F1 7 Tf ${cx} 466 Td (${escapePdfString(dh.dateStr)}) Tj ET`);
    });

    // Dish rows
    rows.forEach(([cat, dish], rIdx) => {
      const textY = rowYs[rIdx + 1] - 24;
      ops.push(`BT /F1 7 Tf ${colXs[0] + 4} ${textY} Td (${escapePdfString(cat)}) Tj ET`);
      for (let d = 0; d < 7; d += 1) {
        const cx = colXs[d + 1] + 4;
        ops.push(`BT /F1 7 Tf ${cx} ${textY} Td (${escapePdfString(dish)}) Tj ET`);
      }
    });

    const streamStr = ops.join('\n');
    const buf = Buffer.from(streamStr, 'ascii');
    return Buffer.concat([
      Buffer.from(`<< /Length ${buf.length} >>\nstream\n`, 'ascii'),
      buf,
      Buffer.from('\nendstream', 'ascii'),
    ]);
  };

  // Objects:
  // 1: Catalog, 2: Pages, 3: Font Helvetica
  // 4,5: Page1 + Content1; 6,7: Page2 + Content2; 8,9: Page3 + Content3
  const c1 = buildPageContent(mealRowsByPage[0]);
  const c2 = buildPageContent(mealRowsByPage[1]);
  const c3 = buildPageContent(mealRowsByPage[2]);

  return assemblePdf([
    Buffer.from('<< /Type /Catalog /Pages 2 0 R >>', 'ascii'),
    Buffer.from('<< /Type /Pages /Kids [4 0 R 6 0 R 8 0 R] /Count 3 >>', 'ascii'),
    Buffer.from('<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>', 'ascii'),
    Buffer.from(
      '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 842 595] /Resources << /Font << /F1 3 0 R >> >> /Contents 5 0 R >>',
      'ascii',
    ),
    c1,
    Buffer.from(
      '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 842 595] /Resources << /Font << /F1 3 0 R >> >> /Contents 7 0 R >>',
      'ascii',
    ),
    c2,
    Buffer.from(
      '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 842 595] /Resources << /Font << /F1 3 0 R >> >> /Contents 9 0 R >>',
      'ascii',
    ),
    c3,
  ]);
}

/**
 * Builds a real Carteirinha Estudantil PDF containing a 250x250 RGB QR Code XObject
 * encoding `2210998872026204` so WorkerPdfExtractor + jsQR decode it end-to-end.
 */
function buildCarteirinhaPdf(): Buffer {
  const qr = QRCode.create(QR_PAYLOAD_16, { errorCorrectionLevel: 'M' });
  const moduleCount = qr.modules.size;
  const quietZone = 4;
  const totalModules = moduleCount + quietZone * 2;
  const imgSize = 250;
  const rgb = Buffer.alloc(imgSize * imgSize * 3, 255);

  for (let y = 0; y < imgSize; y += 1) {
    const my = Math.floor((y / imgSize) * totalModules) - quietZone;
    for (let x = 0; x < imgSize; x += 1) {
      const mx = Math.floor((x / imgSize) * totalModules) - quietZone;
      const isDark =
        mx >= 0 && mx < moduleCount && my >= 0 && my < moduleCount
          ? Boolean(qr.modules.get(my, mx))
          : false;
      if (isDark) {
        const dst = (y * imgSize + x) * 3;
        rgb[dst] = 0;
        rgb[dst + 1] = 0;
        rgb[dst + 2] = 0;
      }
    }
  }

  const compressedRgb = zlib.deflateSync(rgb);
  const imgObj = Buffer.concat([
    Buffer.from(
      `<< /Type /XObject /Subtype /Image /Width 250 /Height 250 /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode /Length ${compressedRgb.length} >>\nstream\n`,
      'ascii',
    ),
    compressedRgb,
    Buffer.from('\nendstream', 'ascii'),
  ]);

  const contentOps = [
    'BT /F1 11 Tf 24 260 Td (MARIA CLARA SILVA) Tj ET',
    'BT /F1 10 Tf 24 240 Td (Matricula: 221099887) Tj ET',
    'BT /F1 10 Tf 24 220 Td (CPF: 123.456.789-00) Tj ET',
    'BT /F1 10 Tf 24 200 Td (Curso: CIENCIA DA COMPUTACAO) Tj ET',
    'BT /F1 10 Tf 24 180 Td (Nivel: GRADUACAO) Tj ET',
    'BT /F1 10 Tf 24 160 Td (Validade: 31/12/2026) Tj ET',
    'q 120 0 0 120 260 120 cm /Im4 Do Q',
  ].join('\n');
  const contentBuf = Buffer.from(contentOps, 'ascii');
  const contentObj = Buffer.concat([
    Buffer.from(`<< /Length ${contentBuf.length} >>\nstream\n`, 'ascii'),
    contentBuf,
    Buffer.from('\nendstream', 'ascii'),
  ]);

  return assemblePdf([
    Buffer.from('<< /Type /Catalog /Pages 2 0 R >>', 'ascii'),
    Buffer.from('<< /Type /Pages /Kids [4 0 R] /Count 1 >>', 'ascii'),
    Buffer.from('<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>', 'ascii'),
    Buffer.from(
      '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 420 310] /Resources << /Font << /F1 3 0 R >> /XObject << /Im4 6 0 R >> >> /Contents 5 0 R >>',
      'ascii',
    ),
    contentObj,
    imgObj,
  ]);
}

const RU_MENU_PDF = buildRuWeeklyMenuPdf();
const CARTEIRINHA_PDF = buildCarteirinhaPdf();

const MOCK_RU_MENU_PAGE_JSON = JSON.stringify({
  modified_gmt: '2026-11-23T10:00:00',
  content: {
    rendered: `
      <h3>Cardápio Darcy Ribeiro</h3>
      <p><a href="https://ru.unb.br/wp-content/uploads/2026/11/Cardapio_Darcy_23a29.pdf">Cardápio Semanal</a></p>
      <h3>Cardápio Ceilândia</h3>
      <p><a href="https://ru.unb.br/wp-content/uploads/2026/11/Cardapio_Ceilandia_23a29.pdf">Cardápio Semanal</a></p>
    `,
  },
});

const MOCK_RU_PRICES_JSON = JSON.stringify({
  modified_gmt: '2026-11-01T10:00:00',
  content: {
    rendered: `
      <p>Resolução CAD 0037/2022</p>
      <table>
        <tr><td>Grupo I</td><td>Assistência Estudantil</td><td>Isento</td><td>Isento</td><td>Isento</td></tr>
        <tr><td>Grupo II</td><td>Estudantes Graduação</td><td>R$ 2,75</td><td>R$ 6,10</td><td>R$ 6,10</td></tr>
        <tr><td>Grupo III</td><td>Servidores e Visitantes</td><td>R$ 7,50</td><td>R$ 16,50</td><td>R$ 16,50</td></tr>
      </table>
      <p>Formas de pagamento: PIX, GRU e Cartão de Débito.</p>
    `,
  },
});

const MOCK_RU_NOTICES_JSON = JSON.stringify([
  {
    id: 501,
    date: '2026-11-24T08:00:00',
    link: 'https://ru.unb.br/aviso-funcionamento',
    title: { rendered: 'Horário normal nos restaurantes universitários nesta semana' },
    excerpt: { rendered: '<p>Atendimento regular para café, almoço e jantar no campus Darcy Ribeiro.</p>' },
    content: { rendered: '<p>Atendimento regular para café, almoço e jantar no campus Darcy Ribeiro.</p>' },
  },
]);

const SAMPLE_CAS_HTML = `<!doctype html><html><body>
  <form id="login-form" action="/sso-server/login?service=https%3A%2F%2Fsigaa.unb.br%2Fsigaa%2Fportais%2Fdiscente%2Fdiscente.jsf" method="post">
    <input type="hidden" name="lt" value="LT-E2E-UNB" />
    <input type="hidden" name="execution" value="e1s1" />
    <input type="hidden" name="_eventId" value="submit" />
    <input name="username" />
    <input name="password" type="password" />
  </form>
</body></html>`;

const SAMPLE_PORTAL_HOME_HTML = `<!doctype html><html><body>
  <div id="info-usuario">
    <p class="usuario"><span>Maria Clara</span></p>
    <p class="unidade">Departamento de Ciência da Computação</p>
    <p class="periodo-atual">Semestre atual: <strong>2026.2</strong></p>
  </div>
  <div id="perfil-docente">
    <p class="info-docente"><span class="nome"><b>MARIA CLARA SILVA</b></span>Estudante de Computação</p>
  </div>
  <div id="agenda-docente">
    <table>
      <tr><td>Matrícula:</td><td>221099887</td></tr>
      <tr><td>Curso:</td><td>CIÊNCIA DA COMPUTAÇÃO - BACHARELADO</td></tr>
      <tr><td>Nível:</td><td>GRADUAÇÃO</td></tr>
      <tr><td>Status:</td><td>ATIVO</td></tr>
      <tr><td>E-Mail:</td><td>ma***@aluno.unb.br</td></tr>
      <tr><td>Entrada:</td><td>2022.1</td></tr>
      <tr><td>IRA:</td><td>4.4210</td></tr>
      <tr><td>MP:</td><td>4.5000</td></tr>
      <tr><td>CH. Total Currículo:</td><td>3240</td></tr>
      <tr><td>CH. Obrigatória Pendente:</td><td>360</td></tr>
      <tr><td>CH. Optativa Pendente:</td><td>120</td></tr>
      <tr><td>CH. Complementar Pendente:</td><td>30</td></tr>
      <tr><td colspan="2"><div style="width: 84.2%"></div>84.2% Integralizado</td></tr>
    </table>
  </div>
  <form id="menu:form_menu_discente" action="/sigaa/portais/discente/discente.jsf" method="post">
    <input type="hidden" name="menu:form_menu_discente" value="menu:form_menu_discente" />
    <input type="hidden" name="id" value="987654" />
    <input type="hidden" name="jscook_action" value="" />
    <input type="hidden" name="javax.faces.ViewState" value="j_id1" />
  </form>
  <form id="formExibirExtrato" action="/sigaa/portais/discente/discente.jsf" method="post">
    <input type="hidden" name="formExibirExtrato" value="formExibirExtrato" />
    <input type="hidden" name="javax.faces.ViewState" value="j_id1" />
    <a onclick="jsfcljs(document.getElementById('formExibirExtrato'),{'formExibirExtrato:btnExtrato':'formExibirExtrato:btnExtrato'},'');return false;">Extrato RU</a>
  </form>
  <div id="turmas-portal">
    <table>
      <tbody>
        <tr>
          <td class="descricao">
            <form id="form_acessarTurmaVirtual" action="/sigaa/portais/discente/discente.jsf" method="post">
              <input type="hidden" name="form_acessarTurmaVirtual" value="form_acessarTurmaVirtual" />
              <input type="hidden" name="javax.faces.ViewState" value="j_id1" />
              <a onclick="jsfcljs(document.getElementById('form_acessarTurmaVirtual'),{'form_acessarTurmaVirtual:turmaVirtual':'form_acessarTurmaVirtual:turmaVirtual','frontEndIdTurma':'${FE_CIC0004}'},'');return false;">ESTRUTURAS DE DADOS E ALGORITMOS</a>
            </form>
          </td>
          <td class="info">PAT AT 019</td>
          <td class="info">23456M34</td>
        </tr>
        <tr><td id="linha_554433"></td></tr>
      </tbody>
    </table>
  </div>
  <div id="atualizacoes-turma">
    <div class="rotator">
      <table>
        <tr><td>24/11/2026 - <a onclick="jsfcljs(f,{'idTurma':'554433'},'')">CIC0090 (2026.2)</a></td></tr>
        <tr><td>Roteiro do Laboratório de Árvores</td></tr>
      </table>
    </div>
  </div>
</body></html>`;

const SAMPLE_EXTRATO_HTML = `<!doctype html><html><body>
  <div>
    <h4>Extrato no Restaurante Universitário</h4>
    <table>
      <tr><td>-</td><td>Saldo anterior</td><td>R$ 30,50</td></tr>
      <tr><td>24/11/2026 12:15</td><td>Consumo Almoço Grupo II - Darcy Ribeiro</td><td>-R$ 6,10</td></tr>
      <tr><td>-</td><td>Saldo</td><td>R$ 24,40</td></tr>
    </table>
  </div>
</body></html>`;

const SAMPLE_ATESTADO_HTML = `<!doctype html><html><head><title>Atestado de Matrícula UnB</title></head><body>
  <h2>UNIVERSIDADE DE BRASÍLIA - ATESTADO DE MATRÍCULA 2026.2</h2>
  <p>Discente: MARIA CLARA SILVA (221099887)</p>
  <table class="listagem">
    <tbody>
      <tr>
        <td>CIC0090</td>
        <td>ESTRUTURAS DE DADOS E ALGORITMOS<br/>Prof. Dr. Bruno Ribas<br/>Local: PAT AT 019</td>
        <td>01</td>
        <td>MATRICULADO</td>
        <td>23456M34</td>
      </tr>
    </tbody>
  </table>
  <p>Código de Verificação: UNB2026VERIF99</p>
</body></html>`;

const SAMPLE_BOLETIM_HTML = `<!doctype html><html><body>
  <table class="tabelaRelatorio">
    <caption>2026.1</caption>
    <tbody>
      <tr>
        <td>CIC0004</td>
        <td>ALGORITMOS E PROGRAMAÇÃO DE COMPUTADORES</td>
        <td>9,4</td>
        <td>SS</td>
        <td>0</td>
        <td>APROVADO</td>
      </tr>
      <tr>
        <td>MAT0025</td>
        <td>CÁLCULO 1</td>
        <td>8,2</td>
        <td>MS</td>
        <td>2</td>
        <td>APROVADO</td>
      </tr>
    </tbody>
  </table>
</body></html>`;

const SAMPLE_AVA_HOME_HTML = `<!doctype html><html><body>
  <span id="linkCodigoTurma">CIC0090 - </span>
  <span id="linkNomeTurma">ESTRUTURAS DE DADOS E ALGORITMOS</span>
  <span id="linkPeriodoTurma">(2026.2)</span>
  <div id="barraDireita">24 / 30 aulas <div class="progress-bar" style="width: 80%">80%</div></div>
  <div id="ultimaNoticia">
    <h4>Prova 2 confirmada para sexta-feira - 24/11/2026 09:30</h4>
    <span class="conteudoNoticia"><p>Revisem árvores AVL e grafos.</p><script>window.__xssExecuted = true;</script></span>
    <small><i>Prof. Dr. Bruno Ribas</i></small>
  </div>
  <div class="topico-aula">
    <h3>Semana 12 - Árvores Balanceadas (18/11/2026 - 24/11/2026)</h3>
    <p>Implementação de rotações simples e duplas em AVL.</p>
  </div>
  <form id="formMenu" action="/sigaa/ava/index.jsf" method="post">
    <input type="hidden" name="formMenu" value="formMenu" />
    <input type="hidden" name="javax.faces.ViewState" value="j_id_ava1" />
    <a onclick="jsfcljs(document.getElementById('formMenu'),{'formMenu:freq':'frequencia'},'');return false;">Frequência</a>
    <a onclick="jsfcljs(document.getElementById('formMenu'),{'formMenu:notas':'notas'},'');return false;">Ver Notas</a>
    <a onclick="jsfcljs(document.getElementById('formMenu'),{'formMenu:aval':'avaliacoes'},'');return false;">Avaliações</a>
    <a onclick="jsfcljs(document.getElementById('formMenu'),{'formMenu:tar':'tarefas'},'');return false;">Tarefas</a>
  </form>
</body></html>`;

const SAMPLE_AVA_FREQ_HTML = `<!doctype html><html><body>
  <table class="listagem">
    <tbody>
      <tr><td>20/11/2026</td><td>Presente</td></tr>
      <tr><td>22/11/2026</td><td>2 Faltas</td></tr>
    </tbody>
  </table>
  <p>Total de Faltas: 6</p>
  <p>Máximo de Faltas Permitido: 15</p>
</body></html>`;

const SAMPLE_AVA_NOTAS_HTML = `<!doctype html><html><body>
  <table class="tabelaRelatorio">
    <thead><tr><th>Matrícula</th><th>Nome</th><th>Unidade 1</th><th>Unidade 2</th><th>Resultado</th><th>Faltas</th><th>Situação</th></tr></thead>
    <tbody>
      <tr><td>221099887</td><td>MARIA CLARA SILVA</td><td>9,2</td><td>8,8</td><td>9,0</td><td>2</td><td>CURSANDO</td></tr>
    </tbody>
  </table>
</body></html>`;

async function installUnbMocks(page: Page): Promise<void> {
  let loggedIn = false;

  await page.route('https://ru.unb.br/wp-json/**', async (route: Route) => {
    const url = route.request().url();
    const corsHeaders = {
      'Access-Control-Allow-Origin': '*',
      'Content-Type': 'application/json; charset=utf-8',
    };
    if (url.includes('/pages/12853')) {
      await route.fulfill({
        status: 200,
        headers: corsHeaders,
        body: MOCK_RU_MENU_PAGE_JSON,
      });
      return;
    }
    if (url.includes('/pages/13245')) {
      await route.fulfill({
        status: 200,
        headers: corsHeaders,
        body: MOCK_RU_PRICES_JSON,
      });
      return;
    }
    if (url.includes('/posts')) {
      await route.fulfill({
        status: 200,
        headers: corsHeaders,
        body: MOCK_RU_NOTICES_JSON,
      });
      return;
    }
    await route.fulfill({ status: 200, headers: corsHeaders, body: '[]' });
  });

  await page.route('**/api/proxy', async (route: Route) => {
    const reqJson = JSON.parse(route.request().postData() ?? '{}') as {
      url: string;
      method: string;
      formBody?: string | null;
    };
    const decodedForm = decodeURIComponent(reqJson.formBody ?? '');

    if (reqJson.url.includes('/wp-json/')) {
      const jsonBody = reqJson.url.includes('/pages/12853')
        ? MOCK_RU_MENU_PAGE_JSON
        : reqJson.url.includes('/pages/13245')
          ? MOCK_RU_PRICES_JSON
          : reqJson.url.includes('/posts')
            ? MOCK_RU_NOTICES_JSON
            : '[]';
      await route.fulfill({
        status: 200,
        headers: {
          'Content-Type': 'application/json; charset=utf-8',
          'X-Unb-Status': '200',
          'X-Unb-Final-Url': reqJson.url,
        },
        body: Buffer.from(jsonBody, 'utf8'),
      });
      return;
    }

    if (reqJson.url.includes('.pdf')) {
      await route.fulfill({
        status: 200,
        headers: {
          'Content-Type': 'application/pdf',
          'X-Unb-Status': '200',
          'X-Unb-Final-Url': reqJson.url,
        },
        body: RU_MENU_PDF,
      });
      return;
    }

    if (reqJson.method === 'GET' && reqJson.url.includes('discente.jsf') && !loggedIn) {
      await route.fulfill({
        status: 200,
        headers: {
          'Content-Type': 'text/html;charset=ISO-8859-1',
          'X-Unb-Status': '200',
          'X-Unb-Final-Url': 'https://autenticacao.unb.br/sso-server/login',
          'X-Unb-Cookies': encodeCookiesBase64(),
        },
        body: encodeLatin1Buffer(SAMPLE_CAS_HTML),
      });
      return;
    }

    if (reqJson.url.includes('/sso-server/login')) {
      loggedIn = true;
      await route.fulfill({
        status: 200,
        headers: {
          'Content-Type': 'text/html;charset=ISO-8859-1',
          'X-Unb-Status': '200',
          'X-Unb-Final-Url': 'https://sigaa.unb.br/sigaa/portais/discente/discente.jsf',
          'X-Unb-Cookies': encodeCookiesBase64(),
        },
        body: encodeLatin1Buffer(SAMPLE_PORTAL_HOME_HTML),
      });
      return;
    }

    if (decodedForm.includes('emitiCarteiraEstudantilUnb')) {
      await route.fulfill({
        status: 200,
        headers: {
          'Content-Type': 'application/pdf',
          'X-Unb-Status': '200',
          'X-Unb-Final-Url': 'https://sigaa.unb.br/sigaa/portais/discente/carteirinha.pdf',
          'X-Unb-Cookies': encodeCookiesBase64(),
        },
        body: CARTEIRINHA_PDF,
      });
      return;
    }

    let html = SAMPLE_PORTAL_HOME_HTML;
    if (decodedForm.includes('formExibirExtrato')) {
      html = SAMPLE_EXTRATO_HTML;
    } else if (decodedForm.includes('atestadoMatriculaUnb')) {
      html = SAMPLE_ATESTADO_HTML;
    } else if (decodedForm.includes('relatorioNotasAluno')) {
      html = SAMPLE_BOLETIM_HTML;
    } else if (decodedForm.includes('turmaVirtual')) {
      html = SAMPLE_AVA_HOME_HTML;
    } else if (decodedForm.includes('formMenu:freq')) {
      html = SAMPLE_AVA_FREQ_HTML;
    } else if (decodedForm.includes('formMenu:notas')) {
      html = SAMPLE_AVA_NOTAS_HTML;
    }

    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/html;charset=ISO-8859-1',
        'X-Unb-Status': '200',
        'X-Unb-Final-Url': reqJson.url,
        'X-Unb-Cookies': encodeCookiesBase64(),
      },
      body: encodeLatin1Buffer(html),
    });
  });
}

async function assertNoHorizontalOverflow(page: Page): Promise<void> {
  const dims = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  expect(dims.scrollWidth).toBeLessThanOrEqual(dims.innerWidth);
}

test.describe('UnB Pocket PWA iOS Safari (WebKit) 1:1 Parity', () => {
  test('renders RU, CAS Login, Turmas/AVA, Discente, 3D QR Pass, Document Viewer, Light/Dark, and Offline mode cleanly on iPhone', async ({
    page,
    context,
  }) => {
    const errors: string[] = [];
    page.on('pageerror', (err) => errors.push(`pageerror: ${err.message}`));
    page.on('console', (msg) => {
      if (msg.type() === 'error') {
        errors.push(`console.error: ${msg.text()}`);
      }
    });

    await page.setViewportSize({ width: 390, height: 844 });
    await page.emulateMedia({ colorScheme: 'dark', reducedMotion: 'reduce' });
    await installUnbMocks(page);

    await page.goto('/');

    // 1. Cardapio RU structured PDF parsing in WebKit Worker + First-visit iOS Install Card
    await expect(page.getByRole('heading', { name: 'Cardápio RU' })).toBeVisible();
    await expect(page.getByText('Instalar no iPhone')).toBeVisible();
    await page.getByRole('button', { name: 'Ver passo a passo' }).click();
    await expect(page.getByText('1. Toque em Compartilhar no Safari')).toBeVisible();
    await expect(page.getByText('Abriu pelo WhatsApp ou Instagram?')).toBeVisible();
    await page.getByRole('button', { name: 'Fechar janela' }).click();
    await page.getByRole('button', { name: 'Dispensar aviso de instalação' }).click();
    await expect(page.getByText('Instalar no iPhone')).toBeHidden();

    await page.getByRole('tab', { name: 'Almoço' }).click();
    await expect(page.getByText('PRATOS PRINCIPAIS')).toBeVisible();
    await expect(page.getByText('Frango Grelhado ao Limao')).toBeVisible();
    await expect(page.getByText('Moqueca de Banana-da-Terra')).toBeVisible();
    await assertNoHorizontalOverflow(page);

    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '01-ru-dark-iphone.png'),
    });

    // Open Portal RU (Notices & Prices native <dialog>)
    await page.getByRole('button', { name: 'Avisos e preços do RU' }).click();
    await expect(
      page.getByText('Horário normal nos restaurantes universitários nesta semana'),
    ).toBeVisible();
    await page.getByRole('tab', { name: 'Tabela de Preços' }).click();
    await expect(page.getByText('R$ 4,50').first()).toBeVisible();
    await page.getByRole('button', { name: 'Fechar janela' }).click();

    // 2. CAS Login via Account Quick Sheet
    await page.getByRole('button', { name: 'Entrar na conta UnB' }).click();
    await page.getByRole('button', { name: 'Entrar com CAS / SIGAA' }).click();
    await expect(page.getByRole('heading', { name: 'Entrar no SIGAA' })).toBeVisible();

    await page.getByLabel('Matrícula').fill('221099887');
    await page.getByLabel('Senha do SIGAA / CAS').fill('Confirmar123!');
    await page.getByRole('button', { name: 'Conectar conta SIGAA' }).click();

    // Wait until back on home and authenticated
    await expect(page.getByRole('heading', { name: 'Cardápio RU' })).toBeVisible();

    // 3. Navigate to Turmas & Turma Virtual
    await page.getByRole('tab', { name: 'Turmas' }).click();
    await expect(page.getByRole('heading', { name: 'Aulas & Turmas' })).toBeVisible();
    await expect(page.getByText('ESTRUTURAS DE DADOS E ALGORITMOS').first()).toBeVisible();
    await assertNoHorizontalOverflow(page);

    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '02-turmas-dark-iphone.png'),
    });

    // Enter Turma Virtual (AVA) and verify XSS script tag was stripped by SafeHtmlContent
    await page.getByText('ESTRUTURAS DE DADOS E ALGORITMOS').first().click();
    await expect(page.getByText('ANDAMENTO DO CURSO')).toBeVisible();
    await page.getByRole('tab', { name: 'Notícias' }).click();
    await expect(page.getByText('Prova 2 confirmada para sexta-feira')).toBeVisible();
    const xssFlag = await page.evaluate(
      () => (window as Window & { __xssExecuted?: boolean }).__xssExecuted === true,
    );
    expect(xssFlag).toBe(false);

    await page.getByRole('button', { name: 'Voltar para turmas' }).click();

    // 4. Navigate to Perfil Discente, verify IRA/MP, RU Balance, and 3D QR Code Pass
    await page.getByRole('tab', { name: 'Discente' }).click();
    await expect(page.getByRole('heading', { name: 'Perfil Discente' })).toBeVisible();
    await expect(page.getByText('MARIA CLARA SILVA')).toBeVisible();
    await expect(page.getByText('4.4210')).toBeVisible();
    await expect(page.getByText('R$ 24,40')).toBeVisible();
    await assertNoHorizontalOverflow(page);

    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '03-discente-dark-iphone.png'),
    });

    // Flip to Full-Screen QR Pass (#FFFFFF pure canvas + decoded 16-digit QR from PDF!)
    await page.getByRole('button', { name: 'Girar para QR Code da Catraca' }).click();
    await expect(page.getByText('2210 9988 7202 6204')).toBeVisible();
    await expect(page.getByAltText('QR Code da carteirinha estudantil UnB')).toBeVisible();

    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '04-qr-pass-iphone.png'),
    });

    // Tap QR Pass to flip back
    await page.getByAltText('QR Code da carteirinha estudantil UnB').click();
    await expect(page.getByRole('heading', { name: 'Perfil Discente' })).toBeVisible();

    // 5. Open Boletim & Mencoes
    await page.getByText('Boletim & Menções').click();
    await expect(page.getByText('PERÍODO 2026.1')).toBeVisible();
    await expect(page.getByText('SS')).toBeVisible();
    await page.getByRole('button', { name: 'Voltar ao perfil' }).click();

    // 6. Emit Official Document and view inside InAppDocumentViewerModal
    await page.getByText('Documentos Digitais').click();
    await expect(page.getByRole('heading', { name: 'Documentos Digitais' })).toBeVisible();
    await page.getByRole('button', { name: 'Emitir e abrir' }).first().click();
    await expect(
      page.getByRole('dialog', { name: 'Atestado de Matrícula' }).getByText('Código: UNB2026VERIF99'),
    ).toBeVisible();
    await expect(
      page
        .getByRole('dialog', { name: 'Atestado de Matrícula' })
        .getByText('UNIVERSIDADE DE BRASÍLIA - ATESTADO DE MATRÍCULA 2026.2'),
    ).toBeVisible();
    await page.getByRole('button', { name: 'Fechar documento' }).click();
    await page.getByRole('button', { name: 'Voltar ao perfil' }).click();

    // 7. Switch to Light Theme in Account & Appearance sheet and check compact iPhone SE (375x667)
    await page.getByRole('button', { name: 'Conta UnB conectada' }).click();
    await page.getByRole('tab', { name: 'Claro' }).click();
    await page.getByRole('button', { name: 'Fechar janela' }).click();

    await page.setViewportSize({ width: 375, height: 667 });
    await page.getByRole('tab', { name: 'RU' }).click();
    await expect(page.getByRole('heading', { name: 'Cardápio RU' })).toBeVisible();
    await page.waitForTimeout(220);
    await assertNoHorizontalOverflow(page);

    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '05-ru-light-iphone-se.png'),
    });

    // 8. Check iPad layout (>= 600px NavigationRail) + Offline Mode persistence
    await page.setViewportSize({ width: 820, height: 1180 });
    await expect(page.getByRole('navigation', { name: 'Navegação lateral' })).toBeVisible();
    await assertNoHorizontalOverflow(page);

    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '06-ru-light-ipad.png'),
    });

    // Go offline and verify cached RU + Turmas + Discente remain navigable
    await context.setOffline(true);
    await page.getByRole('tab', { name: 'Discente' }).click();
    await expect(page.getByText('MARIA CLARA SILVA')).toBeVisible();
    await expect(page.getByText('R$ 24,40')).toBeVisible();
    await context.setOffline(false);

    expect(errors).toEqual([]);
  });
});
