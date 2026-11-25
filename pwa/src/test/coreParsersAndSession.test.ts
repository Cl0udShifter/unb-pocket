import QRCode from 'qrcode';
import { describe, expect, it } from 'vitest';
import {
  ALERGENICO_LIST,
  DEFAULT_SIGAA_NOTIFICACAO_CONFIG,
  type Alergenico,
} from '../core/model/models';
import { UnbHttpSession } from '../core/network/unbHttpSession';
import { CasAndJsfParser } from '../core/parser/casAndJsfParser';
import { DiscenteDocumentosParser } from '../core/parser/discenteDocumentosParser';
import { DiscentePortalParser } from '../core/parser/discentePortalParser';
import { IcsCalendarExporter } from '../core/parser/icsCalendarExporter';
import {
  createRgbImageBuffer,
  RuAllergenClassifier,
  type RgbImageBuffer,
} from '../core/parser/ruAllergenClassifier';
import { RuCalculator } from '../core/parser/ruCalculator';
import { RuMenuFreshness, ScheduleSelectionResolver } from '../core/parser/ruMenuFreshness';
import { RuMenuPdfParser } from '../core/parser/ruMenuPdfParser';
import { RuWpParser } from '../core/parser/ruWpParser';
import { SigaaNotificationPlanner } from '../core/parser/sigaaNotificationPlanner';
import { SigaaScheduleDecoder } from '../core/parser/sigaaScheduleDecoder';
import { TurmaVirtualParser } from '../core/parser/turmaVirtualParser';

describe('UnbHttpSession JSF Priority Preemption', () => {
  it('preempts queued background preload with interactive requests while preserving mutual exclusion', async () => {
    const session = new UnbHttpSession();
    let inLockCount = 0;
    let resolveFirstEntered!: () => void;
    const firstEntered = new Promise<void>((r) => {
      resolveFirstEntered = r;
    });
    let releaseFirst!: () => void;
    const releaseFirstPromise = new Promise<void>((r) => {
      releaseFirst = r;
    });

    const executionOrder: string[] = [];

    const p1 = session.withJsfLock('INTERACTIVE', async () => {
      inLockCount += 1;
      expect(inLockCount).toBe(1);
      executionOrder.push('interactive-1');
      resolveFirstEntered();
      await releaseFirstPromise;
      inLockCount -= 1;
    });

    await firstEntered;

    const pBg = session.withJsfLock('BACKGROUND_PRELOAD', async () => {
      inLockCount += 1;
      expect(inLockCount).toBe(1);
      executionOrder.push('background-1');
      inLockCount -= 1;
    });

    await Promise.resolve();
    expect(executionOrder).not.toContain('background-1');

    const p2 = session.withJsfLock('INTERACTIVE', async () => {
      inLockCount += 1;
      expect(inLockCount).toBe(1);
      executionOrder.push('interactive-2');
      inLockCount -= 1;
    });

    releaseFirst();
    await Promise.all([p1, p2, pBg]);
    expect(executionOrder).toEqual(['interactive-1', 'interactive-2', 'background-1']);
  });
});

describe('CasAndJsfParser & SigaaScheduleDecoder', () => {
  it('parses CAS login form and JSF cljs parameters', () => {
    const casHtml = `
      <html><body>
        <form id="login-form" action="/sso-server/login;jsessionid=123?service=sigaa">
          <input type="hidden" name="lt" value="LT-999" />
          <input type="hidden" name="execution" value="e1s1" />
          <input type="hidden" name="_eventId" value="submit" />
        </form>
      </body></html>
    `;
    expect(CasAndJsfParser.isCasLoginPage(casHtml)).toBe(true);
    expect(CasAndJsfParser.parseCasLoginForm(casHtml)).toEqual({
      action: '/sso-server/login;jsessionid=123?service=sigaa',
      lt: 'LT-999',
      execution: 'e1s1',
      eventId: 'submit',
    });

    const slots = SigaaScheduleDecoder.decode('24M12 6T34 (10/08/2026 - 14/12/2026)');
    expect(slots).toHaveLength(6);
    expect(slots[0]).toMatchObject({ diaSemana: 'SEGUNDA', turno: 'MANHA', inicio: '08:00', fim: '08:55' });
    expect(slots[5]).toMatchObject({ diaSemana: 'SEXTA', turno: 'TARDE', inicio: '16:00', fim: '16:55' });
  });
});

describe('DiscentePortalParser', () => {
  it('parses full portal home profile, indices, feed, turmas, and RU statement', () => {
    const html = `
      <html><body>
        <div id="info-usuario">
          <p class="usuario"><span>Daniel Alexandro C. Junior</span></p>
          <p class="unidade">DEPTO ENGENHARIA ELETRICA (11.01.01.11.03)</p>
          <p class="periodo-atual"><strong>2026.2</strong></p>
        </div>
        <form id="menu:form_menu_discente" action="/sigaa/portais/discente/discente.jsf" method="post">
          <input type="hidden" name="id" value="331956" />
        </form>
        <script>var url = "/sigaa/public/curso/portal.jsf?id=414330&lc=pt_BR";</script>
        <div id="perfil-docente">
          <div class="foto"><img src="//arquivos.unb.br/arquivos/foto.png" /></div>
          <p class="info-docente">
            <span class="nome"><b>Daniel Alexandro Contreira Junior</b></span>
            Conhecimento em lingua inglesa e redes.
          </p>
        </div>
        <div id="agenda-docente">
          <table>
            <tr><td>Matrícula:</td><td>190026308</td></tr>
            <tr><td>Curso:</td><td>ENGENHARIA DE REDES DE COMUNICAÇÃO/ENE - Bacharelado - MT</td></tr>
            <tr><td>Nível:</td><td>GRADUAÇÃO</td></tr>
            <tr><td>Status:</td><td>ATIVO</td></tr>
            <tr><td>E-Mail:</td><td>daniel.contreira16@...</td></tr>
            <tr><td>Entrada:</td><td>2019.1</td></tr>
            <tr><td><acronym title="IRA">IRA:</acronym></td><td>3.2102</td></tr>
            <tr><td><acronym title="MP">MP:</acronym></td><td>3.4563</td></tr>
            <tr><td>CH. Obrigatória Pendente</td><td>420</td></tr>
            <tr><td>CH. Optativa Pendente</td><td>540</td></tr>
            <tr><td>CH. Total Currículo</td><td>3750</td></tr>
            <tr><td>CH. Complementar Pendente</td><td>0</td></tr>
            <tr><td colspan="2"><div class="progress-bar" style="width: 74.4%"></div></td></tr>
            <tr><td colspan="2">74% Integralizado</td></tr>
          </table>
        </div>
        <input type="hidden" name="javax.faces.ViewState" id="javax.faces.ViewState" value="j_id1" />
        <div id="turmas-portal">
          <div id="atualizacoes-turma">
            <div class="rotator">
              <table style="display: table">
                <tr><td>22/11/2026 - <a onclick="jsfcljs(document.getElementById('f'),{'idTurma':'1616625'},'')">LABORATÓRIO DE PRINCÍPIOS DE COMUNICAÇÃO (2026.2)</a></td></tr>
                <tr><td>Nova Notícia: Experimento 11 - Roteiro</td></tr>
              </table>
            </div>
          </div>
          <table>
            <tbody>
              <tr class="odd">
                <td class="descricao">
                  <form id="form_acessarTurmaVirtualj_id_3">
                    <a onclick="jsfcljs(document.getElementById('form_acessarTurmaVirtualj_id_3'),{'form_acessarTurmaVirtualj_id_3:btn':'form_acessarTurmaVirtualj_id_3:btn','frontEndIdTurma':'7D4F1E83699CC81005304EC1877AE1F7888882CD'},'')">REDES SEM FIO</a>
                  </form>
                </td>
                <td class="info">FT AT-31/12</td>
                <td class="info">24M12 <div class="popUp">Segunda-feira 08:00 às 09:50<br/>Quarta-feira 08:00 às 09:50</div></td>
              </tr>
              <tr><td colspan="5" id="linha_1614167" style="display:none"></td></tr>
            </tbody>
          </table>
        </div>
      </body></html>
    `;

    const res = DiscentePortalParser.parseDiscenteResumo(html);
    expect(res.ok).toBe(true);
    if (!res.ok) return;
    expect(res.value.perfil.idDiscente).toBe('331956');
    expect(res.value.perfil.matricula).toBe('190026308');
    expect(res.value.perfil.fotoUrl).toBe('https://arquivos.unb.br/arquivos/foto.png');
    expect(res.value.indices.ira).toBeCloseTo(3.2102, 4);
    expect(res.value.indices.mp).toBeCloseTo(3.4563, 4);
    expect(res.value.indices.percentualIntegralizadoExato).toBeCloseTo(74.4, 1);

    const feed = DiscentePortalParser.parseAtualizacoesFeed(html);
    expect(feed).toHaveLength(1);
    expect(feed[0].idTurma).toBe('1616625');

    const turmas = DiscentePortalParser.parseTurmasDaHome(html);
    expect(turmas).toHaveLength(1);
    expect(turmas[0].resumo.idTurma).toBe('1614167');
    expect(turmas[0].resumo.horarioSlots).toHaveLength(4);

    const extratoHtml = `
      <html><body>
        <div>
          <h4>Extrato no Restaurante Universitário</h4>
          <table>
            <tr><td>22/11/2026 20:43</td><td>Saldo</td><td>R$ 8,65</td></tr>
            <tr><td>16/11/2026 17:37</td><td>Grupo 4 Jantar</td><td>-R$ 2,50</td></tr>
            <tr><td>15/11/2026 20:43</td><td>Saldo Anterior</td><td>R$ 11,15</td></tr>
          </table>
        </div>
      </body></html>
    `;
    const saldoRes = DiscentePortalParser.parseExtratoRu(extratoHtml);
    expect(saldoRes.ok).toBe(true);
    if (!saldoRes.ok) return;
    expect(saldoRes.value.saldoAtualCentavos).toBe(865);
    expect(saldoRes.value.grupoAcesso).toBe('GRUPO_4');
    expect(saldoRes.value.refeicoesRestantes?.almocosOuJantaresRestantes).toBe(3);
    expect(saldoRes.value.refeicoesRestantes?.cafesRestantes).toBe(5);
  });
});

describe('TurmaVirtualParser', () => {
  it('extracts real notice titles, materials ignoring indicator.gif, frequency 25% rule, and grades', () => {
    const avaHtml = `
      <html><body>
        <div id="nomeTurma">
          <span id="linkCodigoTurma">ENE0295 - </span>
          <span id="linkNomeTurma">LABORATÓRIO</span>
          <span id="linkPeriodoTurma">- T04 (2026.2)</span>
        </div>
        <input type="hidden" name="idTurma" value="1616625" />
        <input type="hidden" name="javax.faces.ViewState" value="j_id4" />
        <div id="barraDireita">
          <div>Aulas (Ministradas/Total): <i>24 / 32</i></div>
          <div class="progress-bar" style="width: 75%">75%</div>
          <div class="rich-panel-body">
            22/11/2026 09:34<br/>
            <i>Experimento 11 - Roteiro</i>
            <form id="f1">
              <input type="hidden" name="id" value="24442200" />
              <a onclick="jsfcljs(document.getElementById('f1'),{'f1:v':'f1:v'},'')">(Visualizar)</a>
            </form>
          </div>
        </div>
        <form id="formAva">
          <input type="hidden" name="formAva:extraHidden" value="ok" />
          <div class="topico-aula">
            <div class="titulo">Experimentos (10/08/2026 - 14/12/2026)</div>
            <div class="conteudotopico">
              <div class="item">
                <img src="/sigaa/img/indicator.gif" style="display:none" />
                <img src="/sigaa/img/porta_arquivos/icones/zip.png" />
                <a onclick="jsfcljs(document.getElementById('formAva'),{'formAva:m1':'formAva:m1','id':'HASH_1'},'_blank')">Experimento 01</a>
              </div>
            </div>
          </div>
        </form>
      </body></html>
    `;

    const parsed = TurmaVirtualParser.parseAvaHome(avaHtml, 'FE_1');
    expect(parsed.ok).toBe(true);
    if (!parsed.ok) return;
    expect(parsed.value.detalhe.historicoNoticiasResumo[0].titulo).toBe('Experimento 11 - Roteiro');
    expect(parsed.value.detalhe.topicosAula[0].materiais[0].tipoExtensao).toBe('zip');
    expect(parsed.value.materialRefs['HASH_1'].formParams['formAva:extraHidden']).toBe('ok');

    const freqHtml = `
      <html><body>
        <fieldset>
          <legend>Mapa de Frequências</legend>
          <table class="listagem">
            <tbody>
              <tr><td>10/08/2026</td><td>Presente</td></tr>
              <tr><td>12/08/2026</td><td>Não registrado</td></tr>
              <tr><td>17/08/2026</td><td>7 Faltas</td></tr>
            </tbody>
          </table>
          <p>Presenças Registradas: 25</p>
          <p>Número de Aulas com Registro de Frequência: 32</p>
          <p>Porcentagem de Frequência em relação as Aulas com Registro de Frequência: 78%</p>
          <p>Número de Aulas definidas pela CH do Componente: 32</p>
          <p>Porcentagem de Frequência em relação a CH: 78%</p>
        </fieldset>
      </body></html>
    `;
    const freqRes = TurmaVirtualParser.parseAvaFrequencia(freqHtml);
    expect(freqRes.ok).toBe(true);
    if (!freqRes.ok) return;
    expect(freqRes.value.faltasComputadas).toBe(7);
    expect(freqRes.value.limiteMaximoFaltas).toBe(8);
    expect(freqRes.value.faltasRestantes).toBe(1);
    expect(freqRes.value.emRiscoReprovacaoPorFalta).toBe(true);
  });
});

describe('RU Allergen MSE Classifier, PDF Grid Parser, and QR Carteirinha Decoder', () => {
  it('classifies concatenated multi-icon strips using 32x32 MSE', () => {
    const legends = new Map<Alergenico, Float32Array>();
    for (const meta of ALERGENICO_LIST) {
      const v = meta.legendOrderIndex * 25;
      const bytes = new Uint8Array(32 * 32 * 3);
      for (let idx = 0; idx < bytes.length; idx += 3) {
        bytes[idx] = v;
        bytes[idx + 1] = 255 - v;
        bytes[idx + 2] = (meta.legendOrderIndex * 17) % 255;
      }
      legends.set(meta.id, RuAllergenClassifier.to32x32Vector(createRgbImageBuffer(32, 32, bytes)));
    }

    const leftVec = legends.get('LEITE')!;
    const rightVec = legends.get('GLUTEN')!;
    const stripBytes = new Uint8Array(64 * 32 * 3);
    for (let y = 0; y < 32; y += 1) {
      for (let x = 0; x < 64; x += 1) {
        const src = x < 32 ? leftVec : rightVec;
        const srcX = x < 32 ? x : x - 32;
        const srcOff = (y * 32 + srcX) * 3;
        const dstOff = (y * 64 + x) * 3;
        stripBytes[dstOff] = Math.round(src[srcOff]);
        stripBytes[dstOff + 1] = Math.round(src[srcOff + 1]);
        stripBytes[dstOff + 2] = Math.round(src[srcOff + 2]);
      }
    }

    const detected = RuAllergenClassifier.classifyStrip(
      createRgbImageBuffer(64, 32, stripBytes),
      legends,
    );
    expect(detected).toEqual(['LEITE', 'GLUTEN']);
  });

  it('decodes 16-digit student pass QR payload from RGB buffer via jsQR', () => {
    const qrMatrix = QRCode.create('7744502674419362', { errorCorrectionLevel: 'M' });
    const modules = qrMatrix.modules;
    const moduleCount = modules.size;
    const quiet = 4;
    const scale = 6;
    const imgSize = (moduleCount + quiet * 2) * scale;
    const rgb = new Uint8Array(imgSize * imgSize * 3).fill(255);

    for (let my = 0; my < moduleCount; my += 1) {
      for (let mx = 0; mx < moduleCount; mx += 1) {
        if (modules.get(mx, my)) {
          for (let dy = 0; dy < scale; dy += 1) {
            for (let dx = 0; dx < scale; dx += 1) {
              const py = (my + quiet) * scale + dy;
              const px = (mx + quiet) * scale + dx;
              const off = (py * imgSize + px) * 3;
              rgb[off] = 0;
              rgb[off + 1] = 0;
              rgb[off + 2] = 0;
            }
          }
        }
      }
    }

    const qrImg: RgbImageBuffer = createRgbImageBuffer(imgSize, imgSize, rgb);
    const parsed = DiscenteDocumentosParser.parseCarteirinha({
      textLines: [
        'UNIVERSIDADE DE BRASÍLIA',
        'Daniel Alexandro Contreira Junior',
        '190026308',
        '033.145.241-37',
        'GRADUAÇÃO',
        'VALIDADE Março 2027',
        'ENGENHARIA DE REDES DE COMUNICAÇÃO',
      ],
      img2Foto: null,
      img4Qr: qrImg,
    });

    expect(parsed.ok).toBe(true);
    if (!parsed.ok) return;
    expect(parsed.value.qrPayload16Digitos).toBe('7744502674419362');
    expect(parsed.value.matricula).toBe('190026308');
    expect(parsed.value.cpfTexto).toBe('033.145.241-37');
  });

  it('parses WordPress RU menu page, resolves Brasilia meal/day, and exports .ics calendar', () => {
    const wpJson = JSON.stringify({
      id: 12853,
      modified_gmt: '2026-11-22T12:00:00',
      content: {
        rendered: `
          <h3>Cardápio Darcy Ribeiro</h3>
          <p><a href="https://ru.unb.br/wp-content/uploads/2026/11/Darcy.pdf">23/11/2026 a 29/11/2026</a></p>
        `,
      },
    });
    const wpRes = RuWpParser.parseMenuPage(wpJson, '2026-11-24');
    expect(wpRes.ok).toBe(true);
    if (!wpRes.ok) return;
    expect(wpRes.value.campusLinks.DARCY_RIBEIRO?.pdfUrl).toContain('Darcy.pdf');

    // 2026-11-24 12:00 BRT = 15:00 UTC -> ALMOCO, TERCA
    const noonBrtMillis = Date.UTC(2026, 10, 24, 15, 0, 0);
    expect(ScheduleSelectionResolver.resolveRuMeal(noonBrtMillis)).toBe('ALMOCO');
    expect(ScheduleSelectionResolver.resolveCurrentSigaaDay(noonBrtMillis)).toBe('TERCA');
    expect(RuMenuFreshness.localDateInBrasilia(noonBrtMillis)).toBe('2026-11-24');

    const ics = IcsCalendarExporter.buildClassesScheduleIcs(
      [
        {
          idTurma: '1',
          frontEndIdTurma: 'FE1',
          semestre: '2026.2',
          codigoDisciplina: 'ENE0064',
          nomeDisciplina: 'REDES SEM FIO',
          numeroTurma: '01',
          cargaHorariaHoras: 60,
          docenteNome: 'JOAO GONDIM',
          local: 'FT AT-31/12',
          codigoHorarioRaw: '24M12',
          horarioSlots: SigaaScheduleDecoder.decode('24M12'),
          horarioExtenso: [],
        },
      ],
      30,
      noonBrtMillis,
    );
    expect(ics).toContain('BEGIN:VCALENDAR');
    expect(ics).toContain('TRIGGER:-PT30M');
    expect(ics).toContain('ENE0064 · REDES SEM FIO');

    // Verify RuCalculator and SigaaNotificationPlanner baseline diff
    expect(RuCalculator.parseBrlToCentavos('-R$ 2,50')).toBe(-250);
    const baseline = SigaaNotificationPlanner.seedBaseline(
      {
        turmas: [],
        feed: [],
        feedAvailable: true,
        detalhesPorTurma: {},
        notasPorTurma: {},
        frequenciaPorTurma: {},
        saldoExtratoRu: null,
        boletim: null,
        indices: {
          ira: 3.2,
          mp: 3.4,
          chObrigatoriaPendente: 420,
          chOptativaPendente: 540,
          chComplementarPendente: 0,
          chTotalCurriculo: 3750,
          percentualIntegralizadoExato: 74.0,
          percentualIntegralizadoTexto: '74% Integralizado',
        },
      },
      noonBrtMillis,
    );
    const diff = SigaaNotificationPlanner.detectSnapshotChanges(
      baseline,
      {
        turmas: [],
        feed: [],
        feedAvailable: true,
        detalhesPorTurma: {},
        notasPorTurma: {},
        frequenciaPorTurma: {},
        saldoExtratoRu: null,
        boletim: null,
        indices: {
          ira: 3.35,
          mp: 3.4,
          chObrigatoriaPendente: 420,
          chOptativaPendente: 540,
          chComplementarPendente: 0,
          chTotalCurriculo: 3750,
          percentualIntegralizadoExato: 74.0,
          percentualIntegralizadoTexto: '74% Integralizado',
        },
      },
      { ...DEFAULT_SIGAA_NOTIFICACAO_CONFIG, habilitado: true },
      noonBrtMillis + 60_000,
    );
    expect(diff.notifications).toHaveLength(1);
    expect(diff.notifications[0].tipo).toBe('INDICES_ATUALIZADOS');

    // Quick smoke check on RuMenuPdfParser empty handling + kerning-split date cells
    const emptyPdfRes = RuMenuPdfParser.parsePages({
      campus: 'DARCY_RIBEIRO',
      semanaLabel: '23/11 a 29/11',
      pdfUrl: 'https://ru.unb.br/test.pdf',
      fallbackImageUrl: null,
      localPdfPath: null,
      modifiedGmt: '',
      pages: [],
    });
    expect(emptyPdfRes.ok).toBe(false);

    const gridCols = [80, 180, 300, 420];
    const gridRows = [80, 120, 160, 200, 240, 280];
    const gridLines = [
      ...gridCols.map((x) => ({ x0: x, top: 80, x1: x, bottom: 280 })),
      ...gridRows.map((y) => ({ x0: 80, top: y, x1: 420, bottom: y })),
    ];
    const kerningSplitRes = RuMenuPdfParser.parsePages({
      campus: 'DARCY_RIBEIRO',
      semanaLabel: '23/11/2026 a 29/11/2026',
      pdfUrl: 'https://ru.unb.br/darcy.pdf',
      fallbackImageUrl: null,
      localPdfPath: null,
      modifiedGmt: '2026-11-19T11:19:39',
      pages: [
        {
          pageIndex: 0,
          pageWidth: 842,
          pageHeight: 595,
          lines: gridLines,
          images: [],
          texts: [
            { text: 'COMPOSIÇÃO', x0: 90, top: 92, x1: 160, bottom: 104 },
            { text: '2ª FEIRA', x0: 210, top: 88, x1: 260, bottom: 98 },
            { text: '(2', x0: 205, top: 102, x1: 214, bottom: 112 },
            { text: '3/11', x0: 214, top: 102, x1: 234, bottom: 112 },
            { text: '/2026)', x0: 234.1, top: 102, x1: 262, bottom: 112 },
            { text: 'Panificação', x0: 90, top: 132, x1: 150, bottom: 144 },
            { text: 'Pão Francês', x0: 200, top: 132, x1: 270, bottom: 144 },
          ],
        },
        {
          pageIndex: 1,
          pageWidth: 842,
          pageHeight: 595,
          lines: gridLines,
          images: [],
          texts: [
            { text: 'COMPOSIÇÃO', x0: 90, top: 92, x1: 160, bottom: 104 },
            { text: '2ª FEIRA', x0: 210, top: 88, x1: 260, bottom: 98 },
            { text: '(23/11/2026)', x0: 205, top: 102, x1: 262, bottom: 112 },
            { text: 'Prato Principal', x0: 90, top: 132, x1: 165, bottom: 144 },
            { text: 'Frango Grelhado', x0: 200, top: 132, x1: 280, bottom: 144 },
          ],
        },
      ],
    });
    expect(kerningSplitRes.ok).toBe(true);
    if (kerningSplitRes.ok) {
      expect(kerningSplitRes.value.dias).toHaveLength(1);
      expect(kerningSplitRes.value.dias[0].dataTexto).toBe('23/11/2026');
      expect(kerningSplitRes.value.dias[0].refeicoes.CAFE_DA_MANHA).toHaveLength(1);
      expect(kerningSplitRes.value.dias[0].refeicoes.ALMOCO).toHaveLength(1);
    }
  });
});
