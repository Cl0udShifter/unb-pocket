import 'fake-indexeddb/auto';
import { describe, expect, it } from 'vitest';
import type { StoredCookie } from '../../shared/network/cookieCodec';
import { BlobFileStore } from '../core/data/local/blobFileStore';
import {
  RuPreferencesStore,
  SigaaNotificationPreferencesStore,
  SnapshotCacheStore,
} from '../core/data/local/snapshotCacheStore';
import { WebCryptoCredentialVault } from '../core/data/security/webCryptoVault';
import { UnbPocketCoreFactory } from '../core/data/unbPocketCoreImpl';
import { CoreResults } from '../core/model/models';
import { UnbHttpTransport } from '../core/network/unbHttpSession';
import type { PdfExtractor } from '../core/pdf/pdfExtractorClient';
import { Formatters } from '../ui/common/formatters';
import { createAppStores } from '../ui/viewmodel/appStores';

function encodeLatin1Bytes(text: string): Uint8Array<ArrayBuffer> {
  const buf = new ArrayBuffer(text.length);
  const bytes = new Uint8Array(buf);
  for (let i = 0; i < text.length; i += 1) {
    bytes[i] = text.charCodeAt(i) & 0xff;
  }
  return bytes;
}

function encodeCookiesHeader(cookies: readonly StoredCookie[]): string {
  return Buffer.from(JSON.stringify(cookies), 'utf8').toString('base64');
}

const FE_CIC0004 = '0123456789ABCDEF0123456789ABCDEF01234567';

const SAMPLE_CAS_HTML = `<!doctype html><html><body>
  <form id="login-form" action="/sso-server/login?service=https%3A%2F%2Fsigaa.unb.br%2Fsigaa%2Fportais%2Fdiscente%2Fdiscente.jsf" method="post">
    <input type="hidden" name="lt" value="LT-123-UNB" />
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
      <tr><td>Curso:</td><td>CIÊNCIA DA COMPUTAÇÃO - BACHARELADO - PRESENCIAL - MATUTINO</td></tr>
      <tr><td>Nível:</td><td>GRADUAÇÃO</td></tr>
      <tr><td>Status:</td><td>ATIVO</td></tr>
      <tr><td>E-Mail:</td><td>ma***@aluno.unb.br</td></tr>
      <tr><td>Entrada:</td><td>2022.1</td></tr>
      <tr><td>IRA:</td><td>4.4210</td></tr>
      <tr><td>MP:</td><td>4.5000</td></tr>
      <tr><td>CH. Obrigatória Pendente:</td><td>360</td></tr>
      <tr><td>CH. Optativa Pendente:</td><td>120</td></tr>
      <tr><td>CH. Complementar Pendente:</td><td>30</td></tr>
      <tr><td colspan="2"><div style="width: 78.5%"></div>78.5% Integralizado</td></tr>
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
              <a onclick="jsfcljs(document.getElementById('form_acessarTurmaVirtual'),{'form_acessarTurmaVirtual:turmaVirtual':'form_acessarTurmaVirtual:turmaVirtual','frontEndIdTurma':'${FE_CIC0004}'},'');return false;">ALGORITMOS E PROGRAMAÇÃO DE COMPUTADORES</a>
            </form>
          </td>
          <td class="info">PJC BT 052</td>
          <td class="info">246M12</td>
        </tr>
        <tr><td id="linha_554433"></td></tr>
      </tbody>
    </table>
  </div>
  <div id="atualizacoes-turma">
    <div class="rotator">
      <table>
        <tr><td>24/11/2026 - <a onclick="jsfcljs(f,{'idTurma':'554433'},'')">CIC0004 (2026.2)</a></td></tr>
        <tr><td>Lista 3 publicada</td></tr>
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

const SAMPLE_ATESTADO_HTML = `<!doctype html><html><body>
  <table class="listagem">
    <tbody>
      <tr>
        <td>CIC0004</td>
        <td>ALGORITMOS E PROGRAMAÇÃO DE COMPUTADORES<br/>Prof. Dr. João Carlos<br/>Local: PJC BT 052</td>
        <td>01</td>
        <td>MATRICULADO</td>
        <td>246M12</td>
      </tr>
    </tbody>
  </table>
  <p>Código de Verificação: ABCD1234EFGH5678</p>
</body></html>`;

describe('UnbPocketCore + AppStores integration', () => {
  it('executes CAS login, syncPortalHome, offline cache hydration, and clean logout isolation', async () => {
    let loggedIn = false;

    const mockFetch: typeof fetch = async (_input, init) => {
      const reqBody = JSON.parse(String(init?.body ?? '{}')) as {
        url: string;
        method: string;
        formBody?: string | null;
      };
      const formBody = decodeURIComponent(reqBody.formBody ?? '');

      let finalUrl = reqBody.url;
      let html = SAMPLE_PORTAL_HOME_HTML;
      const cookies: StoredCookie[] = [
        {
          name: 'JSESSIONID',
          value: 'SIGAA_SESSION_1',
          domain: 'sigaa.unb.br',
          hostOnly: true,
          path: '/sigaa',
          secure: true,
          expiresAt: null,
        },
      ];

      if (reqBody.method === 'GET' && reqBody.url.includes('discente.jsf') && !loggedIn) {
        finalUrl = 'https://autenticacao.unb.br/sso-server/login';
        html = SAMPLE_CAS_HTML;
      } else if (reqBody.url.includes('/sso-server/login')) {
        loggedIn = true;
        finalUrl = 'https://sigaa.unb.br/sigaa/portais/discente/discente.jsf';
        html = SAMPLE_PORTAL_HOME_HTML;
      } else if (formBody.includes('formExibirExtrato')) {
        finalUrl = 'https://sigaa.unb.br/sigaa/portais/discente/extrato.jsf';
        html = SAMPLE_EXTRATO_HTML;
      } else if (formBody.includes('atestadoMatriculaUnb')) {
        finalUrl = 'https://sigaa.unb.br/sigaa/ensino/graduacao/matricula/atestado.jsf';
        html = SAMPLE_ATESTADO_HTML;
      }

      return new Response(encodeLatin1Bytes(html), {
        status: 200,
        headers: {
          'Content-Type': 'text/html;charset=ISO-8859-1',
          'X-Unb-Status': '200',
          'X-Unb-Final-Url': finalUrl,
          'X-Unb-Cookies': encodeCookiesHeader(cookies),
        },
      });
    };

    const stubPdfExtractor: PdfExtractor = {
      extractRuMenuPages: async () => CoreResults.success([]),
      extractCarteirinha: async () =>
        CoreResults.failure({
          kind: 'UnavailableForProfile',
          feature: 'Carteirinha',
          reason: 'Stub',
        }),
      extractTextLines: async () => CoreResults.success([]),
    };

    const transport = new UnbHttpTransport({ fetchImpl: mockFetch, forceProxyAll: true });
    const suffix = String(Math.random()).slice(2);
    const core = UnbPocketCoreFactory.createWithPorts({
      transport,
      cacheStore: new SnapshotCacheStore(`test_cache_${suffix}`),
      ruPrefs: new RuPreferencesStore(`test_cache_${suffix}`),
      sigaaNotifPrefs: new SigaaNotificationPreferencesStore(`test_cache_${suffix}`),
      vault: new WebCryptoCredentialVault(),
      fileStore: new BlobFileStore(`test_files_${suffix}`),
      pdfExtractor: stubPdfExtractor,
      notificationPoster: {
        postSigaa: async () => undefined,
        postRu: async () => undefined,
      },
    });

    const stores = createAppStores(core, { autoBootstrap: false, autoFetchRu: false });
    stores.authStore.onMatriculaChange('221099887');
    stores.authStore.onSenhaChange('Confirmar123!');
    await stores.authStore.submitLogin();

    expect(stores.authStore.uiState.getSnapshot().authState.kind).toBe('Authenticated');

    const discenteState = stores.discenteStore.uiState.getSnapshot();
    expect(discenteState.discenteResumo?.value.perfil.nomeCompleto).toBe('MARIA CLARA SILVA');
    expect(discenteState.discenteResumo?.value.indices.ira).toBeCloseTo(4.421, 3);
    expect(discenteState.saldoExtrato?.value.saldoAtualCentavos).toBe(2440);
    expect(Formatters.formatCentavosBrl(2440)).toBe('R$ 24,40');

    const turmasState = stores.turmasStore.listUiState.getSnapshot();
    expect(turmasState.turmas?.value.length).toBe(1);
    expect(turmasState.turmas?.value[0].codigoDisciplina).toBe('CIC0004');
    expect(turmasState.turmas?.value[0].docenteNome).toContain('João Carlos');

    await stores.discenteStore.emitirDocumento('ATESTADO_MATRICULA');
    const emitted = stores.discenteStore.uiState.getSnapshot().lastEmittedDocumentoEvent;
    expect(emitted?.codigoVerificacao).toBe('ABCD1234EFGH5678');
    const savedBlob = emitted ? await core.readCachedBlob(emitted.localFilePath) : null;
    expect(savedBlob).not.toBeNull();

    await stores.authStore.logout(true);
    expect(stores.authStore.uiState.getSnapshot().authState.kind).toBe('Unauthenticated');
    expect(await core.auth.hasSavedCredentials()).toBe(false);
    expect(stores.discenteStore.uiState.getSnapshot().discenteResumo).toBeNull();
    expect(stores.turmasStore.listUiState.getSnapshot().turmas).toBeNull();

    stores.dispose();
  });
});
