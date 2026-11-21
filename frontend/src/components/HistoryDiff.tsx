import React, { useEffect, useMemo, useState } from 'react';
import {
  AlertOctagon,
  ArrowRight,
  ExternalLink,
  GitCompareArrows,
  Loader2,
  MinusCircle,
  PlusCircle,
  RefreshCw,
  TrendingUp,
} from 'lucide-react';
import { ApiError, getRunDiff } from '../api/client';
import type {
  AnalysisRunDto,
  RunDiffDto,
  SubjectSummaryDto,
} from '../types/api';
import {
  formatPln,
  formatSignedPln,
  formatUtcTimestamp,
  translateCashOutSignalType,
  translateScoreChangeType,
  translateSeverity,
} from '../utils/formatters';

interface HistoryDiffProps {
  subject: SubjectSummaryDto | null;
  activeRun: AnalysisRunDto;
  isSubmittingRun: boolean;
  onTriggerV2Run: (subjectId: string) => Promise<void>;
}

export const HistoryDiff: React.FC<HistoryDiffProps> = ({
  subject,
  activeRun,
  isSubmittingRun,
  onTriggerV2Run,
}) => {
  const orderedRuns = useMemo(() => {
    const runs = subject?.runs ?? [];
    return [...runs].sort((a, b) => a.runNumber - b.runNumber);
  }, [subject]);

  const [fromRunId, setFromRunId] = useState<string>('');
  const [toRunId, setToRunId] = useState<string>('');
  const [diff, setDiff] = useState<RunDiffDto | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  // Sync default selections whenever the subject or run count changes
  useEffect(() => {
    if (orderedRuns.length >= 2) {
      const oldest = orderedRuns[0];
      const latest = orderedRuns[orderedRuns.length - 1];
      setFromRunId((prev) =>
        orderedRuns.some((r) => r.runId === prev) ? prev : oldest.runId,
      );
      setToRunId(latest.runId);
    } else if (orderedRuns.length === 1) {
      setFromRunId(orderedRuns[0].runId);
      setToRunId(orderedRuns[0].runId);
      setDiff(null);
    }
  }, [orderedRuns]);

  const fetchDiff = async (subjectId: string, fromId: string, toId: string) => {
    if (!subjectId || !fromId || !toId || fromId === toId) {
      return;
    }
    setIsLoading(true);
    setError(null);
    try {
      const result = await getRunDiff(subjectId, fromId, toId);
      setDiff(result);
    } catch (err) {
      const msg =
        err instanceof ApiError
          ? err.message
          : 'Nie udało się pobrać porównania analiz w czasie.';
      setError(msg);
      setDiff(null);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (subject && fromRunId && toRunId && fromRunId !== toRunId) {
      void fetchDiff(subject.id, fromRunId, toRunId);
    }
  }, [subject, fromRunId, toRunId]);

  if (!subject || orderedRuns.length < 2) {
    const targetSubjectId = subject?.id ?? activeRun.subjectId;
    return (
      <div className="bg-white rounded-xl border border-slate-200 p-8 shadow-xs text-center space-y-4">
        <div className="w-12 h-12 rounded-xl bg-amber-100 text-[#B45309] flex items-center justify-center mx-auto">
          <GitCompareArrows className="w-6 h-6" />
        </div>
        <div className="space-y-1.5 max-w-xl mx-auto">
          <h3 className="text-base font-bold text-[#0F2942]">
            Monitorowanie zmian w czasie wymaga co najmniej 2 uruchomień analizy
          </h3>
          <p className="text-xs text-[#64748B] leading-relaxed">
            Obecnie sprawa <strong>{activeRun.subjectDisplayName}</strong> posiada tylko jedno uruchomienie (Analiza #{activeRun.runNumber}, v{activeRun.snapshotStep}). Uruchom analizę kontrolną (v2), aby porównać migawki i wykryć ewentualną podmianę adresu portfela na profilu Web2.
          </p>
        </div>
        <button
          type="button"
          disabled={isSubmittingRun}
          onClick={() => void onTriggerV2Run(targetSubjectId)}
          className="inline-flex items-center gap-2 rounded-lg bg-[#0F2942] px-5 py-2.5 text-xs font-bold text-white hover:bg-[#163859] disabled:opacity-50 transition-colors cursor-pointer"
        >
          {isSubmittingRun ? (
            <>
              <Loader2 className="w-4 h-4 animate-spin" />
              Uruchamianie analizy kontrolnej (v2)...
            </>
          ) : (
            <>
              <GitCompareArrows className="w-4 h-4" />
              Uruchom analizę kontrolną (v2 — wykryj podmianę portfela)
            </>
          )}
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      {/* Run Selector Bar */}
      <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-xs flex flex-wrap items-end justify-between gap-4">
        <div className="flex flex-wrap items-end gap-3 flex-1">
          <div className="min-w-[220px] flex-1">
            <label
              htmlFor="diff-from-run"
              className="block text-[11px] font-bold uppercase tracking-wide text-[#64748B] mb-1"
            >
              Analiza bazowa (starsza / od)
            </label>
            <select
              id="diff-from-run"
              value={fromRunId}
              onChange={(e) => setFromRunId(e.target.value)}
              className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-xs font-semibold text-[#0F2942] focus:border-[#0F2942] focus:outline-none"
            >
              {orderedRuns.map((r) => (
                <option key={r.runId} value={r.runId}>
                  Analiza #{r.runNumber} (v{r.snapshotStep}) — {formatUtcTimestamp(r.createdAt)}
                </option>
              ))}
            </select>
          </div>

          <div className="hidden sm:flex items-center pb-2 text-[#64748B]">
            <ArrowRight className="w-4 h-4" />
          </div>

          <div className="min-w-[220px] flex-1">
            <label
              htmlFor="diff-to-run"
              className="block text-[11px] font-bold uppercase tracking-wide text-[#64748B] mb-1"
            >
              Analiza kontrolna (nowsza / do)
            </label>
            <select
              id="diff-to-run"
              value={toRunId}
              onChange={(e) => setToRunId(e.target.value)}
              className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-xs font-semibold text-[#0F2942] focus:border-[#0F2942] focus:outline-none"
            >
              {orderedRuns.map((r) => (
                <option key={r.runId} value={r.runId}>
                  Analiza #{r.runNumber} (v{r.snapshotStep}) — {formatUtcTimestamp(r.createdAt)}
                </option>
              ))}
            </select>
          </div>
        </div>

        <button
          type="button"
          disabled={isLoading || fromRunId === toRunId}
          onClick={() => void fetchDiff(subject.id, fromRunId, toRunId)}
          className="inline-flex items-center gap-1.5 rounded-lg bg-[#0F2942] px-4 py-2 text-xs font-semibold text-white hover:bg-[#163859] disabled:opacity-50 transition-colors cursor-pointer"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />
          Porównaj wersje
        </button>
      </div>

      {fromRunId === toRunId && (
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-xs font-medium text-[#B45309]">
          Wybierz dwie różne wersje uruchomień (np. Analiza #1 oraz Analiza #2), aby zobaczyć różnice.
        </div>
      )}

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-xs font-semibold text-[#B91C1C]">
          {error}
        </div>
      )}

      {isLoading && (
        <div className="bg-white rounded-xl border border-slate-200 p-8 flex items-center justify-center gap-2 text-sm font-medium text-[#0F2942]">
          <Loader2 className="w-5 h-5 animate-spin" />
          Porównywanie zapisanych migawek dowodowych...
        </div>
      )}

      {!isLoading && diff && (
        <div className="space-y-5">
          {/* CRITICAL WALLET ROTATION RED ALERT */}
          {diff.walletRotationDetected && (
            <div className="rounded-xl border-2 border-[#B91C1C] bg-red-50 p-5 shadow-sm space-y-4">
              <div className="flex flex-wrap items-center gap-3">
                <div className="w-10 h-10 rounded-lg bg-[#B91C1C] text-white flex items-center justify-center shrink-0">
                  <AlertOctagon className="w-6 h-6" />
                </div>
                <div>
                  <div className="text-[11px] font-extrabold uppercase tracking-wider text-[#B91C1C]">
                    Alert Monitoringu OSINT • Zmiana Śladów Cyfrowych
                  </div>
                  <h3 className="text-base sm:text-lg font-extrabold text-[#B91C1C]">
                    WYKRYTO PODMIANĘ ADRESU PORTFELA NA PROFILU
                  </h3>
                </div>
              </div>

              <div className="space-y-3">
                {(diff.walletRotations ?? []).map((rot, idx) => (
                  <div
                    key={`${rot.previousAddress}-${rot.newAddress}-${idx}`}
                    className="rounded-lg border border-red-200 bg-white p-4 space-y-3"
                  >
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="flex items-center gap-2">
                        <span className="rounded bg-[#0F2942] px-2 py-0.5 text-[11px] font-bold text-white">
                          {rot.network}
                        </span>
                        <span className="text-xs font-bold text-[#0F2942]">
                          Źródło Web2: {rot.sourceName}
                        </span>
                      </div>
                      {rot.sourceUrl && (
                        <a
                          href={rot.sourceUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="inline-flex items-center gap-1 text-xs text-sky-800 underline hover:text-sky-600 break-all"
                        >
                          {rot.sourceUrl}
                          <ExternalLink className="w-3 h-3 shrink-0" />
                        </a>
                      )}
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                      <div className="rounded-lg border border-red-200 bg-red-50/50 p-3 space-y-1">
                        <div className="flex items-center justify-between text-[10px] font-bold uppercase text-[#B91C1C]">
                          <span>Poprzedni adres portfela</span>
                          <span>{formatUtcTimestamp(rot.previousSeenAt)}</span>
                        </div>
                        <code className="block font-mono text-xs font-bold text-[#B91C1C] line-through break-all">
                          {rot.previousAddress}
                        </code>
                      </div>

                      <div className="rounded-lg border border-emerald-300 bg-emerald-50/60 p-3 space-y-1">
                        <div className="flex items-center justify-between text-[10px] font-bold uppercase text-[#15803D]">
                          <span>Nowy adres portfela (po podmianie)</span>
                          <span>{formatUtcTimestamp(rot.newSeenAt)}</span>
                        </div>
                        <code className="block font-mono text-xs font-bold text-[#15803D] break-all">
                          {rot.newAddress}
                        </code>
                      </div>
                    </div>

                    <p className="text-xs font-medium text-[#334155] bg-[#F8FAFC] rounded border border-slate-200 px-3 py-2">
                      {rot.alertPl}
                    </p>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Diff Overview & Volume Delta */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
            <div className="lg:col-span-2 bg-white rounded-xl border border-slate-200 p-4 shadow-xs space-y-2">
              <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-[#64748B]">
                <span>
                  Porównanie: <strong>Analiza #{diff.fromRunNumber}</strong> ({formatUtcTimestamp(diff.fromTimestamp)}) →{' '}
                  <strong>Analiza #{diff.toRunNumber}</strong> ({formatUtcTimestamp(diff.toTimestamp)})
                </span>
              </div>
              <p className="text-sm font-medium text-[#0F2942] leading-relaxed">
                {diff.summaryPl}
              </p>
            </div>

            <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-xs flex flex-col justify-between">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold uppercase tracking-wide text-[#64748B]">
                  Przyrost / Delta wolumenu (PLN)
                </span>
                <TrendingUp className="w-4 h-4 text-[#0F2942]" />
              </div>
              <div
                className={`text-2xl font-extrabold tabular-nums mt-2 ${
                  diff.volumeDeltaPln > 0
                    ? 'text-[#B91C1C]'
                    : diff.volumeDeltaPln < 0
                      ? 'text-[#15803D]'
                      : 'text-[#0F2942]'
                }`}
              >
                {formatSignedPln(diff.volumeDeltaPln)}
              </div>
              <div className="text-[11px] text-[#64748B] mt-1">
                Różnica łącznego szacowanego obrotu między analizami
              </div>
            </div>
          </div>

          {/* Added & Removed Addresses */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-xs space-y-2.5">
              <h4 className="text-xs font-bold uppercase tracking-wide text-[#15803D] flex items-center gap-1.5">
                <PlusCircle className="w-4 h-4" />
                Nowo wykryte adresy ({diff.addedAddresses?.length ?? 0})
              </h4>
              {(diff.addedAddresses ?? []).length === 0 ? (
                <p className="text-xs text-[#64748B] italic">Brak nowo dodanych adresów.</p>
              ) : (
                <ul className="space-y-1.5">
                  {diff.addedAddresses.map((addr) => (
                    <li
                      key={addr}
                      className="rounded border border-emerald-200 bg-emerald-50/50 px-2.5 py-1.5 font-mono text-xs text-[#15803D] break-all"
                    >
                      + {addr}
                    </li>
                  ))}
                </ul>
              )}
            </div>

            <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-xs space-y-2.5">
              <h4 className="text-xs font-bold uppercase tracking-wide text-[#B91C1C] flex items-center gap-1.5">
                <MinusCircle className="w-4 h-4" />
                Usunięte / brakujące adresy ({diff.removedAddresses?.length ?? 0})
              </h4>
              {(diff.removedAddresses ?? []).length === 0 ? (
                <p className="text-xs text-[#64748B] italic">Brak usuniętych adresów.</p>
              ) : (
                <ul className="space-y-1.5">
                  {diff.removedAddresses.map((addr) => (
                    <li
                      key={addr}
                      className="rounded border border-red-200 bg-red-50/50 px-2.5 py-1.5 font-mono text-xs text-[#B91C1C] line-through break-all"
                    >
                      - {addr}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>

          {/* Score Changes Table */}
          <div className="bg-white rounded-xl border border-slate-200 shadow-xs overflow-hidden">
            <div className="px-4 py-3 bg-[#F8FAFC] border-b border-slate-200">
              <h4 className="text-xs font-bold uppercase tracking-wide text-[#0F2942]">
                Zmiany Wskaźnika Pewności (Confidence Score)
              </h4>
            </div>
            {(diff.scoreChanges ?? []).length === 0 ? (
              <div className="p-4 text-xs text-[#64748B]">Brak zarejestrowanych zmian punktacji.</div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="bg-[#F8FAFC] border-b border-slate-200 text-[11px] uppercase text-[#64748B]">
                    <tr>
                      <th className="px-3 py-2">Sieć</th>
                      <th className="px-3 py-2">Adres portfela</th>
                      <th className="px-3 py-2 text-right">Poprzedni wynik</th>
                      <th className="px-3 py-2 text-right">Nowy wynik</th>
                      <th className="px-3 py-2">Typ zmiany</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200">
                    {diff.scoreChanges.map((sc, idx) => {
                      const badge = translateScoreChangeType(sc.changeType);
                      return (
                        <tr key={`${sc.address}-${idx}`} className="bg-white">
                          <td className="px-3 py-2.5 font-bold text-[#0F2942]">{sc.network}</td>
                          <td className="px-3 py-2.5 font-mono text-[11px] text-[#0F2942] break-all">
                            {sc.address}
                          </td>
                          <td className="px-3 py-2.5 text-right font-mono tabular-nums">
                            {sc.oldScore !== null ? `${sc.oldScore}%` : '—'}
                          </td>
                          <td className="px-3 py-2.5 text-right font-mono font-bold tabular-nums">
                            {sc.newScore !== null ? `${sc.newScore}%` : '—'}
                          </td>
                          <td className="px-3 py-2.5">
                            <span
                              className={`inline-block rounded border px-2 py-0.5 text-[10px] font-bold ${badge.badgeClass}`}
                            >
                              {badge.label}
                            </span>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* New Cash-Out Signals */}
          {diff.newCashOutSignals && diff.newCashOutSignals.length > 0 && (
            <div className="bg-white rounded-xl border border-amber-200 p-4 shadow-xs space-y-3">
              <h4 className="text-xs font-bold uppercase tracking-wide text-[#B45309]">
                Nowe sygnały Fiat Cash-Out wykryte w nowszej analizie ({diff.newCashOutSignals.length})
              </h4>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                {diff.newCashOutSignals.map((sig) => {
                  const sigType = translateCashOutSignalType(sig.signalType);
                  const sev = translateSeverity(sig.severity);
                  return (
                    <div
                      key={sig.id}
                      className="rounded-lg border border-amber-200 bg-amber-50/50 p-3 space-y-1.5 text-xs"
                    >
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <span className={`rounded border px-2 py-0.5 text-[10px] font-bold ${sigType.badgeClass}`}>
                          {sigType.label}
                        </span>
                        <span className={`rounded border px-2 py-0.5 text-[10px] font-bold ${sev.badgeClass}`}>
                          {sev.label}
                        </span>
                      </div>
                      <div className="flex items-baseline justify-between font-bold text-[#0F2942]">
                        <span>{sig.counterpartyName}</span>
                        <span className="text-[#B91C1C] tabular-nums">
                          {formatPln(sig.estimatedPln)}
                        </span>
                      </div>
                      <p className="text-[#334155]">{sig.descriptionPl}</p>
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
