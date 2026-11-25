import {
  Bell,
  Check,
  ChevronDown,
  Coffee,
  ExternalLink,
  FileText,
  Newspaper,
  RefreshCw,
  SlidersHorizontal,
  Soup,
  User,
  UserCheck,
  Utensils,
} from 'lucide-react';
import React, { useMemo, useState } from 'react';
import {
  ALERGENICO_BY_ID,
  ALERGENICO_LIST,
  CAMPUS_RU_BY_ID,
  CAMPUS_RU_LIST,
  DIA_SEMANA_RU_LIST,
  GRUPO_ACESSO_RU_BY_ID,
  TIPO_REFEICAO_BY_ID,
  type Alergenico,
  type AuthState,
  type CampusRu,
  type CardapioDiaRu,
  type CardapioSemanalRu,
  type DiaSemanaRu,
  type NoticiaRu,
  type RuNotificacaoConfig,
  type TabelaPrecosRu,
  type TipoRefeicao,
} from '../../core/model/models';
import type { UiErrorBanner } from '../common/coreErrorMapper';
import { Formatters, type RuMenuDisplayRow } from '../common/formatters';
import {
  DietaryBadge,
  EditorialCard,
  EditorialHeader,
  EditorialSwitch,
  EmptyStateCard,
  ErrorBanner,
  HairlineDivider,
  HeaderActionIconButton,
  InfoCalloutRow,
  InnerTile,
  OfflineOriginStrip,
  SegmentedPillBar,
  SkeletonBox,
  SkeletonInlineBar,
  StatusTag,
  UnbPullToRefreshBox,
} from '../components/CommonComponents';
import type { ViewableDocumentRequest } from '../components/InAppDocumentViewerModal';
import { IosInstallCard } from '../components/IosInstallCard';
import { ModalDialogSheet } from '../components/ModalDialogSheet';
import type { RuUiState } from '../viewmodel/appStores';

interface RuScreenProps {
  readonly uiState: RuUiState;
  readonly authState: AuthState;
  readonly onSelectCampus: (campus: CampusRu) => void;
  readonly onSelectDay: (index: number) => void;
  readonly onSelectMeal: (meal: TipoRefeicao) => void;
  readonly onToggleAllergen: (alergenico: Alergenico) => void;
  readonly onSetHideRestricted: (hide: boolean) => void;
  readonly onClearAllergens: () => void;
  readonly onRefreshMenu: () => void;
  readonly onRefreshExtras: () => void;
  readonly onSetCampusSheetOpen: (open: boolean) => void;
  readonly onSetAllergenSheetOpen: (open: boolean) => void;
  readonly onSetNoticesSheetOpen: (open: boolean) => void;
  readonly onOpenNotifications: () => void;
  readonly onOpenAccountSheet: () => void;
  readonly onOpenLogin: () => void;
  readonly onOpenCachedDocument: (doc: ViewableDocumentRequest) => void;
  readonly onDismissMenuError: () => void;
  readonly onDismissExtrasError: () => void;
}

export const RuScreen: React.FC<RuScreenProps> = ({
  uiState,
  authState,
  onSelectCampus,
  onSelectDay,
  onSelectMeal,
  onToggleAllergen,
  onSetHideRestricted,
  onClearAllergens,
  onRefreshMenu,
  onRefreshExtras,
  onSetCampusSheetOpen,
  onSetAllergenSheetOpen,
  onSetNoticesSheetOpen,
  onOpenNotifications,
  onOpenAccountSheet,
  onOpenLogin,
  onOpenCachedDocument,
  onDismissMenuError,
  onDismissExtrasError,
}) => {
  const cardapioSynced = uiState.cardapio;
  const cardapio = cardapioSynced?.value ?? null;
  const dias = cardapio?.dias ?? [];
  const selectedDay = dias[uiState.selectedDayIndex] ?? null;
  const campusMeta = CAMPUS_RU_BY_ID[uiState.preferencias.campusSelecionado];
  const isAuthenticated = authState.kind === 'Authenticated';

  return (
    <div className="screen-root">
      <EditorialHeader
        title="Cardápio RU"
        subtitle={
          <button
            type="button"
            onClick={() => onSetCampusSheetOpen(true)}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '5px',
              minHeight: '32px',
              padding: '2px 4px',
              marginLeft: '-4px',
              borderRadius: 'var(--radius-tag)',
              color: 'var(--muted)',
              whiteSpace: 'nowrap',
            }}
          >
            <span className="ty-label">UnB</span>
            <span className="ty-label">·</span>
            <span className="ty-label">{campusMeta.displayName.toUpperCase()}</span>
            <ChevronDown size={15} strokeWidth={1.75} aria-hidden="true" />
          </button>
        }
        actions={
          <>
            <HeaderActionIconButton
              icon={RefreshCw}
              label="Atualizar cardápio"
              isLoading={uiState.isRefreshingMenu}
              onClick={onRefreshMenu}
            />
            <HeaderActionIconButton
              icon={Newspaper}
              label="Avisos e preços do RU"
              showDot={uiState.hasUnreadNoticias}
              onClick={() => onSetNoticesSheetOpen(true)}
            />
            <HeaderActionIconButton
              icon={SlidersHorizontal}
              label="Filtrar alérgenos"
              showDot={uiState.preferencias.alergenicosRestritos.length > 0}
              onClick={() => onSetAllergenSheetOpen(true)}
            />
            <HeaderActionIconButton
              icon={Bell}
              label="Notificações do RU"
              onClick={onOpenNotifications}
            />
            <HeaderActionIconButton
              icon={isAuthenticated ? UserCheck : User}
              label={isAuthenticated ? 'Conta UnB conectada' : 'Entrar na conta UnB'}
              onClick={onOpenAccountSheet}
            />
          </>
        }
      />

      {(dias.length > 0 || uiState.isMenuLoading) && (
        <div style={{ padding: '4px 16px 8px', flexShrink: 0 }}>
          {dias.length > 0 ? (
            <RuDayChipsStrip
              dias={dias}
              selectedIndex={uiState.selectedDayIndex}
              onSelectDay={onSelectDay}
            />
          ) : (
            <RuDayChipsSkeletonStrip />
          )}
        </div>
      )}

      <UnbPullToRefreshBox isRefreshing={uiState.isRefreshingMenu} onRefresh={onRefreshMenu}>
        <IosInstallCard />

        {uiState.menuError && (
          <ErrorBanner
            banner={uiState.menuError}
            onRetry={uiState.menuError.canRetry ? onRefreshMenu : null}
            onLoginClick={uiState.menuError.requiresLogin ? onOpenLogin : null}
            onDismiss={onDismissMenuError}
          />
        )}

        <RuMealSelectorRow
          selectedMeal={uiState.selectedMeal}
          onSelectMeal={onSelectMeal}
        />

        {uiState.preferencias.alergenicosRestritos.length > 0 && (
          <ActiveAllergenSummaryPill
            selected={uiState.preferencias.alergenicosRestritos}
            hiding={uiState.preferencias.ocultarPratosComAlergenicos}
            onClick={() => onSetAllergenSheetOpen(true)}
          />
        )}

        {uiState.isMenuLoading ? (
          <RuMenuSkeletonCard />
        ) : !cardapio ? (
          <EmptyStateCard
            icon={Utensils}
            title="Cardápio indisponível no momento"
            body={`Ainda não há refeições salvas para ${campusMeta.displayName}. Tente atualizar ou verificar os avisos do RU.`}
            actionLabel="Atualizar cardápio"
            onAction={onRefreshMenu}
            secondaryLabel="Ver avisos e preços"
            onSecondaryAction={() => onSetNoticesSheetOpen(true)}
          />
        ) : cardapio.parserStatus === 'FALLBACK_IMAGE_ONLY' ? (
          <FallbackImageMenuCard
            cardapio={cardapio}
            onRetry={onRefreshMenu}
            onOpenPdf={
              cardapio.localPdfPath
                ? () =>
                    onOpenCachedDocument({
                      title: `Cardápio · ${campusMeta.displayName}`,
                      fileName: `cardapio_${campusMeta.slug}.pdf`,
                      localFilePath: cardapio.localPdfPath!,
                      mimeType: 'application/pdf',
                    })
                : null
            }
          />
        ) : cardapio.parserStatus === 'FALLBACK_PDF_ONLY' && dias.length === 0 ? (
          <EmptyStateCard
            icon={FileText}
            title="Formato não estruturado no portal"
            body={`O cardápio oficial de ${campusMeta.displayName} nesta semana foi publicado em PDF sem texto extraível direto.`}
            actionLabel={cardapio.localPdfPath ? 'Abrir PDF oficial' : 'Tentar novamente'}
            onAction={() => {
              if (cardapio.localPdfPath) {
                onOpenCachedDocument({
                  title: `Cardápio · ${campusMeta.displayName}`,
                  fileName: `cardapio_${campusMeta.slug}.pdf`,
                  localFilePath: cardapio.localPdfPath,
                  mimeType: 'application/pdf',
                });
              } else {
                onRefreshMenu();
              }
            }}
            secondaryLabel="Ver avisos do RU"
            onSecondaryAction={() => onSetNoticesSheetOpen(true)}
          />
        ) : !selectedDay ? (
          <EmptyStateCard
            icon={Utensils}
            title="Dia sem cardápio"
            body={`Não encontramos refeições listadas para o dia selecionado em ${campusMeta.displayName}.`}
            actionLabel="Atualizar cardápio"
            onAction={onRefreshMenu}
          />
        ) : (
          <StructuredMenuContentCard
            day={selectedDay}
            meal={uiState.selectedMeal}
            restrictedAllergens={uiState.preferencias.alergenicosRestritos}
            hideRestricted={uiState.preferencias.ocultarPratosComAlergenicos}
            onEditAllergens={() => onSetAllergenSheetOpen(true)}
            onRefresh={onRefreshMenu}
          />
        )}

        {cardapioSynced && (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: '8px',
            }}
          >
            <OfflineOriginStrip
              origin={cardapioSynced.origin}
              syncedAtEpochMillis={cardapioSynced.syncedAtEpochMillis}
            />
            {cardapio?.localPdfPath && (
              <button
                type="button"
                className="ty-label"
                style={{ color: 'var(--muted)', whiteSpace: 'nowrap', minHeight: '32px' }}
                onClick={() =>
                  onOpenCachedDocument({
                    title: `Cardápio · ${campusMeta.displayName}`,
                    fileName: `cardapio_${campusMeta.slug}.pdf`,
                    localFilePath: cardapio.localPdfPath!,
                    mimeType: 'application/pdf',
                  })
                }
              >
                PDF oficial
              </button>
            )}
          </div>
        )}
      </UnbPullToRefreshBox>

      <CampusPickerBottomSheet
        open={uiState.isCampusSheetOpen}
        selected={uiState.preferencias.campusSelecionado}
        onSelect={onSelectCampus}
        onDismiss={() => onSetCampusSheetOpen(false)}
      />

      <AllergenFilterBottomSheet
        open={uiState.isAllergenSheetOpen}
        selected={uiState.preferencias.alergenicosRestritos}
        hideRestricted={uiState.preferencias.ocultarPratosComAlergenicos}
        onToggleAllergen={onToggleAllergen}
        onSetHideRestricted={onSetHideRestricted}
        onClearAll={onClearAllergens}
        onDismiss={() => onSetAllergenSheetOpen(false)}
      />

      <NoticesAndPricesBottomSheet
        open={uiState.isNoticesAndPricesSheetOpen}
        notices={uiState.noticias?.value ?? []}
        tabelaPrecos={uiState.tabelaPrecos?.value ?? null}
        isLoading={uiState.isRefreshingExtras}
        error={uiState.extrasError}
        onRetry={onRefreshExtras}
        onDismissError={onDismissExtrasError}
        onDismiss={() => onSetNoticesSheetOpen(false)}
      />
    </div>
  );
};

const RuDayChipsStrip: React.FC<{
  readonly dias: readonly CardapioDiaRu[];
  readonly selectedIndex: number;
  readonly onSelectDay: (index: number) => void;
}> = ({ dias, selectedIndex, onSelectDay }) => (
  <div
    role="tablist"
    aria-label="Dias do cardápio semanal"
    style={{
      display: 'grid',
      gridTemplateColumns: `repeat(${Math.min(dias.length, 7)}, minmax(0, 1fr))`,
      gap: '5px',
      width: '100%',
    }}
  >
    {dias.slice(0, 7).map((dia, index) => {
      const selected = index === selectedIndex;
      const meta = Formatters.parseDayChipMeta(index, dia);
      return (
        <button
          key={`${index}_${dia.dataTexto}`}
          type="button"
          role="tab"
          aria-selected={selected}
          aria-label={meta.fullLabel}
          onClick={() => onSelectDay(index)}
          style={{
            minHeight: '60px',
            borderRadius: 'var(--radius-inner)',
            backgroundColor: selected ? 'var(--pill-active-bg)' : 'var(--surface)',
            border: `1px solid ${selected ? 'var(--pill-active-bg)' : 'var(--border)'}`,
            color: selected ? 'var(--pill-active-ink)' : 'var(--ink)',
            padding: '10px 2px',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            gap: '4px',
          }}
        >
          <span
            className="ty-overline"
            style={{
              color: selected ? 'var(--pill-active-ink)' : 'var(--muted)',
              opacity: selected ? 0.78 : 1,
            }}
          >
            {meta.shortDow}
          </span>
          <span className="ty-title-strong ty-tabular">{meta.dayNumber}</span>
        </button>
      );
    })}
  </div>
);

const RuDayChipsSkeletonStrip: React.FC = () => (
  <div
    style={{
      display: 'grid',
      gridTemplateColumns: 'repeat(7, minmax(0, 1fr))',
      gap: '5px',
      width: '100%',
    }}
  >
    {Array.from({ length: 7 }).map((_, idx) => (
      <div
        key={idx}
        style={{
          minHeight: '60px',
          borderRadius: 'var(--radius-inner)',
          backgroundColor: 'var(--surface)',
          border: '1px solid var(--border)',
          padding: '10px 4px',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: '6px',
        }}
      >
        <SkeletonBox style={{ width: '24px', height: '10px', borderRadius: '3px' }} />
        <SkeletonBox style={{ width: '20px', height: '16px', borderRadius: '4px' }} />
      </div>
    ))}
  </div>
);

const RuMealSelectorRow: React.FC<{
  readonly selectedMeal: TipoRefeicao;
  readonly onSelectMeal: (meal: TipoRefeicao) => void;
}> = ({ selectedMeal, onSelectMeal }) => {
  const mealMeta = TIPO_REFEICAO_BY_ID[selectedMeal];
  const pills = [
    { id: 'CAFE_DA_MANHA' as const, Icon: Coffee, label: 'Café da Manhã' },
    { id: 'ALMOCO' as const, Icon: Utensils, label: 'Almoço' },
    { id: 'JANTAR' as const, Icon: Soup, label: 'Jantar' },
  ];

  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '8px' }}>
      <div style={{ flex: 1, minWidth: 0 }}>
        <h2 className="ty-title" style={{ color: 'var(--ink)' }}>
          {mealMeta.displayName}
        </h2>
        <p className="ty-label ty-tabular" style={{ color: 'var(--muted)' }}>
          {Formatters.mealScheduleWindow(selectedMeal)}
        </p>
      </div>

      <div
        role="tablist"
        aria-label="Turno de refeição"
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: '4px',
          padding: '4px',
          backgroundColor: 'var(--surface)',
          border: '1px solid var(--border)',
          borderRadius: 'var(--radius-capsule)',
        }}
      >
        {pills.map(({ id, Icon, label }) => {
          const active = selectedMeal === id;
          return (
            <button
              key={id}
              type="button"
              role="tab"
              aria-selected={active}
              aria-label={label}
              title={label}
              onClick={() => onSelectMeal(id)}
              style={{
                minWidth: '44px',
                minHeight: '40px',
                borderRadius: 'var(--radius-capsule)',
                backgroundColor: active ? 'var(--pill-active-bg)' : 'transparent',
                color: active ? 'var(--pill-active-ink)' : 'var(--muted)',
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Icon size={19} strokeWidth={1.75} />
            </button>
          );
        })}
      </div>
    </div>
  );
};

const ActiveAllergenSummaryPill: React.FC<{
  readonly selected: readonly Alergenico[];
  readonly hiding: boolean;
  readonly onClick: () => void;
}> = ({ selected, hiding, onClick }) => {
  const names = [...selected]
    .sort((a, b) => ALERGENICO_BY_ID[a].legendOrderIndex - ALERGENICO_BY_ID[b].legendOrderIndex)
    .map((a) => ALERGENICO_BY_ID[a].labelPtBr)
    .join(', ');
  const modeText = hiding ? 'ocultando incompatíveis' : 'alertando incompatíveis';

  return (
    <button
      type="button"
      onClick={onClick}
      style={{
        width: '100%',
        borderRadius: 'var(--radius-inner)',
        backgroundColor: 'var(--warning-bg)',
        border: '1px solid var(--warning-border)',
        padding: '10px 12px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: '8px',
        textAlign: 'left',
      }}
    >
      <span className="ty-label" style={{ color: 'var(--warning-ink)', flex: 1 }}>
        Restrições ({modeText}): {names}
      </span>
      <span className="ty-label" style={{ color: 'var(--warning-ink)' }}>
        Editar
      </span>
    </button>
  );
};

const EMPTY_MENU_ITEMS: readonly import('../../core/model/models').ItemCardapioRu[] = [];

const StructuredMenuContentCard: React.FC<{
  readonly day: CardapioDiaRu;
  readonly meal: TipoRefeicao;
  readonly restrictedAllergens: readonly Alergenico[];
  readonly hideRestricted: boolean;
  readonly onEditAllergens: () => void;
  readonly onRefresh: () => void;
}> = ({ day, meal, restrictedAllergens, hideRestricted, onEditAllergens, onRefresh }) => {
  const rawItems = day.refeicoes[meal] ?? EMPTY_MENU_ITEMS;
  const restrictedSet = useMemo(() => new Set(restrictedAllergens), [restrictedAllergens]);
  const { sections, hiddenCount } = useMemo(
    () => Formatters.buildRuMenuSections(rawItems, restrictedSet, hideRestricted),
    [rawItems, restrictedSet, hideRestricted],
  );

  if (rawItems.length === 0) {
    return (
      <EmptyStateCard
        icon={Utensils}
        title={`Sem itens para ${TIPO_REFEICAO_BY_ID[meal].displayName.toLowerCase()}`}
        body="Esta unidade não divulgou refeição para este turno na data selecionada."
        actionLabel="Atualizar"
        onAction={onRefresh}
      />
    );
  }

  if (sections.length === 0 && hiddenCount > 0) {
    return (
      <EmptyStateCard
        icon={SlidersHorizontal}
        title="Todos os pratos filtrados"
        body={`Os ${hiddenCount} itens deste turno contêm alérgenos da sua lista de restrição.`}
        actionLabel="Ajustar filtros de alérgenos"
        onAction={onEditAllergens}
      />
    );
  }

  return (
    <EditorialCard style={{ gap: '14px' }}>
      {sections.map((section, idx) => (
        <React.Fragment key={section.kind}>
          {idx > 0 && <HairlineDivider />}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <h3 className="ty-overline" style={{ color: 'var(--muted)' }}>
              {section.heading}
            </h3>
            {section.rows.map((row, rowIdx) => (
              <MenuItemRow
                key={`${section.kind}_${rowIdx}_${row.item.prato}`}
                row={row}
                isPrimarySection={section.kind === 'PRATOS_PRINCIPAIS'}
              />
            ))}
          </div>
        </React.Fragment>
      ))}

      {hiddenCount > 0 && (
        <>
          <HairlineDivider />
          <p className="ty-label" style={{ color: 'var(--muted)' }}>
            {hiddenCount} prato(s) oculto(s) pelos seus filtros de alérgenos.
          </p>
        </>
      )}
    </EditorialCard>
  );
};

const MenuItemRow: React.FC<{
  readonly row: RuMenuDisplayRow;
  readonly isPrimarySection: boolean;
}> = ({ row, isPrimarySection }) => {
  const isDefaultDish = row.dietaryBadge === 'PADRAO' && isPrimarySection;
  return (
    <div style={{ display: 'flex', alignItems: 'flex-start', gap: '12px', width: '100%' }}>
      {row.dietaryBadge ? (
        <DietaryBadge kind={row.dietaryBadge} />
      ) : row.rowLabel ? (
        <span
          className="ty-body"
          style={{ color: 'var(--muted)', width: '84px', flexShrink: 0 }}
        >
          {row.rowLabel}
        </span>
      ) : null}

      <div style={{ flex: 1, minWidth: 0 }}>
        <p
          className={isDefaultDish ? 'ty-body-strong' : 'ty-body'}
          style={{ color: 'var(--ink)' }}
        >
          {row.item.prato}
        </p>
        {row.restrictedMatches.length > 0 && (
          <div style={{ marginTop: '6px' }}>
            <StatusTag
              text={`Contém ${row.restrictedMatches.map((a) => ALERGENICO_BY_ID[a].labelPtBr).join(', ')}`}
              tone="WARNING"
            />
          </div>
        )}
      </div>
    </div>
  );
};

const RuMenuSkeletonCard: React.FC = () => (
  <EditorialCard style={{ gap: '14px' }}>
    <SkeletonBox style={{ width: '128px', height: '11px', borderRadius: '4px' }} />
    {[0.76, 0.58, 0.72].map((f, i) => (
      <div key={i} style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        <SkeletonBox style={{ width: '76px', height: '26px', borderRadius: '8px' }} />
        <SkeletonBox style={{ width: `${Math.round(f * 100)}%`, height: '16px', borderRadius: '6px' }} />
      </div>
    ))}
    <HairlineDivider />
    <SkeletonBox style={{ width: '164px', height: '11px', borderRadius: '4px' }} />
    {[0.82, 0.54].map((f, i) => (
      <div key={i} style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        <SkeletonBox style={{ width: '76px', height: '14px', borderRadius: '4px' }} />
        <SkeletonBox style={{ width: `${Math.round(f * 100)}%`, height: '15px', borderRadius: '5px' }} />
      </div>
    ))}
  </EditorialCard>
);

const FallbackImageMenuCard: React.FC<{
  readonly cardapio: CardapioSemanalRu;
  readonly onOpenPdf: (() => void) | null;
  readonly onRetry: () => void;
}> = ({ cardapio, onOpenPdf, onRetry }) => {
  const [imgFailed, setImgFailed] = useState(false);

  return (
    <EditorialCard style={{ gap: '12px' }}>
      <div>
        <StatusTag text="IMAGEM OFICIAL DO PORTAL" tone="INFO" />
      </div>
      <p className="ty-body" style={{ color: 'var(--muted)' }}>
        Leitura estruturada indisponível para esta semana; exibindo a arte oficial publicada pelo RU.
      </p>

      {cardapio.fallbackImageUrl && !imgFailed ? (
        <img
          src={cardapio.fallbackImageUrl}
          alt="Imagem oficial do cardápio semanal"
          onError={() => setImgFailed(true)}
          style={{ width: '100%', height: 'auto', borderRadius: 'var(--radius-inner)' }}
        />
      ) : (
        <InnerTile>
          <p className="ty-body" style={{ color: 'var(--muted)' }}>
            Não foi possível carregar a imagem oficial agora.
          </p>
        </InnerTile>
      )}

      <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
        {onOpenPdf ? (
          <button type="button" className="btn-primary" onClick={onOpenPdf}>
            <FileText size={16} strokeWidth={1.75} />
            <span>Abrir PDF no app</span>
          </button>
        ) : cardapio.fallbackImageUrl ? (
          <a
            href={cardapio.fallbackImageUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="btn-primary"
            style={{ textDecoration: 'none' }}
          >
            <ExternalLink size={16} strokeWidth={1.75} />
            <span>Abrir imagem</span>
          </a>
        ) : null}
        <button type="button" className="btn-secondary" onClick={onRetry}>
          Recarregar
        </button>
      </div>
    </EditorialCard>
  );
};

const CampusPickerBottomSheet: React.FC<{
  readonly open: boolean;
  readonly selected: CampusRu;
  readonly onSelect: (campus: CampusRu) => void;
  readonly onDismiss: () => void;
}> = ({ open, selected, onSelect, onDismiss }) => (
  <ModalDialogSheet
    open={open}
    onClose={onDismiss}
    title="Selecionar Campus"
    subtitle="Escolha a unidade do Restaurante Universitário"
  >
    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      {CAMPUS_RU_LIST.map((campus) => {
        const active = campus.id === selected;
        return (
          <InnerTile
            key={campus.id}
            onClick={() => onSelect(campus.id)}
            style={{
              backgroundColor: active ? 'var(--pill-active-bg)' : 'var(--surface-raised)',
              borderColor: active ? 'var(--pill-active-bg)' : 'transparent',
              padding: '14px 12px',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '8px' }}>
              <div>
                <p
                  className="ty-title"
                  style={{ color: active ? 'var(--pill-active-ink)' : 'var(--ink)' }}
                >
                  {campus.displayName}
                </p>
                <p
                  className="ty-label"
                  style={{
                    color: active ? 'var(--pill-active-ink)' : 'var(--muted)',
                    opacity: active ? 0.8 : 1,
                  }}
                >
                  {campus.operatingDaysSummary}
                </p>
              </div>
              {active && (
                <Check
                  size={18}
                  strokeWidth={1.75}
                  style={{ color: 'var(--pill-active-ink)' }}
                  aria-label="Selecionado"
                />
              )}
            </div>
          </InnerTile>
        );
      })}
    </div>
  </ModalDialogSheet>
);

const AllergenFilterBottomSheet: React.FC<{
  readonly open: boolean;
  readonly selected: readonly Alergenico[];
  readonly hideRestricted: boolean;
  readonly onToggleAllergen: (a: Alergenico) => void;
  readonly onSetHideRestricted: (hide: boolean) => void;
  readonly onClearAll: () => void;
  readonly onDismiss: () => void;
}> = ({
  open,
  selected,
  hideRestricted,
  onToggleAllergen,
  onSetHideRestricted,
  onClearAll,
  onDismiss,
}) => {
  const selectedSet = new Set(selected);
  return (
    <ModalDialogSheet
      open={open}
      onClose={onDismiss}
      title="Alergênicos"
      headerRight={
        (selected.length > 0 || hideRestricted) ? (
          <button
            type="button"
            className="ty-label"
            style={{ color: 'var(--muted)', padding: '6px 8px' }}
            onClick={onClearAll}
          >
            Limpar
          </button>
        ) : undefined
      }
    >
      <p className="ty-body" style={{ color: 'var(--muted)' }}>
        Marque suas restrições para destacar ou ocultar pratos que contenham esses ingredientes no
        cardápio.
      </p>

      <InnerTile style={{ padding: '10px 12px' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '12px' }}>
          <div style={{ flex: 1 }}>
            <p className="ty-title" style={{ color: 'var(--ink)' }}>
              Ocultar pratos com restrição
            </p>
            <p className="ty-label" style={{ color: 'var(--muted)' }}>
              Quando desligado, exibe alerta sem esconder o prato
            </p>
          </div>
          <EditorialSwitch
            checked={hideRestricted}
            onCheckedChange={onSetHideRestricted}
            label="Ocultar pratos com restrição"
          />
        </div>
      </InnerTile>

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
        {ALERGENICO_LIST.map((allergen) => {
          const isSelected = selectedSet.has(allergen.id);
          return (
            <button
              key={allergen.id}
              type="button"
              aria-pressed={isSelected}
              className={`filter-chip ${isSelected ? 'filter-chip--selected' : ''}`}
              onClick={() => onToggleAllergen(allergen.id)}
            >
              {isSelected && <Check size={15} strokeWidth={2} />}
              <span>{allergen.labelPtBr}</span>
            </button>
          );
        })}
      </div>
    </ModalDialogSheet>
  );
};

export const RuNotificationScheduleGrid: React.FC<{
  readonly notificacoes: RuNotificacaoConfig;
  readonly onToggleSlot: (dia: DiaSemanaRu, refeicao: TipoRefeicao) => void;
  readonly onToggleDay: (dia: DiaSemanaRu) => void;
}> = ({ notificacoes, onToggleSlot, onToggleDay }) => {
  const columns: readonly { meal: TipoRefeicao; label: string; hour: string }[] = [
    { meal: 'CAFE_DA_MANHA', label: 'CAFÉ', hour: '06h' },
    { meal: 'ALMOCO', label: 'ALMOÇO', hour: '10h' },
    { meal: 'JANTAR', label: 'JANTAR', hour: '16h' },
  ];

  return (
    <InnerTile style={{ gap: '8px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
        <span className="ty-overline" style={{ color: 'var(--muted)', width: '44px' }}>
          DIA
        </span>
        {columns.map((col) => (
          <div
            key={col.meal}
            style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center' }}
          >
            <span className="ty-overline" style={{ color: 'var(--ink)' }}>
              {col.label}
            </span>
            <span className="ty-label ty-tabular" style={{ color: 'var(--muted)' }}>
              {col.hour}
            </span>
          </div>
        ))}
      </div>

      {DIA_SEMANA_RU_LIST.map((dia) => {
        const dayMeals = notificacoes.refeicoesPorDia[dia.id] ?? [];
        const hasAnyForDay = dayMeals.length > 0;
        return (
          <div
            key={dia.id}
            style={{ display: 'flex', alignItems: 'center', gap: '6px' }}
          >
            <button
              type="button"
              onClick={() => onToggleDay(dia.id)}
              style={{
                width: '44px',
                minHeight: '36px',
                textAlign: 'left',
                padding: '0 4px',
                color:
                  hasAnyForDay && notificacoes.habilitado ? 'var(--ink)' : 'var(--muted)',
                fontWeight: hasAnyForDay ? 700 : 600,
              }}
              className="ty-label"
            >
              {dia.shortLabel}
            </button>

            {columns.map(({ meal }) => {
              const isSelected = dayMeals.includes(meal);
              const isActive = isSelected && notificacoes.habilitado;
              return (
                <button
                  key={meal}
                  type="button"
                  role="checkbox"
                  aria-checked={isSelected}
                  aria-label={`${dia.displayName}, ${TIPO_REFEICAO_BY_ID[meal].displayName}`}
                  onClick={() => onToggleSlot(dia.id, meal)}
                  style={{
                    flex: 1,
                    height: '36px',
                    borderRadius: 'var(--radius-tag)',
                    backgroundColor: isActive
                      ? 'var(--pill-active-bg)'
                      : isSelected
                        ? 'var(--surface-raised)'
                        : 'var(--surface)',
                    border: `1px solid ${isActive ? 'var(--pill-active-bg)' : 'var(--border)'}`,
                    color: isActive ? 'var(--pill-active-ink)' : 'var(--muted)',
                    display: 'inline-flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  {isSelected ? <Check size={16} strokeWidth={2} /> : <span className="ty-label">-</span>}
                </button>
              );
            })}
          </div>
        );
      })}
    </InnerTile>
  );
};

const NoticesAndPricesBottomSheet: React.FC<{
  readonly open: boolean;
  readonly notices: readonly NoticiaRu[];
  readonly tabelaPrecos: TabelaPrecosRu | null;
  readonly isLoading: boolean;
  readonly error: UiErrorBanner | null;
  readonly onRetry: () => void;
  readonly onDismissError: () => void;
  readonly onDismiss: () => void;
}> = ({
  open,
  notices,
  tabelaPrecos,
  isLoading,
  error,
  onRetry,
  onDismissError,
  onDismiss,
}) => {
  const [tabIndex, setTabIndex] = useState(0);

  return (
    <ModalDialogSheet
      open={open}
      onClose={onDismiss}
      title="Portal RU"
      headerRight={isLoading ? <SkeletonInlineBar width={24} height={7} /> : undefined}
    >
      <SegmentedPillBar
        options={['Avisos do RU', 'Tabela de Preços']}
        selectedIndex={tabIndex}
        onSelect={setTabIndex}
      />

      {error && (
        <ErrorBanner
          banner={error}
          onRetry={error.canRetry ? onRetry : null}
          onDismiss={onDismissError}
        />
      )}

      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        {tabIndex === 0 ? (
          notices.length === 0 && isLoading ? (
            [0, 1, 2].map((i) => (
              <InnerTile key={i} style={{ gap: '8px' }}>
                <SkeletonBox style={{ width: '80%', height: '16px', borderRadius: '5px' }} />
                <SkeletonBox style={{ width: '92%', height: '13px', borderRadius: '4px' }} />
              </InnerTile>
            ))
          ) : notices.length === 0 ? (
            <EmptyStateCard
              icon={Newspaper}
              title="Nenhum aviso carregado"
              body="Toque abaixo para buscar comunicados recentes de ru.unb.br."
              actionLabel="Atualizar avisos"
              onAction={onRetry}
            />
          ) : (
            notices.map((notice) => {
              const preview =
                notice.excerptText.trim() || Formatters.htmlToPlainText(notice.contentHtml);
              return (
                <InnerTile
                  key={notice.id}
                  onClick={
                    notice.link
                      ? () => window.open(notice.link, '_blank', 'noopener,noreferrer')
                      : undefined
                  }
                >
                  <p className="ty-title" style={{ color: 'var(--ink)' }}>
                    {notice.title}
                  </p>
                  {notice.dateIso && (
                    <p className="ty-label ty-tabular" style={{ color: 'var(--muted)', marginTop: '2px' }}>
                      {notice.dateIso.slice(0, 10)}
                    </p>
                  )}
                  {preview && (
                    <p
                      className="ty-body"
                      style={{
                        color: 'var(--muted)',
                        marginTop: '6px',
                        display: '-webkit-box',
                        WebkitLineClamp: 4,
                        WebkitBoxOrient: 'vertical',
                        overflow: 'hidden',
                      }}
                    >
                      {preview}
                    </p>
                  )}
                </InnerTile>
              );
            })
          )
        ) : !tabelaPrecos ? (
          <EmptyStateCard
            icon={Utensils}
            title="Tabela não carregada"
            body="Consulte os valores oficiais das refeições por grupo de acesso."
            actionLabel="Carregar tabela"
            onAction={onRetry}
          />
        ) : (
          <>
            <InfoCalloutRow text={`Referência: ${tabelaPrecos.resolucaoReferencia}`} />
            {tabelaPrecos.grupos.map((preco) => {
              const gMeta = GRUPO_ACESSO_RU_BY_ID[preco.grupo];
              return (
                <InnerTile key={preco.grupo}>
                  <p className="ty-title" style={{ color: 'var(--ink)' }}>
                    {gMeta.roman} · {gMeta.descricao}
                  </p>
                  <p className="ty-label" style={{ color: 'var(--muted)' }}>
                    {preco.publicoAlvo}
                  </p>
                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(3, minmax(0, 1fr))',
                      gap: '8px',
                      marginTop: '8px',
                    }}
                  >
                    <PriceMetric label="Café" centavos={preco.cafeCentavos} />
                    <PriceMetric label="Almoço" centavos={preco.almocoCentavos} />
                    <PriceMetric label="Jantar" centavos={preco.jantarCentavos} />
                  </div>
                  {preco.porKgCentavos !== null && (
                    <p className="ty-label ty-tabular" style={{ color: 'var(--muted)', marginTop: '6px' }}>
                      Por quilo (visitante/kg): {Formatters.formatCentavosBrl(preco.porKgCentavos)}
                    </p>
                  )}
                </InnerTile>
              );
            })}
            {tabelaPrecos.formasPagamento.length > 0 && (
              <InfoCalloutRow
                text={`Formas de pagamento: ${tabelaPrecos.formasPagamento.join(', ')}`}
              />
            )}
          </>
        )}
      </div>
    </ModalDialogSheet>
  );
};

const PriceMetric: React.FC<{
  readonly label: string;
  readonly centavos: number | null;
}> = ({ label, centavos }) => (
  <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
    <span className="ty-label" style={{ color: 'var(--muted)' }}>
      {label}
    </span>
    <span className="ty-body-strong ty-tabular" style={{ color: 'var(--ink)' }}>
      {centavos === null
        ? '-'
        : centavos === 0
          ? 'Isento'
          : Formatters.formatCentavosBrl(centavos)}
    </span>
  </div>
);
