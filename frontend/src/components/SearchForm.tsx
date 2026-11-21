import React, { useEffect, useMemo, useState } from 'react';
import {
  AlertCircle,
  Bell,
  CheckCircle2,
  Clock,
  FolderOpen,
  GitCompareArrows,
  Globe,
  Loader2,
  Play,
  Plus,
  RefreshCw,
  Search,
  ShieldCheck,
  Sparkles,
  Trash2,
} from 'lucide-react';
import {
  listMonitoringSchedules,
  toggleSubjectMonitoring,
  type MonitoringStatusDto,
} from '../api/client';
import type {
  AnalysisMode,
  CreateSubjectRequest,
  HealthStatusDto,
  IdentifierDto,
  ScenarioPresetDto,
  SubjectSummaryDto,
  TriggerRunRequest,
} from '../types/api';
import {
  IDENTIFIER_OPTIONS,
  formatPln,
  formatUtcTimestamp,
  translateIdentifierType,
  translateMode,
} from '../utils/formatters';

interface SearchFormProps {
  scenarios: ScenarioPresetDto[];
  subjects: SubjectSummaryDto[];
  health: HealthStatusDto | null;
  activeSubjectId: string | null;
  activeRunId: string | null;
  isSubmitting: boolean;
  isLoadingSubjects: boolean;
  onSubmitNewAnalysis: (req: CreateSubjectRequest) => Promise<void>;
  onTriggerFollowUpRun: (subjectId: string, req: TriggerRunRequest) => Promise<void>;
  onSelectRun: (runId: string) => Promise<void>;
  onRefreshSubjects: () => Promise<void>;
}

const DEFAULT_WEB2_SOURCES: Array<{ id: string; label: string; hint: string }> = [
  { id: 'GitHub', label: 'GitHub', hint: 'Profile, bio, README, commity, wyszukiwanie po nazwisku' },
  { id: 'Reddit', label: 'Reddit', hint: 'Posty P2P, dyskusje, komentarze' },
  { id: 'WaybackArchive', label: 'WaybackArchive', hint: 'Migawki archiwalne Web Archive' },
  { id: 'ENS', label: 'ENS', hint: 'Ethereum Name Service (domeny .eth)' },
  { id: 'Keybase', label: 'Keybase', hint: 'Dowody tożsamości i przypięte adresy krypto' },
  { id: 'Synthetic', label: 'Synthetic', hint: 'Zestawy walidacyjne KAS (offline)' },
];

const DEFAULT_WEB3_NETWORKS: Array<{ id: string; label: string }> = [
  { id: 'ETH', label: 'Ethereum (ETH)' },
  { id: 'BTC', label: 'Bitcoin (BTC)' },
  { id: 'TRON', label: 'TRON (TRX)' },
];

const MODE_OPTIONS: Array<{ value: AnalysisMode; title: string; desc: string }> = [
  {
    value: 'HYBRID',
    title: 'Hybrydowy',
    desc: 'Publiczne API OSINT + zanonimizowane scenariusze KAS',
  },
  {
    value: 'LIVE',
    title: 'Live OSINT',
    desc: 'Wyłącznie pasywne zapytania do publicznych źródeł Web2/Web3',
  },
  {
    value: 'SYNTHETIC',
    title: 'Scenariusz Walidacyjny',
    desc: 'Deterministyczny tryb demonstracyjny i audytowy (<1s)',
  },
];

export const SearchForm: React.FC<SearchFormProps> = ({
  scenarios,
  subjects,
  health,
  activeSubjectId,
  activeRunId,
  isSubmitting,
  isLoadingSubjects,
  onSubmitNewAnalysis,
  onTriggerFollowUpRun,
  onSelectRun,
  onRefreshSubjects,
}) => {
  const [displayName, setDisplayName] = useState<string>('');
  const [identifiers, setIdentifiers] = useState<IdentifierDto[]>([
    { type: 'NICK', value: '' },
  ]);
  const [mode, setMode] = useState<AnalysisMode>('HYBRID');
  const [selectedSources, setSelectedSources] = useState<string[]>([
    'GitHub',
    'Reddit',
    'WaybackArchive',
    'ENS',
    'Keybase',
    'Synthetic',
  ]);
  const [selectedNetworks, setSelectedNetworks] = useState<string[]>(['ETH', 'BTC', 'TRON']);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [activeSubtab, setActiveSubtab] = useState<'new' | 'cases'>('new');
  const [monitoringMap, setMonitoringMap] = useState<Record<string, MonitoringStatusDto>>({});

  useEffect(() => {
    void listMonitoringSchedules()
      .then((items) => {
        const next: Record<string, MonitoringStatusDto> = {};
        for (const item of items) {
          next[item.subjectId] = item;
        }
        setMonitoringMap(next);
      })
      .catch(() => {
        // Ignore if backend is starting up
      });
  }, [subjects.length]);

  const handleToggleMonitoring = async (subjectId: string) => {
    const currentlyEnabled = monitoringMap[subjectId]?.enabled ?? false;
    try {
      const updated = await toggleSubjectMonitoring(subjectId, !currentlyEnabled, 24);
      setMonitoringMap((prev) => ({ ...prev, [subjectId]: updated }));
    } catch {
      // Ignore transient errors
    }
  };

  const web2SourceOptions = useMemo(() => {
    const baseIds = new Set(DEFAULT_WEB2_SOURCES.map((s) => s.id.toLowerCase()));
    const extras = (health?.web2Connectors ?? [])
      .filter((name) => !baseIds.has(name.toLowerCase()))
      .map((name) => ({ id: name, label: name, hint: 'Dodatkowy konektor Web2' }));
    return [...DEFAULT_WEB2_SOURCES, ...extras];
  }, [health]);

  const normalizeSourcesToOptions = (rawSources: string[]): string[] => {
    const byLower = new Map(web2SourceOptions.map((opt) => [opt.id.toLowerCase(), opt.id]));
    const normalized = new Set<string>();
    for (const src of rawSources) {
      const match = byLower.get(src.trim().toLowerCase());
      if (match) {
        normalized.add(match);
      } else if (byLower.has('synthetic')) {
        normalized.add(byLower.get('synthetic')!);
      }
    }
    return Array.from(normalized);
  };

  const visibleSelectedCount = useMemo(
    () => web2SourceOptions.filter((opt) => selectedSources.includes(opt.id)).length,
    [web2SourceOptions, selectedSources],
  );

  const handleAddIdentifier = () => {
    setIdentifiers((prev) => [...prev, { type: 'EMAIL', value: '' }]);
  };

  const handleRemoveIdentifier = (idx: number) => {
    setIdentifiers((prev) => (prev.length > 1 ? prev.filter((_, i) => i !== idx) : prev));
  };

  const handleIdentifierChange = (idx: number, field: 'type' | 'value', val: string) => {
    setIdentifiers((prev) =>
      prev.map((item, i) => (i === idx ? { ...item, [field]: val } : item)),
    );
  };

  const toggleSource = (sourceId: string) => {
    setSelectedSources((prev) =>
      prev.includes(sourceId) ? prev.filter((s) => s !== sourceId) : [...prev, sourceId],
    );
  };

  const toggleNetwork = (netId: string) => {
    setSelectedNetworks((prev) =>
      prev.includes(netId) ? prev.filter((n) => n !== netId) : [...prev, netId],
    );
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setValidationError(null);

    const cleanedIdentifiers = identifiers
      .map((id) => ({ type: id.type, value: id.value.trim() }))
      .filter((id) => id.value.length > 0);

    if (cleanedIdentifiers.length === 0) {
      setValidationError('Wprowadź co najmniej jeden niepusty identyfikator podmiotu.');
      return;
    }
    if (selectedSources.length === 0) {
      setValidationError('Wybierz co najmniej jedno źródło Web2.');
      return;
    }
    if (selectedNetworks.length === 0) {
      setValidationError('Wybierz co najmniej jedną sieć Web3.');
      return;
    }

    const effectiveName = displayName.trim() || `Podmiot: ${cleanedIdentifiers[0].value}`;

    await onSubmitNewAnalysis({
      displayName: effectiveName,
      identifiers: cleanedIdentifiers,
      mode,
      sources: selectedSources,
      networks: selectedNetworks,
      snapshotStep: 1,
    });
  };

  const handleLoadScenarioIntoForm = (scenario: ScenarioPresetDto) => {
    setDisplayName(scenario.displayName);
    setIdentifiers(
      scenario.identifiers.length > 0
        ? scenario.identifiers.map((id) => ({ ...id }))
        : [{ type: 'NICK', value: '' }],
    );
    setMode(scenario.recommendedMode ?? 'SYNTHETIC');
    if (scenario.sources?.length) {
      setSelectedSources(normalizeSourcesToOptions(scenario.sources));
    }
    if (scenario.networks?.length) {
      setSelectedNetworks([...scenario.networks]);
    }
    setValidationError(null);
    setActiveSubtab('new');
  };

  const handleRunScenarioImmediately = async (scenario: ScenarioPresetDto) => {
    handleLoadScenarioIntoForm(scenario);
    await onSubmitNewAnalysis({
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

  const handleTriggerV2 = async (subject: SubjectSummaryDto) => {
    const sortedRuns = [...(subject.runs ?? [])].sort((a, b) => b.runNumber - a.runNumber);
    const latestRun = sortedRuns[0];
    const runMode: AnalysisMode =
      latestRun?.mode ?? (subject.displayName.toLowerCase().includes('kowalski') ? 'SYNTHETIC' : mode);
    await onTriggerFollowUpRun(subject.id, {
      mode: runMode,
      sources: selectedSources.length > 0 ? selectedSources : ['GitHub', 'Reddit', 'WaybackArchive', 'ENS', 'Synthetic'],
      networks: selectedNetworks.length > 0 ? selectedNetworks : ['ETH', 'BTC', 'TRON'],
      snapshotStep: 2,
    });
  };

  return (
    <aside className="flex flex-col gap-4">
      {/* Sub-navigation switcher: Form vs Saved Cases */}
      <div className="bg-white border border-slate-200 rounded-xl shadow-xs overflow-hidden">
        <div className="grid grid-cols-2 border-b border-slate-200 bg-[#F8FAFC]">
          <button
            type="button"
            onClick={() => setActiveSubtab('new')}
            className={`px-3 py-2.5 text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors cursor-pointer ${
              activeSubtab === 'new'
                ? 'bg-white text-[#0F2942] border-b-2 border-[#0F2942]'
                : 'text-[#64748B] hover:text-[#0F2942]'
            }`}
          >
            <Search className="w-3.5 h-3.5" />
            Nowe zapytanie OSINT
          </button>
          <button
            type="button"
            onClick={() => setActiveSubtab('cases')}
            className={`px-3 py-2.5 text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors cursor-pointer ${
              activeSubtab === 'cases'
                ? 'bg-white text-[#0F2942] border-b-2 border-[#0F2942]'
                : 'text-[#64748B] hover:text-[#0F2942]'
            }`}
          >
            <FolderOpen className="w-3.5 h-3.5" />
            Zapisane sprawy ({subjects.length})
          </button>
        </div>

        {activeSubtab === 'new' ? (
          <form onSubmit={handleSubmit} className="p-4 space-y-4">
            <div>
              <label
                htmlFor="subject-display-name"
                className="block text-xs font-semibold uppercase tracking-wide text-[#334155] mb-1"
              >
                Oznaczenie sprawy / Podmiot
              </label>
              <input
                id="subject-display-name"
                type="text"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                placeholder="np. Sprawa KAS-2026/01 — Jan Kowalski"
                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-[#0F2942] placeholder:text-slate-400 focus:border-[#0F2942] focus:outline-none focus:ring-2 focus:ring-[#0F2942]/15"
              />
            </div>

            {/* Dynamic identifiers */}
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold uppercase tracking-wide text-[#334155]">
                  Identyfikatory wejściowe ({identifiers.length})
                </span>
                <button
                  type="button"
                  onClick={handleAddIdentifier}
                  className="inline-flex items-center gap-1 text-xs font-semibold text-[#0F2942] hover:text-[#15803D] transition-colors cursor-pointer"
                >
                  <Plus className="w-3.5 h-3.5" />
                  Dodaj identyfikator
                </button>
              </div>

              <div className="space-y-2">
                {identifiers.map((ident, idx) => {
                  const meta = IDENTIFIER_OPTIONS.find((opt) => opt.value === ident.type);
                  return (
                    <div
                      key={idx}
                      className="flex flex-col gap-1.5 rounded-lg border border-slate-200 bg-[#F8FAFC] p-2"
                    >
                      <div className="flex items-center gap-1.5">
                        <select
                          aria-label={`Typ identyfikatora ${idx + 1}`}
                          value={ident.type}
                          onChange={(e) => handleIdentifierChange(idx, 'type', e.target.value)}
                          className="flex-1 rounded-md border border-slate-300 bg-white px-2 py-1.5 text-xs font-medium text-[#0F2942] focus:border-[#0F2942] focus:outline-none"
                        >
                          {IDENTIFIER_OPTIONS.map((opt) => (
                            <option key={opt.value} value={opt.value}>
                              {opt.label}
                            </option>
                          ))}
                        </select>
                        {identifiers.length > 1 && (
                          <button
                            type="button"
                            onClick={() => handleRemoveIdentifier(idx)}
                            title="Usuń identyfikator"
                            className="p-1.5 rounded-md text-slate-400 hover:text-[#B91C1C] hover:bg-red-50 transition-colors cursor-pointer"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        )}
                      </div>
                      <input
                        type="text"
                        value={ident.value}
                        onChange={(e) => handleIdentifierChange(idx, 'value', e.target.value)}
                        placeholder={meta?.placeholder ?? 'Wartość identyfikatora'}
                        className="w-full rounded-md border border-slate-300 bg-white px-2.5 py-1.5 text-sm text-[#0F2942] placeholder:text-slate-400 focus:border-[#0F2942] focus:outline-none"
                      />
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Analysis mode */}
            <div className="space-y-1.5">
              <span className="block text-xs font-semibold uppercase tracking-wide text-[#334155]">
                Tryb pozyskiwania danych
              </span>
              <div className="grid grid-cols-1 gap-1.5">
                {MODE_OPTIONS.map((opt) => {
                  const selected = mode === opt.value;
                  return (
                    <label
                      key={opt.value}
                      className={`flex items-start gap-2.5 rounded-lg border px-2.5 py-2 cursor-pointer transition-all ${
                        selected
                          ? 'border-[#0F2942] bg-[#0F2942]/5 ring-1 ring-[#0F2942]/20'
                          : 'border-slate-200 bg-white hover:border-slate-300'
                      }`}
                    >
                      <input
                        type="radio"
                        name="analysis-mode"
                        value={opt.value}
                        checked={selected}
                        onChange={() => setMode(opt.value)}
                        className="mt-0.5 accent-[#0F2942]"
                      />
                      <div className="leading-tight">
                        <div className="text-xs font-semibold text-[#0F2942]">{opt.title}</div>
                        <div className="text-[11px] text-[#64748B] mt-0.5">{opt.desc}</div>
                      </div>
                    </label>
                  );
                })}
              </div>
            </div>

            {/* Web2 sources */}
            <div className="space-y-1.5">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold uppercase tracking-wide text-[#334155]">
                  Źródła Web2 (OSINT pasywny)
                </span>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() =>
                      setSelectedSources(
                        visibleSelectedCount === web2SourceOptions.length
                          ? []
                          : web2SourceOptions.map((o) => o.id),
                      )
                    }
                    className="text-[11px] font-medium text-[#0F2942] hover:underline cursor-pointer"
                  >
                    {visibleSelectedCount === web2SourceOptions.length ? 'Odznacz' : 'Wszystkie'}
                  </button>
                  <span className="text-[11px] text-[#64748B]">
                    {visibleSelectedCount}/{web2SourceOptions.length}
                  </span>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-1.5">
                {web2SourceOptions.map((src) => {
                  const checked = selectedSources.includes(src.id);
                  return (
                    <label
                      key={src.id}
                      title={src.hint}
                      className={`flex items-center gap-2 rounded-md border px-2 py-1.5 text-xs cursor-pointer transition-colors ${
                        checked
                          ? 'border-[#0F2942]/40 bg-slate-50 text-[#0F2942] font-medium'
                          : 'border-slate-200 bg-white text-[#64748B]'
                      }`}
                    >
                      <input
                        type="checkbox"
                        checked={checked}
                        onChange={() => toggleSource(src.id)}
                        className="accent-[#0F2942]"
                      />
                      <span className="truncate">{src.label}</span>
                    </label>
                  );
                })}
              </div>
            </div>

            {/* Web3 networks */}
            <div className="space-y-1.5">
              <span className="block text-xs font-semibold uppercase tracking-wide text-[#334155]">
                Sieci Blockchain (Web3)
              </span>
              <div className="grid grid-cols-3 gap-1.5">
                {DEFAULT_WEB3_NETWORKS.map((net) => {
                  const checked = selectedNetworks.includes(net.id);
                  return (
                    <label
                      key={net.id}
                      className={`flex items-center justify-center gap-1.5 rounded-md border px-2 py-1.5 text-xs cursor-pointer transition-colors ${
                        checked
                          ? 'border-[#0F2942] bg-[#0F2942] text-white font-semibold'
                          : 'border-slate-200 bg-white text-[#334155] hover:border-slate-300'
                      }`}
                    >
                      <input
                        type="checkbox"
                        checked={checked}
                        onChange={() => toggleNetwork(net.id)}
                        className="sr-only"
                      />
                      <span>{net.id}</span>
                    </label>
                  );
                })}
              </div>
            </div>

            {validationError && (
              <div className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-xs text-[#B91C1C]">
                <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                <span>{validationError}</span>
              </div>
            )}

            <button
              type="submit"
              disabled={isSubmitting}
              className="w-full inline-flex items-center justify-center gap-2 rounded-lg bg-[#0F2942] px-4 py-2.5 text-sm font-semibold text-white shadow-xs hover:bg-[#163859] disabled:opacity-60 disabled:cursor-not-allowed transition-colors cursor-pointer"
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Trwa analiza równoległa KAS...
                </>
              ) : (
                <>
                  <Search className="w-4 h-4" />
                  Uruchom analizę OSINT &amp; Web3
                </>
              )}
            </button>
          </form>
        ) : (
          <div className="p-3 space-y-3 max-h-[560px] overflow-y-auto">
            <div className="flex items-center justify-between px-1">
              <span className="text-xs font-semibold uppercase tracking-wide text-[#334155]">
                Rejestr spraw w bazie PostgreSQL
              </span>
              <button
                type="button"
                onClick={() => void onRefreshSubjects()}
                disabled={isLoadingSubjects}
                className="inline-flex items-center gap-1 text-xs font-medium text-[#0F2942] hover:underline cursor-pointer disabled:opacity-50"
              >
                <RefreshCw className={`w-3.5 h-3.5 ${isLoadingSubjects ? 'animate-spin' : ''}`} />
                Odśwież
              </button>
            </div>

            {subjects.length === 0 ? (
              <div className="rounded-lg border border-dashed border-slate-300 bg-[#F8FAFC] p-4 text-center text-xs text-[#64748B]">
                Brak zapisanych spraw. Uruchom jeden z 3 gotowych scenariuszy KAS poniżej lub utwórz nowe zapytanie.
              </div>
            ) : (
              subjects.map((subject) => {
                const isSelectedSubject = subject.id === activeSubjectId;
                return (
                  <div
                    key={subject.id}
                    className={`rounded-lg border p-3 space-y-2.5 transition-all ${
                      isSelectedSubject
                        ? 'border-[#0F2942] bg-[#0F2942]/[0.03] shadow-xs'
                        : 'border-slate-200 bg-white'
                    }`}
                  >
                    <div>
                      <div className="flex items-start justify-between gap-2">
                        <h4 className="text-xs font-bold text-[#0F2942] leading-snug">
                          {subject.displayName}
                        </h4>
                        <span className="shrink-0 rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-semibold text-[#334155]">
                          {subject.runs?.length ?? 0} analiz
                        </span>
                      </div>
                      <div className="text-[11px] text-[#64748B] mt-0.5 flex items-center gap-1">
                        <Clock className="w-3 h-3" />
                        {formatUtcTimestamp(subject.createdAt)}
                      </div>
                      <div className="flex flex-wrap gap-1 mt-1.5">
                        {subject.identifiers.map((id, i) => (
                          <span
                            key={`${id.type}-${i}`}
                            className="inline-flex items-center gap-1 rounded border border-slate-200 bg-[#F8FAFC] px-1.5 py-0.5 text-[10px] text-[#334155]"
                            title={translateIdentifierType(id.type)}
                          >
                            <span className="font-semibold text-[#0F2942]">{id.type}:</span>
                            <span className="truncate max-w-[140px]">{id.value}</span>
                          </span>
                        ))}
                      </div>
                    </div>

                    {/* Runs history */}
                    {subject.runs && subject.runs.length > 0 && (
                      <div className="space-y-1">
                        {[...subject.runs]
                          .sort((a, b) => a.runNumber - b.runNumber)
                          .map((run) => {
                          const isRunActive = run.runId === activeRunId;
                          return (
                            <button
                              key={run.runId}
                              type="button"
                              onClick={() => void onSelectRun(run.runId)}
                              className={`w-full text-left rounded-md border px-2.5 py-1.5 text-xs transition-colors cursor-pointer ${
                                isRunActive
                                  ? 'border-[#0F2942] bg-[#0F2942] text-white'
                                  : 'border-slate-200 bg-[#F8FAFC] text-[#334155] hover:border-slate-300 hover:bg-slate-100'
                              }`}
                            >
                              <div className="flex items-center justify-between font-semibold">
                                <span>
                                  Analiza #{run.runNumber} (v{run.snapshotStep})
                                </span>
                                <span
                                  className={`text-[10px] px-1.5 py-0.5 rounded ${
                                    isRunActive
                                      ? 'bg-white/20 text-white'
                                      : 'bg-white text-[#0F2942] border border-slate-200'
                                  }`}
                                >
                                  {run.maxConfidenceScore}% max
                                </span>
                              </div>
                              <div
                                className={`flex items-center justify-between text-[11px] mt-0.5 ${
                                  isRunActive ? 'text-slate-200' : 'text-[#64748B]'
                                }`}
                              >
                                <span>
                                  Portfele: {run.validatedAddressCount} | FP: {run.rejectedFpCount}
                                </span>
                                <span className="tabular-nums font-medium">
                                  {formatPln(run.totalVolumePln)}
                                </span>
                              </div>
                            </button>
                          );
                        })}
                      </div>
                    )}

                    {/* V2 Follow-up wallet rotation detection + Cyclic CRON toggle */}
                    <div className="space-y-1.5">
                      <button
                        type="button"
                        disabled={isSubmitting}
                        onClick={() => void handleTriggerV2(subject)}
                        className="w-full inline-flex items-center justify-center gap-1.5 rounded-md border border-[#B45309]/40 bg-amber-50 px-2.5 py-2 text-xs font-semibold text-[#B45309] hover:bg-amber-100 transition-colors cursor-pointer disabled:opacity-50"
                      >
                        <GitCompareArrows className="w-3.5 h-3.5 shrink-0" />
                        <span>Uruchom analizę kontrolną (v2 — wykryj podmianę portfela)</span>
                      </button>

                      <button
                        type="button"
                        onClick={() => void handleToggleMonitoring(subject.id)}
                        className={`w-full inline-flex items-center justify-between rounded-md border px-2.5 py-1.5 text-[11px] font-semibold transition-colors cursor-pointer ${
                          monitoringMap[subject.id]?.enabled
                            ? 'border-emerald-300 bg-emerald-50 text-[#15803D]'
                            : 'border-slate-200 bg-[#F8FAFC] text-[#64748B] hover:border-slate-300'
                        }`}
                      >
                        <span className="inline-flex items-center gap-1.5">
                          <Bell className="w-3.5 h-3.5 shrink-0" />
                          Monitoring cykliczny 24h (CRON)
                        </span>
                        <span>{monitoringMap[subject.id]?.enabled ? 'AKTYWNY' : 'WYŁ.'}</span>
                      </button>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        )}
      </div>

      {/* Quick Start Validation Scenarios Card Box */}
      <div className="bg-white border border-slate-200 rounded-xl shadow-xs p-4 space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Sparkles className="w-4 h-4 text-[#B45309]" />
            <h3 className="text-xs font-bold uppercase tracking-wide text-[#0F2942]">
              Scenariusze walidacyjne KAS (Szybki start)
            </h3>
          </div>
          <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-semibold text-[#334155]">
            {scenarios.length} gotowe
          </span>
        </div>

        {scenarios.length === 0 ? (
          <p className="text-xs text-[#64748B]">
            Ładowanie wbudowanych scenariuszy walidacyjnych z backendu (<code className="text-[11px]">GET /api/scenarios</code>)...
          </p>
        ) : (
          <div className="space-y-2.5">
            {scenarios.map((scenario, idx) => (
              <div
                key={scenario.id}
                className="rounded-lg border border-slate-200 bg-[#F8FAFC] p-3 space-y-2 hover:border-[#0F2942]/40 transition-colors"
              >
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <div className="text-[10px] font-bold uppercase tracking-wider text-[#64748B]">
                      Scenariusz {idx + 1} • {translateMode(scenario.recommendedMode)}
                    </div>
                    <h4 className="text-xs font-bold text-[#0F2942] mt-0.5">
                      {scenario.titlePl}
                    </h4>
                  </div>
                  {scenario.supportsWalletRotationDemo && (
                    <span className="shrink-0 inline-flex items-center gap-1 rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-semibold text-[#B45309]">
                      <GitCompareArrows className="w-3 h-3" />
                      Demo v1→v2
                    </span>
                  )}
                </div>

                <p className="text-[11px] text-[#334155] leading-relaxed">
                  {scenario.subtitlePl || scenario.descriptionPl}
                </p>

                <div className="flex items-start gap-1.5 rounded bg-white border border-slate-200 px-2 py-1.5 text-[11px] text-[#15803D]">
                  <ShieldCheck className="w-3.5 h-3.5 shrink-0 mt-0.5" />
                  <span>{scenario.expectedOutcomePl}</span>
                </div>

                <div className="flex items-center gap-2 pt-0.5">
                  <button
                    type="button"
                    disabled={isSubmitting}
                    onClick={() => void handleRunScenarioImmediately(scenario)}
                    className="flex-1 inline-flex items-center justify-center gap-1.5 rounded-md bg-[#0F2942] px-2.5 py-1.5 text-xs font-semibold text-white hover:bg-[#163859] disabled:opacity-50 transition-colors cursor-pointer"
                  >
                    <Play className="w-3 h-3" />
                    Uruchom
                  </button>
                  <button
                    type="button"
                    disabled={isSubmitting}
                    onClick={() => handleLoadScenarioIntoForm(scenario)}
                    className="inline-flex items-center justify-center gap-1 rounded-md border border-slate-300 bg-white px-2.5 py-1.5 text-xs font-medium text-[#334155] hover:bg-slate-50 transition-colors cursor-pointer"
                  >
                    <Globe className="w-3 h-3" />
                    Edytuj
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}

        {activeSubjectId && (
          <div className="pt-1 border-t border-slate-200">
            <div className="flex items-center gap-1.5 text-[11px] text-[#15803D] font-medium">
              <CheckCircle2 className="w-3.5 h-3.5 shrink-0" />
              <span>Aktywna sprawa posiada przypisaną historię uruchomień w zakładce „Zapisane sprawy”.</span>
            </div>
          </div>
        )}
      </div>
    </aside>
  );
};
