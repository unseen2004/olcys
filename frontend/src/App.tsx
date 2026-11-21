import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Activity,
  AlertCircle,
  Cpu,
  Database,
  FileText,
  GitCompareArrows,
  Layers,
  Loader2,
  Network,
  PanelLeftClose,
  PanelLeftOpen,
  Play,
  RefreshCw,
  ShieldAlert,
  ShieldCheck,
  Sparkles,
  TrendingUp,
  Wallet,
  X,
} from 'lucide-react';
import {
  ApiError,
  createSubjectAnalysis,
  getAnalysisRun,
  getHealth,
  listScenarios,
  listSubjects,
  triggerSubjectRun,
} from './api/client';
import { AddressCards } from './components/AddressCards';
import { BlockchainFlow } from './components/BlockchainFlow';
import { EvidenceGraph } from './components/EvidenceGraph';
import { HistoryDiff } from './components/HistoryDiff';
import { OsintReportView } from './components/OsintReportView';
import { SearchForm } from './components/SearchForm';
import type {
  AnalysisRunDto,
  CreateSubjectRequest,
  HealthStatusDto,
  ScenarioPresetDto,
  SubjectSummaryDto,
  TriggerRunRequest,
} from './types/api';
import {
  formatUtcTimestamp,
  translateIdentifierType,
  translateMode,
} from './utils/formatters';

type MainTab = 'graph' | 'wallets' | 'flow' | 'report' | 'diff';

export const App: React.FC = () => {
  const [health, setHealth] = useState<HealthStatusDto | null>(null);
  const [healthError, setHealthError] = useState<string | null>(null);
  const [scenarios, setScenarios] = useState<ScenarioPresetDto[]>([]);
  const [subjects, setSubjects] = useState<SubjectSummaryDto[]>([]);
  const [activeRun, setActiveRun] = useState<AnalysisRunDto | null>(null);
  const [activeTab, setActiveTab] = useState<MainTab>('graph');
  const [isSidebarOpen, setIsSidebarOpen] = useState<boolean>(true);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [isLoadingSubjects, setIsLoadingSubjects] = useState<boolean>(false);
  const [isLoadingRun, setIsLoadingRun] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const loadHealth = useCallback(async () => {
    try {
      const h = await getHealth();
      setHealth(h);
      setHealthError(null);
    } catch (err) {
      setHealthError(
        err instanceof ApiError
          ? err.message
          : 'Brak połączenia z endpointem /api/health.',
      );
    }
  }, []);

  const loadSubjects = useCallback(async () => {
    setIsLoadingSubjects(true);
    try {
      const data = await listSubjects();
      setSubjects(data);
    } catch {
      // Initial load may happen while backend starts; keep silent if healthError already notifies
    } finally {
      setIsLoadingSubjects(false);
    }
  }, []);

  const loadScenarios = useCallback(async () => {
    try {
      const data = await listScenarios();
      setScenarios(data);
    } catch {
      // Handled via health banner
    }
  }, []);

  useEffect(() => {
    void loadHealth();
    void loadScenarios();
    void loadSubjects();
  }, [loadHealth, loadScenarios, loadSubjects]);

  const activeSubject = useMemo<SubjectSummaryDto | null>(() => {
    if (!activeRun) return null;
    return subjects.find((s) => s.id === activeRun.subjectId) ?? null;
  }, [activeRun, subjects]);

  const handleCreateSubjectAnalysis = async (req: CreateSubjectRequest) => {
    setIsSubmitting(true);
    setErrorMessage(null);
    try {
      const run = await createSubjectAnalysis(req);
      setActiveRun(run);
      setActiveTab('graph');
      await loadSubjects();
    } catch (err) {
      setErrorMessage(
        err instanceof ApiError
          ? `${err.message}${err.detail ? ` (${err.detail})` : ''}`
          : 'Wystąpił błąd podczas uruchamiania analizy OSINT.',
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleTriggerFollowUpRun = async (subjectId: string, req: TriggerRunRequest) => {
    setIsSubmitting(true);
    setErrorMessage(null);
    try {
      const run = await triggerSubjectRun(subjectId, req);
      setActiveRun(run);
      await loadSubjects();
      // If user triggered v2 control run, jump to Diff tab if >=2 runs exist so rotation alert is immediately visible
      if (req.snapshotStep === 2) {
        setActiveTab('diff');
      }
    } catch (err) {
      setErrorMessage(
        err instanceof ApiError
          ? `${err.message}${err.detail ? ` (${err.detail})` : ''}`
          : 'Nie udało się uruchomić analizy kontrolnej.',
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleQuickTriggerV2ForActive = async (subjectId: string) => {
    const mode = activeRun?.mode ?? 'SYNTHETIC';
    const sources = activeRun?.activeSources?.length
      ? activeRun.activeSources
      : ['GitHub', 'Reddit', 'WaybackArchive', 'ENS', 'Synthetic'];
    const networks = activeRun?.activeNetworks?.length
      ? activeRun.activeNetworks
      : ['ETH', 'BTC', 'TRON'];

    await handleTriggerFollowUpRun(subjectId, {
      mode,
      sources,
      networks,
      snapshotStep: 2,
    });
  };

  const handleSelectRun = async (runId: string) => {
    setIsLoadingRun(true);
    setErrorMessage(null);
    try {
      const run = await getAnalysisRun(runId);
      setActiveRun(run);
    } catch (err) {
      setErrorMessage(
        err instanceof ApiError
          ? err.message
          : 'Nie udało się pobrać szczegółów wybranej analizy.',
      );
    } finally {
      setIsLoadingRun(false);
    }
  };

  const handleRunScenarioPreset = async (scenario: ScenarioPresetDto) => {
    await handleCreateSubjectAnalysis({
      displayName: scenario.displayName,
      identifiers: scenario.identifiers,
      mode: scenario.recommendedMode ?? 'SYNTHETIC',
      sources:
        scenario.sources?.length > 0
          ? scenario.sources
          : ['GitHub', 'Reddit', 'WaybackArchive', 'ENS', 'Synthetic'],
      networks: scenario.networks?.length > 0 ? scenario.networks : ['ETH', 'BTC', 'TRON'],
      snapshotStep: 1,
    });
  };

  const tabItems: Array<{
    id: MainTab;
    label: string;
    icon: React.ReactNode;
    badge?: string | number;
    badgeTone?: 'default' | 'green' | 'amber' | 'red';
  }> = [
    {
      id: 'graph',
      label: 'Graf powiązań',
      icon: <Network className="w-4 h-4" />,
      badge: activeRun ? activeRun.graph?.nodes?.length ?? 0 : undefined,
    },
    {
      id: 'wallets',
      label: 'Portfele & Firewall FP',
      icon: <Wallet className="w-4 h-4" />,
      badge: activeRun
        ? `${activeRun.confirmedAddresses.length} / ${activeRun.rejectedAddresses.length} FP`
        : undefined,
      badgeTone:
        activeRun && activeRun.confirmedAddresses.length > 0 ? 'green' : 'default',
    },
    {
      id: 'flow',
      label: 'Przepływy On-Chain & Cash-Out',
      icon: <TrendingUp className="w-4 h-4" />,
      badge: activeRun ? activeRun.cashOutSignals.length : undefined,
      badgeTone:
        activeRun && activeRun.cashOutSignals.length > 0 ? 'amber' : 'default',
    },
    {
      id: 'report',
      label: 'Raport OSINT (3 strefy)',
      icon: <FileText className="w-4 h-4" />,
    },
    {
      id: 'diff',
      label: 'Porównanie w czasie (Diff)',
      icon: <GitCompareArrows className="w-4 h-4" />,
      badge: activeSubject ? `${activeSubject.runs.length} wer.` : undefined,
      badgeTone:
        activeSubject && activeSubject.runs.length >= 2 ? 'red' : 'default',
    },
  ];

  return (
    <div className="min-h-screen flex flex-col bg-[#F4F6F9] text-[#0F2942]">
      {/* Institutional KAS Header */}
      <header className="bg-[#0F2942] text-white border-b border-[#091B2C] shadow-sm no-print">
        <div className="max-w-[1720px] mx-auto px-4 sm:px-6 py-3 flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={() => setIsSidebarOpen((prev) => !prev)}
              title={isSidebarOpen ? 'Ukryj panel wyszukiwania' : 'Pokaż panel wyszukiwania'}
              className="p-2 rounded-lg bg-white/10 hover:bg-white/20 text-white transition-colors cursor-pointer"
            >
              {isSidebarOpen ? (
                <PanelLeftClose className="w-4 h-4" />
              ) : (
                <PanelLeftOpen className="w-4 h-4" />
              )}
            </button>

            <div className="w-10 h-10 rounded-xl bg-white text-[#0F2942] flex items-center justify-center shadow-xs shrink-0">
              <ShieldCheck className="w-6 h-6 text-[#0F2942]" />
            </div>

            <div>
              <div className="flex flex-wrap items-center gap-2">
                <h1 className="text-lg sm:text-xl font-extrabold tracking-tight">
                  OŁCyŚ
                </h1>
                <span className="rounded bg-white/15 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider text-sky-100">
                  KAS • HackNation 2026
                </span>
              </div>
              <p className="text-xs text-slate-300">
                Osintowy Łapacz Cyfrowych Śladów — Analityka OSINT Web2 &amp; Blockchain
              </p>
            </div>
          </div>

          {/* Right Health & Privacy Telemetry */}
          <div className="flex flex-wrap items-center gap-2 text-xs">
            {/* Strict On-Premise Privacy Indicator */}
            <div
              className={`inline-flex items-center gap-1.5 rounded-lg border px-3 py-1.5 font-bold ${
                health && health.externalDataTransfer
                  ? 'border-red-400 bg-red-900/60 text-red-100'
                  : 'border-emerald-400/50 bg-[#15803D]/90 text-white'
              }`}
            >
              <ShieldCheck className="w-4 h-4 shrink-0" />
              <span>
                {health && health.externalDataTransfer
                  ? 'UWAGA: Zewnętrzny transfer AI aktywny'
                  : 'AI: Lokalny On-Premise | Zero transferu zewnętrznego'}
              </span>
            </div>

            {health ? (
              <div className="flex flex-wrap items-center gap-1.5 rounded-lg bg-white/10 border border-white/15 px-3 py-1.5 text-[11px] text-slate-100">
                <span className="inline-flex items-center gap-1 font-semibold">
                  <Cpu className="w-3.5 h-3.5 text-sky-300" />
                  Java {health.javaVersion}
                </span>
                <span className="text-white/30">|</span>
                <span className="inline-flex items-center gap-1">
                  <Activity className="w-3.5 h-3.5 text-emerald-300" />
                  Virtual Threads: {health.virtualThreadsEnabled ? 'ON' : 'OFF'}
                </span>
                <span className="text-white/30">|</span>
                <span className="inline-flex items-center gap-1">
                  <Database className="w-3.5 h-3.5 text-sky-300" />
                  DB: {health.databaseStatus}
                </span>
                <span className="text-white/30">|</span>
                <span title={`Web2: ${health.web2Connectors.join(', ')} | Web3: ${health.web3Connectors.join(', ')}`}>
                  Konektory: {health.web2Connectors.length} Web2 / {health.web3Connectors.length} Web3
                </span>
              </div>
            ) : (
              <button
                type="button"
                onClick={() => void loadHealth()}
                className="inline-flex items-center gap-1.5 rounded-lg border border-amber-400/40 bg-amber-500/20 px-3 py-1.5 text-[11px] font-semibold text-amber-100 hover:bg-amber-500/30 cursor-pointer"
              >
                <RefreshCw className="w-3.5 h-3.5" />
                {healthError ? 'Backend offline — Ponów' : 'Łączenie z backendem...'}
              </button>
            )}
          </div>
        </div>

        {/* Connector Strip */}
        {health && (
          <div className="bg-[#091B2C] border-t border-white/10">
            <div className="max-w-[1720px] mx-auto px-4 sm:px-6 py-1.5 flex flex-wrap items-center justify-between gap-2 text-[11px] text-slate-300">
              <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
                <span>
                  <strong className="text-white">Konektory Web2:</strong>{' '}
                  {health.web2Connectors.join(', ') || 'brak'}
                </span>
                <span>
                  <strong className="text-white">Konektory Web3:</strong>{' '}
                  {health.web3Connectors.join(', ') || 'brak'}
                </span>
              </div>
              <div>
                <strong className="text-white">Silnik AI:</strong> {health.aiProvider} ({health.aiModel})
              </div>
            </div>
          </div>
        )}
      </header>

      {/* Main Content Layout */}
      <div className="flex-1 max-w-[1720px] w-full mx-auto px-4 sm:px-6 py-5 flex flex-col lg:flex-row gap-5 items-start">
        {/* Left Sidebar: Search Form, Scenarios, Cases */}
        {isSidebarOpen && (
          <div className="w-full lg:w-[375px] xl:w-[400px] shrink-0 no-print">
            <SearchForm
              scenarios={scenarios}
              subjects={subjects}
              health={health}
              activeSubjectId={activeRun?.subjectId ?? null}
              activeRunId={activeRun?.runId ?? null}
              isSubmitting={isSubmitting}
              isLoadingSubjects={isLoadingSubjects}
              onSubmitNewAnalysis={handleCreateSubjectAnalysis}
              onTriggerFollowUpRun={handleTriggerFollowUpRun}
              onSelectRun={handleSelectRun}
              onRefreshSubjects={loadSubjects}
            />
          </div>
        )}

        {/* Right Analytical Workspace */}
        <main className="flex-1 min-w-0 w-full space-y-4">
          {/* Global API / Network Error Banner */}
          {errorMessage && (
            <div className="rounded-xl border border-red-300 bg-red-50 px-4 py-3 flex items-start justify-between gap-3 text-xs text-[#B91C1C] shadow-xs">
              <div className="flex items-start gap-2.5">
                <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                <div>
                  <div className="font-bold">Komunikat błędu operacyjnego</div>
                  <div className="mt-0.5">{errorMessage}</div>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setErrorMessage(null)}
                className="p-1 rounded hover:bg-red-100 cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          )}

          {/* Active Loading Overlay */}
          {(isSubmitting || isLoadingRun) && (
            <div className="rounded-xl border border-[#0F2942]/20 bg-white p-5 flex items-center gap-4 shadow-xs">
              <Loader2 className="w-6 h-6 animate-spin text-[#0F2942] shrink-0" />
              <div>
                <div className="text-sm font-bold text-[#0F2942]">
                  {isSubmitting
                    ? 'Wykonywanie równoległej analizy OSINT Web2 i Blockchain Web3...'
                    : 'Wczytywanie zapisanej analizy z bazy PostgreSQL...'}
                </div>
                <div className="text-xs text-[#64748B] mt-0.5">
                  Java 21 Virtual Threads pobierają artefakty źródłowe (SHA-256), uruchamiają Firewall FP oraz scoring KAS.
                </div>
              </div>
            </div>
          )}

          {/* Empty Welcome State when no run selected */}
          {!activeRun ? (
            <div className="space-y-5">
              <div className="bg-white rounded-xl border border-slate-200 p-6 sm:p-8 shadow-xs space-y-5">
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div className="space-y-2 max-w-2xl">
                    <span className="inline-flex items-center gap-1.5 rounded-full bg-[#0F2942]/10 px-3 py-1 text-xs font-bold text-[#0F2942]">
                      <Layers className="w-3.5 h-3.5" />
                      Stanowisko Analityczne Krajowej Administracji Skarbowej
                    </span>
                    <h2 className="text-2xl font-extrabold text-[#0F2942] tracking-tight">
                      Rozpocznij białowywiadowczą weryfikację śladów cyfrowych (OSINT + Blockchain)
                    </h2>
                    <p className="text-sm text-[#334155] leading-relaxed">
                      System <strong>OŁCyŚ</strong> automatycznie łączy pasywne źródła publiczne Web2 (GitHub, Reddit, Wayback Machine, ENS), weryfikuje kryptograficznie sumy kontrolne adresów (ETH EIP-55, BTC Base58Check/Bech32, TRON), eliminuje fałszywe trafienia giełdowe i smart kontrakty (Firewall FP), a następnie generuje dowodowy raport 3-strefowy zgodny ze standardem KAS.
                    </p>
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-3 pt-2">
                  <div className="rounded-lg border border-slate-200 bg-[#F8FAFC] p-3.5 space-y-1">
                    <div className="text-xs font-bold text-[#0F2942]">
                      1. Fakty Źródłowe (Chain-of-Custody)
                    </div>
                    <p className="text-xs text-[#64748B]">
                      Każde trafienie zawiera dokładny adres URL, znacznik czasu UTC oraz skrót SHA-256 treści.
                    </p>
                  </div>
                  <div className="rounded-lg border border-slate-200 bg-[#F8FAFC] p-3.5 space-y-1">
                    <div className="text-xs font-bold text-[#15803D]">
                      2. Firewall FP &amp; Scoring KAS
                    </div>
                    <p className="text-xs text-[#64748B]">
                      Odrzuca CEX, smart kontrakty (<code className="text-[11px]">bytecode ≠ 0x</code>) i miksery (0%), wylicza deterministyczny wynik z premią synergii +15 pp.
                    </p>
                  </div>
                  <div className="rounded-lg border border-slate-200 bg-[#F8FAFC] p-3.5 space-y-1">
                    <div className="text-xs font-bold text-[#B45309]">
                      3. Cash-Out &amp; Podmiana Portfela
                    </div>
                    <p className="text-xs text-[#64748B]">
                      Wykrywa przelewy na CASP/CEX, stablecoiny i podmianę adresu portfela między analizą v1 a kontrolną v2.
                    </p>
                  </div>
                </div>
              </div>

              {/* 3 Scenario Quick Start Cards in Main Area */}
              <div className="bg-white rounded-xl border border-slate-200 p-6 shadow-xs space-y-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <Sparkles className="w-5 h-5 text-[#B45309]" />
                    <div>
                      <h3 className="text-base font-bold text-[#0F2942]">
                        Szybki start — 3 Scenariusze Walidacyjne KAS
                      </h3>
                      <p className="text-xs text-[#64748B]">
                        Kliknij wybrany scenariusz, aby natychmiast przeprowadzić pełną analizę i zweryfikować działanie silnika OŁCyŚ.
                      </p>
                    </div>
                  </div>
                </div>

                {scenarios.length === 0 ? (
                  <div className="rounded-lg border border-dashed border-slate-300 bg-[#F8FAFC] p-6 text-center text-xs text-[#64748B]">
                    {healthError
                      ? 'Uruchom backend Spring Boot (port 8080), aby pobrać wbudowane scenariusze walidacyjne KAS.'
                      : 'Pobieranie scenariuszy walidacyjnych z /api/scenarios...'}
                  </div>
                ) : (
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    {scenarios.map((scenario, idx) => (
                      <div
                        key={scenario.id}
                        className="rounded-xl border border-slate-200 bg-[#F8FAFC] p-4 flex flex-col justify-between gap-3 hover:border-[#0F2942] transition-colors"
                      >
                        <div className="space-y-2">
                          <div className="flex items-center justify-between gap-2">
                            <span className="rounded bg-[#0F2942] px-2 py-0.5 text-[10px] font-bold uppercase text-white">
                              Scenariusz {idx + 1}
                            </span>
                            {scenario.supportsWalletRotationDemo && (
                              <span className="inline-flex items-center gap-1 rounded bg-amber-100 px-2 py-0.5 text-[10px] font-bold text-[#B45309]">
                                <GitCompareArrows className="w-3 h-3" />
                                Demo podmiany v1→v2
                              </span>
                            )}
                          </div>

                          <h4 className="text-sm font-bold text-[#0F2942] leading-snug">
                            {scenario.titlePl}
                          </h4>

                          <p className="text-xs text-[#334155] leading-relaxed">
                            {scenario.descriptionPl}
                          </p>

                          <div className="flex flex-wrap gap-1">
                            {scenario.identifiers.map((id, i) => (
                              <span
                                key={`${id.type}-${i}`}
                                className="rounded border border-slate-200 bg-white px-1.5 py-0.5 text-[10px] font-mono text-[#0F2942]"
                                title={translateIdentifierType(id.type)}
                              >
                                {id.value}
                              </span>
                            ))}
                          </div>
                        </div>

                        <div className="space-y-2.5 pt-2 border-t border-slate-200">
                          <div className="rounded bg-emerald-50 border border-emerald-200 px-2.5 py-1.5 text-[11px] text-[#15803D] font-medium">
                            <strong>Oczekiwany wynik:</strong> {scenario.expectedOutcomePl}
                          </div>

                          <button
                            type="button"
                            disabled={isSubmitting}
                            onClick={() => void handleRunScenarioPreset(scenario)}
                            className="w-full inline-flex items-center justify-center gap-2 rounded-lg bg-[#0F2942] px-3 py-2 text-xs font-bold text-white hover:bg-[#163859] disabled:opacity-50 transition-colors cursor-pointer"
                          >
                            <Play className="w-3.5 h-3.5" />
                            Uruchom scenariusz ({translateMode(scenario.recommendedMode)})
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          ) : (
            <>
              {/* Active Run Context Strip */}
              <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-xs flex flex-wrap items-center justify-between gap-4">
                <div className="space-y-1 min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="rounded-md bg-[#0F2942] px-2.5 py-0.5 text-xs font-bold text-white">
                      Analiza #{activeRun.runNumber} (v{activeRun.snapshotStep})
                    </span>
                    <h2 className="text-base sm:text-lg font-extrabold text-[#0F2942] truncate">
                      {activeRun.subjectDisplayName}
                    </h2>
                    <span className="rounded-full bg-slate-100 px-2.5 py-0.5 text-[11px] font-semibold text-[#334155]">
                      {translateMode(activeRun.mode)}
                    </span>
                  </div>

                  <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-[#64748B]">
                    <span>Data UTC: {formatUtcTimestamp(activeRun.createdAt)}</span>
                    <span>Czas wykonania: {activeRun.durationMs} ms</span>
                    <span>Dowody Web2: {activeRun.evidences.length}</span>
                    <span className="font-semibold text-[#15803D]">
                      Portfele niehostowane: {activeRun.confirmedAddresses.length}
                    </span>
                    <span className="font-semibold text-[#B91C1C]">
                      Wykluczone FP: {activeRun.rejectedAddresses.length}
                    </span>
                    {activeRun.cashOutSignals.length > 0 && (
                      <span className="inline-flex items-center gap-1 font-semibold text-[#B45309]">
                        <ShieldAlert className="w-3.5 h-3.5" />
                        Sygnały Cash-Out: {activeRun.cashOutSignals.length}
                      </span>
                    )}
                  </div>
                </div>

                {/* Direct Follow-Up v2 Control Run Button */}
                <button
                  type="button"
                  disabled={isSubmitting}
                  onClick={() => void handleQuickTriggerV2ForActive(activeRun.subjectId)}
                  className="inline-flex items-center gap-2 rounded-lg border border-[#B45309] bg-amber-50 px-3.5 py-2 text-xs font-bold text-[#B45309] hover:bg-amber-100 disabled:opacity-50 transition-colors cursor-pointer no-print"
                >
                  <GitCompareArrows className="w-4 h-4 shrink-0" />
                  Uruchom analizę kontrolną (v2 — wykryj podmianę portfela)
                </button>
              </div>

              {/* Tab Navigation */}
              <nav
                aria-label="Widoki analityczne OŁCyŚ"
                className="bg-white rounded-xl border border-slate-200 p-1.5 shadow-xs flex flex-wrap gap-1 no-print"
              >
                {tabItems.map((tab) => {
                  const isActive = activeTab === tab.id;
                  let badgeClasses = 'bg-slate-100 text-[#334155]';
                  if (isActive) {
                    badgeClasses = 'bg-white/20 text-white';
                  } else if (tab.badgeTone === 'green') {
                    badgeClasses = 'bg-emerald-100 text-[#15803D]';
                  } else if (tab.badgeTone === 'amber') {
                    badgeClasses = 'bg-amber-100 text-[#B45309]';
                  } else if (tab.badgeTone === 'red') {
                    badgeClasses = 'bg-red-100 text-[#B91C1C]';
                  }

                  return (
                    <button
                      key={tab.id}
                      type="button"
                      onClick={() => setActiveTab(tab.id)}
                      className={`inline-flex items-center gap-2 rounded-lg px-3.5 py-2 text-xs font-bold transition-all cursor-pointer ${
                        isActive
                          ? 'bg-[#0F2942] text-white shadow-xs'
                          : 'text-[#334155] hover:bg-slate-100 hover:text-[#0F2942]'
                      }`}
                    >
                      {tab.icon}
                      <span>{tab.label}</span>
                      {tab.badge !== undefined && (
                        <span className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${badgeClasses}`}>
                          {tab.badge}
                        </span>
                      )}
                    </button>
                  );
                })}
              </nav>

              {/* Active Tab Content */}
              <div>
                {activeTab === 'graph' && <EvidenceGraph run={activeRun} />}
                {activeTab === 'wallets' && <AddressCards run={activeRun} />}
                {activeTab === 'flow' && <BlockchainFlow run={activeRun} />}
                {activeTab === 'report' && <OsintReportView run={activeRun} />}
                {activeTab === 'diff' && (
                  <HistoryDiff
                    subject={activeSubject}
                    activeRun={activeRun}
                    isSubmittingRun={isSubmitting}
                    onTriggerV2Run={handleQuickTriggerV2ForActive}
                  />
                )}
              </div>
            </>
          )}
        </main>
      </div>
    </div>
  );
};

export default App;
