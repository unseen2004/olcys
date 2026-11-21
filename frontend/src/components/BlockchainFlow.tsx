import React, { useMemo, useState } from 'react';
import {
  Activity,
  AlertTriangle,
  ArrowDownLeft,
  ArrowUpRight,
  Banknote,
  Check,
  Coins,
  Copy,
  ExternalLink,
  Filter,
  TrendingUp,
} from 'lucide-react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { AnalysisRunDto, TimelineEventDto } from '../types/api';
import {
  copyToClipboard,
  formatNumber,
  formatPln,
  formatUsd,
  formatUtcDateShort,
  formatUtcTimestamp,
  getExplorerUrl,
  shortenAddress,
  shortenHash,
  translateCashOutSignalType,
  translateDirection,
  translateSeverity,
} from '../utils/formatters';

interface BlockchainFlowProps {
  run: AnalysisRunDto;
}

interface DailyFlowPoint {
  date: string;
  inflowPln: number;
  outflowPln: number;
  txCount: number;
}

interface CustomTooltipPayloadItem {
  name?: string;
  value?: number;
  dataKey?: string;
  color?: string;
  payload?: DailyFlowPoint;
}

interface CustomTooltipProps {
  active?: boolean;
  label?: string;
  payload?: CustomTooltipPayloadItem[];
}

const CustomChartTooltip: React.FC<CustomTooltipProps> = ({ active, label, payload }) => {
  if (!active || !payload || payload.length === 0) return null;
  const point = payload[0]?.payload;
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-3 shadow-md text-xs space-y-1.5">
      <div className="font-bold text-[#0F2942] border-b border-slate-100 pb-1">
        Data: {label} ({point?.txCount ?? 0} transakcji)
      </div>
      <div className="flex items-center justify-between gap-4 text-[#15803D] font-semibold">
        <span>Przychodzące:</span>
        <span className="tabular-nums">{formatPln(point?.inflowPln ?? 0)}</span>
      </div>
      <div className="flex items-center justify-between gap-4 text-[#B91C1C] font-semibold">
        <span>Wychodzące:</span>
        <span className="tabular-nums">{formatPln(point?.outflowPln ?? 0)}</span>
      </div>
    </div>
  );
};

export const BlockchainFlow: React.FC<BlockchainFlowProps> = ({ run }) => {
  const [signalFilter, setSignalFilter] = useState<string>('ALL');
  const [copiedHash, setCopiedHash] = useState<string | null>(null);

  const allTimelineEvents = useMemo<TimelineEventDto[]>(() => {
    const reportFlow = run.report?.zone2Computations?.chronologicalFlow ?? [];
    if (reportFlow.length > 0) {
      return [...reportFlow].sort(
        (a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime(),
      );
    }

    const seen = new Set<string>();
    const collected: TimelineEventDto[] = [];
    for (const finding of run.confirmedAddresses ?? []) {
      for (const ev of finding.onChainMetrics?.timeline ?? []) {
        const key = `${ev.txHash}-${ev.direction}-${ev.asset}-${ev.amount}`;
        if (!seen.has(key)) {
          seen.add(key);
          collected.push(ev);
        }
      }
    }
    return collected.sort(
      (a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime(),
    );
  }, [run]);

  const kpis = useMemo(() => {
    const zone2 = run.report?.zone2Computations;
    const confirmed = run.confirmedAddresses ?? [];

    const totalVolumePln =
      zone2 && zone2.totalEstimatedVolumePln > 0
        ? zone2.totalEstimatedVolumePln
        : confirmed.reduce(
            (sum, f) => sum + (f.onChainMetrics?.estimatedTotalVolumePln ?? 0),
            0,
          );

    const stablecoinUsd =
      zone2 && zone2.totalStablecoinVolumeUsd > 0
        ? zone2.totalStablecoinVolumeUsd
        : confirmed.reduce(
            (sum, f) => sum + (f.onChainMetrics?.stablecoinVolumeUsd ?? 0),
            0,
          );

    const txCount =
      zone2 && zone2.totalTransactionsAnalyzed > 0
        ? zone2.totalTransactionsAnalyzed
        : confirmed.reduce((sum, f) => sum + (f.onChainMetrics?.txCount ?? 0), 0) ||
          allTimelineEvents.length;

    const cashOutCount = run.cashOutSignals?.length ?? 0;

    return {
      totalVolumePln,
      stablecoinUsd,
      txCount,
      cashOutCount,
    };
  }, [run, allTimelineEvents]);

  const chartData = useMemo<DailyFlowPoint[]>(() => {
    const map = new Map<string, DailyFlowPoint>();
    for (const ev of allTimelineEvents) {
      const dateKey = formatUtcDateShort(ev.timestamp);
      const current = map.get(dateKey) ?? {
        date: dateKey,
        inflowPln: 0,
        outflowPln: 0,
        txCount: 0,
      };
      const { isOutflow } = translateDirection(ev.direction);
      if (isOutflow) {
        current.outflowPln += ev.estimatedPln || 0;
      } else {
        current.inflowPln += ev.estimatedPln || 0;
      }
      current.txCount += 1;
      map.set(dateKey, current);
    }
    return Array.from(map.values()).sort((a, b) => a.date.localeCompare(b.date));
  }, [allTimelineEvents]);

  const availableSignalTypes = useMemo(() => {
    const types = new Set<string>();
    for (const ev of allTimelineEvents) {
      if (ev.cashOutSignalType) types.add(ev.cashOutSignalType);
    }
    return Array.from(types);
  }, [allTimelineEvents]);

  const filteredEvents = useMemo(() => {
    if (signalFilter === 'ALL') return allTimelineEvents;
    if (signalFilter === 'ONLY_SIGNALS') {
      return allTimelineEvents.filter((ev) => Boolean(ev.cashOutSignalType));
    }
    return allTimelineEvents.filter((ev) => ev.cashOutSignalType === signalFilter);
  }, [allTimelineEvents, signalFilter]);

  const handleCopy = async (text: string) => {
    const ok = await copyToClipboard(text);
    if (ok) {
      setCopiedHash(text);
      setTimeout(() => setCopiedHash(null), 1800);
    }
  };

  return (
    <div className="space-y-6">
      {/* 4 KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wide text-[#64748B]">
              Łączny wolumen (PLN)
            </span>
            <div className="w-8 h-8 rounded-lg bg-[#0F2942]/10 text-[#0F2942] flex items-center justify-center">
              <Banknote className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl font-extrabold text-[#0F2942] tabular-nums mt-2">
            {formatPln(kpis.totalVolumePln)}
          </div>
          <div className="text-[11px] text-[#64748B] mt-1">
            Szacowana łączna wartość obrotów zatwierdzonych portfeli
          </div>
        </div>

        <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wide text-[#64748B]">
              Wolumen Stablecoin (USD)
            </span>
            <div className="w-8 h-8 rounded-lg bg-emerald-100 text-[#15803D] flex items-center justify-center">
              <Coins className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl font-extrabold text-[#15803D] tabular-nums mt-2">
            {formatUsd(kpis.stablecoinUsd)}
          </div>
          <div className="text-[11px] text-[#64748B] mt-1">
            Transfery i swapy USDT / USDC / DAI
          </div>
        </div>

        <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wide text-[#64748B]">
              Przeanalizowane transakcje
            </span>
            <div className="w-8 h-8 rounded-lg bg-sky-100 text-sky-800 flex items-center justify-center">
              <Activity className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl font-extrabold text-[#0F2942] tabular-nums mt-2">
            {formatNumber(kpis.txCount)}
          </div>
          <div className="text-[11px] text-[#64748B] mt-1">
            Zdarzenia na osi chronologicznej: {allTimelineEvents.length}
          </div>
        </div>

        <div
          className={`rounded-xl border p-4 shadow-xs ${
            kpis.cashOutCount > 0
              ? 'bg-amber-50/70 border-amber-300'
              : 'bg-white border-slate-200'
          }`}
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wide text-[#64748B]">
              Sygnały Fiat Cash-Out
            </span>
            <div
              className={`w-8 h-8 rounded-lg flex items-center justify-center ${
                kpis.cashOutCount > 0
                  ? 'bg-red-100 text-[#B91C1C]'
                  : 'bg-slate-100 text-[#64748B]'
              }`}
            >
              <AlertTriangle className="w-4 h-4" />
            </div>
          </div>
          <div
            className={`text-2xl font-extrabold tabular-nums mt-2 ${
              kpis.cashOutCount > 0 ? 'text-[#B91C1C]' : 'text-[#0F2942]'
            }`}
          >
            {kpis.cashOutCount}
          </div>
          <div className="text-[11px] text-[#64748B] mt-1">
            CEX Off-Ramp, P2P, bramki krypto-fiat, cykliczność
          </div>
        </div>
      </div>

      {/* Recharts BarChart */}
      <div className="bg-white rounded-xl border border-slate-200 p-4 sm:p-5 shadow-xs space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div>
            <h3 className="text-sm font-bold text-[#0F2942] flex items-center gap-2">
              <TrendingUp className="w-4 h-4 text-[#0F2942]" />
              Przepływy środków w czasie (Przychodzące vs Wychodzące w PLN)
            </h3>
            <p className="text-xs text-[#64748B]">
              Agregacja dzienna UTC dla zatwierdzonych portfeli niehostowanych podmiotu.
            </p>
          </div>
        </div>

        {chartData.length === 0 ? (
          <div className="h-64 flex items-center justify-center rounded-lg border border-dashed border-slate-200 bg-[#F8FAFC] text-xs text-[#64748B]">
            Brak danych transakcyjnych on-chain do wyrysowania wykresu przepływów.
          </div>
        ) : (
          <div className="h-80 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart
                data={chartData}
                margin={{ top: 12, right: 20, left: 10, bottom: 8 }}
              >
                <CartesianGrid strokeDasharray="3 3" stroke="#E2E8F0" />
                <XAxis
                  dataKey="date"
                  tick={{ fill: '#334155', fontSize: 11 }}
                  axisLine={{ stroke: '#CBD5E1' }}
                />
                <YAxis
                  tick={{ fill: '#334155', fontSize: 11 }}
                  axisLine={{ stroke: '#CBD5E1' }}
                  tickFormatter={(val: number) =>
                    val >= 1000 ? `${Math.round(val / 1000)} tys.` : `${val}`
                  }
                />
                <Tooltip content={<CustomChartTooltip />} />
                <Legend
                  wrapperStyle={{ fontSize: '12px', paddingTop: '8px' }}
                />
                <Bar
                  dataKey="inflowPln"
                  name="Przychodzące (PLN)"
                  fill="#15803D"
                  radius={[4, 4, 0, 0]}
                  maxBarSize={48}
                />
                <Bar
                  dataKey="outflowPln"
                  name="Wychodzące (PLN)"
                  fill="#B91C1C"
                  radius={[4, 4, 0, 0]}
                  maxBarSize={48}
                />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </div>

      {/* Cash-Out Signals Highlight List */}
      {run.cashOutSignals && run.cashOutSignals.length > 0 && (
        <div className="bg-white rounded-xl border border-amber-200 p-4 sm:p-5 shadow-xs space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-bold text-[#0F2942]">
              Wykryte sygnały potencjalnego wyjścia do waluty fiducjarnej (Fiat Cash-Out)
            </h3>
            <span className="rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-bold text-[#B45309]">
              Wymaga weryfikacji KAS
            </span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {run.cashOutSignals.map((sig) => {
              const sigMeta = translateCashOutSignalType(sig.signalType);
              const sevMeta = translateSeverity(sig.severity);
              return (
                <div
                  key={sig.id}
                  className="rounded-lg border border-slate-200 bg-[#F8FAFC] p-3.5 space-y-2"
                >
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span className={`rounded border px-2 py-0.5 text-[11px] font-bold ${sigMeta.badgeClass}`}>
                      {sigMeta.label}
                    </span>
                    <span className={`rounded border px-2 py-0.5 text-[10px] font-bold ${sevMeta.badgeClass}`}>
                      {sevMeta.label}
                    </span>
                  </div>

                  <div className="flex items-baseline justify-between gap-2">
                    <span className="text-xs font-bold text-[#0F2942]">
                      {sig.counterpartyName || 'Strona przeciwna'} ({sig.network})
                    </span>
                    <span className="text-sm font-extrabold text-[#B91C1C] tabular-nums">
                      {formatPln(sig.estimatedPln)}
                    </span>
                  </div>

                  <p className="text-xs text-[#334155] leading-relaxed">
                    {sig.descriptionPl}
                  </p>

                  <div className="rounded bg-white border border-amber-200 px-2.5 py-1.5 text-[11px] text-[#0F2942]">
                    <span className="font-bold text-[#B45309]">Wskazówka KAS: </span>
                    {sig.verificationHintPl}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Chronological Transactions Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-xs overflow-hidden">
        <div className="p-4 border-b border-slate-200 flex flex-wrap items-center justify-between gap-3 bg-[#F8FAFC]">
          <div>
            <h3 className="text-sm font-bold text-[#0F2942]">
              Chronologiczna tabela transakcji on-chain ({filteredEvents.length})
            </h3>
            <p className="text-xs text-[#64748B]">
              Pełna oś przepływów wraz ze stroną przeciwną, tagami Cash-Out i szacowaną wartością PLN.
            </p>
          </div>

          <div className="flex items-center gap-2">
            <Filter className="w-3.5 h-3.5 text-[#64748B]" />
            <select
              aria-label="Filtruj transakcje po sygnale Cash-Out"
              value={signalFilter}
              onChange={(e) => setSignalFilter(e.target.value)}
              className="rounded-lg border border-slate-300 bg-white px-2.5 py-1.5 text-xs font-medium text-[#0F2942] focus:border-[#0F2942] focus:outline-none"
            >
              <option value="ALL">Wszystkie transakcje ({allTimelineEvents.length})</option>
              <option value="ONLY_SIGNALS">Tylko z sygnałem Cash-Out</option>
              {availableSignalTypes.map((st) => (
                <option key={st} value={st}>
                  {translateCashOutSignalType(st).label}
                </option>
              ))}
            </select>
          </div>
        </div>

        {filteredEvents.length === 0 ? (
          <div className="p-8 text-center text-xs text-[#64748B]">
            Brak transakcji spełniających wybrane kryteria filtrowania.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-[#F8FAFC] border-b border-slate-200 text-[11px] uppercase text-[#64748B]">
                <tr>
                  <th className="px-3 py-2.5 font-semibold">Data UTC</th>
                  <th className="px-3 py-2.5 font-semibold">Sieć / Kierunek</th>
                  <th className="px-3 py-2.5 font-semibold">Hash TX</th>
                  <th className="px-3 py-2.5 font-semibold">Strona przeciwna</th>
                  <th className="px-3 py-2.5 font-semibold text-right">Kwota</th>
                  <th className="px-3 py-2.5 font-semibold text-right">Szac. PLN</th>
                  <th className="px-3 py-2.5 font-semibold">Tag Cash-Out</th>
                  <th className="px-3 py-2.5 font-semibold">Opis analityczny</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200">
                {filteredEvents.map((ev, idx) => {
                  const dir = translateDirection(ev.direction);
                  const txUrl = getExplorerUrl(ev.network, ev.txHash, 'tx');
                  const tagInfo = ev.cashOutSignalType
                    ? translateCashOutSignalType(ev.cashOutSignalType)
                    : null;

                  return (
                    <tr
                      key={`${ev.txHash}-${idx}`}
                      className="hover:bg-slate-50/80 transition-colors"
                    >
                      <td className="px-3 py-2.5 font-mono text-[11px] text-[#334155] whitespace-nowrap">
                        {formatUtcTimestamp(ev.timestamp)}
                      </td>
                      <td className="px-3 py-2.5 whitespace-nowrap">
                        <div className="flex items-center gap-1.5">
                          <span className="rounded bg-[#0F2942] px-1.5 py-0.5 text-[10px] font-bold text-white">
                            {ev.network}
                          </span>
                          <span
                            className={`inline-flex items-center gap-0.5 rounded px-1.5 py-0.5 text-[10px] font-bold ${
                              dir.isOutflow
                                ? 'bg-red-50 text-[#B91C1C]'
                                : 'bg-emerald-50 text-[#15803D]'
                            }`}
                          >
                            {dir.isOutflow ? (
                              <ArrowUpRight className="w-3 h-3" />
                            ) : (
                              <ArrowDownLeft className="w-3 h-3" />
                            )}
                            {dir.label}
                          </span>
                        </div>
                      </td>
                      <td className="px-3 py-2.5 font-mono text-[11px] whitespace-nowrap">
                        <div className="flex items-center gap-1">
                          <span title={ev.txHash} className="text-[#0F2942]">
                            {shortenHash(ev.txHash, 8, 6)}
                          </span>
                          <button
                            type="button"
                            onClick={() => void handleCopy(ev.txHash)}
                            title="Kopiuj hash transakcji"
                            className="p-0.5 rounded hover:bg-slate-200 text-[#64748B] cursor-pointer"
                          >
                            {copiedHash === ev.txHash ? (
                              <Check className="w-3 h-3 text-[#15803D]" />
                            ) : (
                              <Copy className="w-3 h-3" />
                            )}
                          </button>
                          {txUrl && (
                            <a
                              href={txUrl}
                              target="_blank"
                              rel="noopener noreferrer"
                              title="Otwórz transakcję w publicznym eksploratorze"
                              className="p-0.5 rounded hover:bg-slate-200 text-sky-700"
                            >
                              <ExternalLink className="w-3 h-3" />
                            </a>
                          )}
                        </div>
                      </td>
                      <td className="px-3 py-2.5">
                        <div className="font-semibold text-[#0F2942]">
                          {ev.counterpartyTag ||
                            shortenAddress(dir.isOutflow ? ev.toAddress : ev.fromAddress)}
                        </div>
                        {ev.counterpartyRole && (
                          <div className="text-[10px] text-[#64748B]">
                            Rola: {ev.counterpartyRole}
                          </div>
                        )}
                      </td>
                      <td className="px-3 py-2.5 text-right font-mono font-semibold text-[#0F2942] tabular-nums whitespace-nowrap">
                        {formatNumber(ev.amount)} {ev.asset}
                      </td>
                      <td className="px-3 py-2.5 text-right font-bold text-[#0F2942] tabular-nums whitespace-nowrap">
                        {formatPln(ev.estimatedPln)}
                      </td>
                      <td className="px-3 py-2.5 whitespace-nowrap">
                        {tagInfo ? (
                          <span
                            className={`inline-block rounded border px-2 py-0.5 text-[10px] font-bold ${tagInfo.badgeClass}`}
                          >
                            {tagInfo.label}
                          </span>
                        ) : (
                          <span className="text-[11px] text-slate-400">—</span>
                        )}
                      </td>
                      <td className="px-3 py-2.5 text-[#334155] min-w-[220px]">
                        {ev.descriptionPl}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};
