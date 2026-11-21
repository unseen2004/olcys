import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Background,
  BackgroundVariant,
  Controls,
  Handle,
  MarkerType,
  MiniMap,
  Position,
  ReactFlow,
  useEdgesState,
  useNodesState,
  type Edge,
  type Node,
  type NodeProps,
  type NodeTypes,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import {
  AlertTriangle,
  ArrowRightLeft,
  Ban,
  Check,
  Copy,
  ExternalLink,
  FileText,
  Globe,
  Info,
  RotateCcw,
  ShieldAlert,
  ShieldCheck,
  UserCheck,
  Wallet,
  X,
} from 'lucide-react';
import type {
  AddressFindingDto,
  AnalysisRunDto,
  CashOutSignalDto,
  EvidenceArtifactDto,
  GraphNodeDto,
} from '../types/api';
import {
  copyToClipboard,
  formatPln,
  formatUtcTimestamp,
  shortenAddress,
  shortenHash,
  translateCashOutSignalType,
  translateChecksumFormat,
  translateContextClassification,
  translateMetadataKey,
  translateRejectionCategory,
  translateSeverity,
} from '../utils/formatters';

interface EvidenceGraphProps {
  run: AnalysisRunDto;
}

type LayerColumn = 0 | 1 | 2 | 3;

interface KasNodeData extends Record<string, unknown> {
  rawNode: GraphNodeDto;
  column: LayerColumn;
  isRejectedFp: boolean;
  isConfirmedWallet: boolean;
}

type KasFlowNode = Node<KasNodeData, 'kasNode'>;

function classifyNodeColumn(node: GraphNodeDto): LayerColumn {
  const t = (node.type ?? '').toUpperCase();
  if (
    t.includes('SUBJECT') ||
    t.includes('IDENT') ||
    t.includes('PERSON') ||
    t.includes('INPUT')
  ) {
    return 0;
  }
  if (
    t.includes('WEB2') ||
    t.includes('EVIDENCE') ||
    t.includes('SOURCE') ||
    t.includes('ARTIFACT') ||
    t.includes('GITHUB') ||
    t.includes('REDDIT') ||
    t.includes('WAYBACK') ||
    t.includes('ENS') ||
    t.includes('SYNTHETIC')
  ) {
    return 1;
  }
  if (
    t.includes('CASHOUT') ||
    t.includes('CASH_OUT') ||
    t.includes('FIAT') ||
    t.includes('OFFRAMP') ||
    t.includes('GATEWAY') ||
    t.includes('STABLECOIN') ||
    t.includes('P2P_NODE') ||
    t === 'CEX'
  ) {
    return 3;
  }
  if (
    t.includes('WALLET') ||
    t.includes('ADDRESS') ||
    t.includes('FALSE_POSITIVE') ||
    t.includes('REJECTED') ||
    t.includes('FP') ||
    node.falsePositive !== null
  ) {
    return 2;
  }
  return 1;
}

const KasGraphNode: React.FC<NodeProps<KasFlowNode>> = ({ data, selected }) => {
  const { rawNode, column, isRejectedFp, isConfirmedWallet } = data;

  let containerClass =
    'bg-white border-slate-300 text-[#0F2942] hover:border-[#0F2942]';
  let headerBarClass = 'bg-slate-100 text-[#334155] border-b border-slate-200';
  let roleTitle = 'Źródło Web2';
  let icon = <Globe className="w-3.5 h-3.5 text-[#0F2942]" />;

  if (column === 0) {
    containerClass =
      'bg-[#0F2942] border-[#091B2C] text-white hover:border-sky-300';
    headerBarClass = 'bg-white/10 text-sky-100 border-b border-white/15';
    roleTitle = 'Identyfikator Podmiotu';
    icon = <UserCheck className="w-3.5 h-3.5 text-sky-200" />;
  } else if (column === 1) {
    containerClass =
      'bg-white border-sky-200 text-[#0F2942] hover:border-[#0F2942]';
    headerBarClass = 'bg-sky-50 text-[#0F2942] border-b border-sky-100';
    roleTitle = 'Źródło Web2 (OSINT)';
    icon = <Globe className="w-3.5 h-3.5 text-sky-700" />;
  } else if (column === 2 && isRejectedFp) {
    containerClass =
      'bg-red-50/70 border-dashed border-[#B91C1C]/70 text-[#B91C1C] hover:border-[#B91C1C]';
    headerBarClass = 'bg-red-100/80 text-[#B91C1C] border-b border-red-200';
    roleTitle = 'Odrzucone FP (0%)';
    icon = <Ban className="w-3.5 h-3.5 text-[#B91C1C]" />;
  } else if (column === 2 && isConfirmedWallet) {
    const score = rawNode.confidenceScore ?? 0;
    const isHigh = score >= 80;
    containerClass = isHigh
      ? 'bg-white border-2 border-[#15803D] text-[#0F2942]'
      : 'bg-white border-2 border-[#B45309] text-[#0F2942]';
    headerBarClass = isHigh
      ? 'bg-emerald-50 text-[#15803D] border-b border-emerald-200'
      : 'bg-amber-50 text-[#B45309] border-b border-amber-200';
    roleTitle = 'Portfel Niehostowany';
    icon = (
      <Wallet
        className={`w-3.5 h-3.5 ${isHigh ? 'text-[#15803D]' : 'text-[#B45309]'}`}
      />
    );
  } else if (column === 3) {
    containerClass =
      'bg-amber-50/80 border-2 border-[#B45309] text-[#0F2942] hover:border-[#B91C1C]';
    headerBarClass = 'bg-amber-100 text-[#B45309] border-b border-amber-200';
    roleTitle = 'Węzeł Fiat Cash-Out';
    icon = <ArrowRightLeft className="w-3.5 h-3.5 text-[#B45309]" />;
  }

  return (
    <div
      className={`w-[260px] rounded-xl border shadow-sm transition-all cursor-pointer ${containerClass} ${
        selected ? 'ring-3 ring-[#0F2942]/30 shadow-md scale-[1.01]' : ''
      }`}
    >
      {column > 0 && (
        <Handle
          type="target"
          position={Position.Left}
          className="!w-2.5 !h-2.5 !bg-[#0F2942] !border-2 !border-white"
        />
      )}

      <div
        className={`flex items-center justify-between px-3 py-1.5 rounded-t-[10px] text-[10px] font-bold uppercase tracking-wider ${headerBarClass}`}
      >
        <span className="inline-flex items-center gap-1.5">
          {icon}
          {roleTitle}
        </span>

        {column === 2 && rawNode.confidenceScore !== null && (
          <span
            className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${
              isRejectedFp
                ? 'bg-[#B91C1C] text-white'
                : (rawNode.confidenceScore ?? 0) >= 80
                  ? 'bg-[#15803D] text-white'
                  : 'bg-[#B45309] text-white'
            }`}
          >
            {isRejectedFp ? '0% FP' : `${rawNode.confidenceScore}%`}
          </span>
        )}
      </div>

      <div className="px-3 py-2.5 space-y-1">
        <div
          className={`text-xs font-bold leading-snug break-all ${
            isRejectedFp ? 'line-through decoration-[#B91C1C] decoration-2' : ''
          } ${column === 2 ? 'font-mono text-[11px]' : ''}`}
        >
          {rawNode.label}
        </div>
        {rawNode.sublabel && (
          <div
            className={`text-[11px] leading-snug line-clamp-2 ${
              column === 0
                ? 'text-slate-200'
                : isRejectedFp
                  ? 'text-[#B91C1C]/90 font-medium'
                  : 'text-[#64748B]'
            }`}
          >
            {rawNode.sublabel}
          </div>
        )}
      </div>

      {column < 3 && (
        <Handle
          type="source"
          position={Position.Right}
          className="!w-2.5 !h-2.5 !bg-[#0F2942] !border-2 !border-white"
        />
      )}
    </div>
  );
};

const NODE_TYPES: NodeTypes = {
  kasNode: KasGraphNode,
};

function buildGraphElements(run: AnalysisRunDto): {
  initialNodes: KasFlowNode[];
  initialEdges: Edge[];
} {
  let rawNodes = run.graph?.nodes ?? [];
  let rawEdges = run.graph?.edges ?? [];

  // Defensive fallback if backend returned empty graph arrays
  if (rawNodes.length === 0) {
    const synthesizedNodes: GraphNodeDto[] = [];
    const synthesizedEdges: { id: string; source: string; target: string; label: string; rejected: boolean }[] = [];

    const subjectNodeId = `subject-${run.subjectId}`;
    synthesizedNodes.push({
      id: subjectNodeId,
      type: 'SUBJECT',
      label: run.subjectDisplayName,
      sublabel: run.queriedIdentifiers.map((i) => `${i.type}: ${i.value}`).join(', '),
      confidenceScore: null,
      falsePositive: null,
      metadata: {
        identifiersCount: run.queriedIdentifiers.length,
      },
    });

    for (const ev of run.evidences) {
      const evId = `ev-${ev.id}`;
      synthesizedNodes.push({
        id: evId,
        type: 'WEB2_SOURCE',
        label: ev.sourceName,
        sublabel: ev.matchedIdentifier,
        confidenceScore: null,
        falsePositive: null,
        metadata: {
          evidenceId: ev.id,
          url: ev.url,
          fetchedAt: ev.fetchedAt,
          sha256: ev.sha256,
          snippet: ev.snippet,
        },
      });
      synthesizedEdges.push({
        id: `e-${subjectNodeId}-${evId}`,
        source: subjectNodeId,
        target: evId,
        label: ev.matchedIdentifier,
        rejected: false,
      });
    }

    for (const addr of [...run.confirmedAddresses, ...run.rejectedAddresses]) {
      const wId = `addr-${addr.id}`;
      synthesizedNodes.push({
        id: wId,
        type: addr.falsePositive ? 'FALSE_POSITIVE' : 'CONFIRMED_WALLET',
        label: `${addr.network}: ${shortenAddress(addr.address)}`,
        sublabel: addr.falsePositive
          ? addr.rejectionReason ?? addr.entityTag ?? 'Wykluczono przez Firewall FP'
          : addr.confidenceLevelPl,
        confidenceScore: addr.confidenceScore,
        falsePositive: addr.falsePositive,
        metadata: {
          address: addr.address,
          network: addr.network,
          findingId: addr.id,
        },
      });
      for (const evId of addr.evidenceIds ?? []) {
        synthesizedEdges.push({
          id: `e-${evId}-${wId}`,
          source: `ev-${evId}`,
          target: wId,
          label: addr.falsePositive ? 'Odrzucono FP' : `${addr.confidenceScore}%`,
          rejected: addr.falsePositive,
        });
      }
    }

    for (const sig of run.cashOutSignals) {
      const cId = `cashout-${sig.id}`;
      synthesizedNodes.push({
        id: cId,
        type: 'CASHOUT',
        label: sig.counterpartyName || sig.signalType,
        sublabel: formatPln(sig.estimatedPln),
        confidenceScore: null,
        falsePositive: null,
        metadata: {
          signalId: sig.id,
          address: sig.address,
          signalType: sig.signalType,
          estimatedPln: sig.estimatedPln,
        },
      });
      const sourceWallet = run.confirmedAddresses.find((a) => a.address === sig.address);
      if (sourceWallet) {
        synthesizedEdges.push({
          id: `e-${sourceWallet.id}-${cId}`,
          source: `addr-${sourceWallet.id}`,
          target: cId,
          label: translateCashOutSignalType(sig.signalType).label,
          rejected: false,
        });
      }
    }

    rawNodes = synthesizedNodes;
    rawEdges = synthesizedEdges;
  }

  const buckets: Record<LayerColumn, GraphNodeDto[]> = {
    0: [],
    1: [],
    2: [],
    3: [],
  };

  for (const n of rawNodes) {
    const col = classifyNodeColumn(n);
    buckets[col].push(n);
  }

  // Sort column 2: confirmed wallets first (by score desc), then rejected FP
  buckets[2].sort((a, b) => {
    const aFp = a.falsePositive ? 1 : 0;
    const bFp = b.falsePositive ? 1 : 0;
    if (aFp !== bFp) return aFp - bFp;
    return (b.confidenceScore ?? 0) - (a.confidenceScore ?? 0);
  });

  const maxCount = Math.max(
    1,
    buckets[0].length,
    buckets[1].length,
    buckets[2].length,
    buckets[3].length,
  );
  const rowHeight = 148;
  const totalHeight = maxCount * rowHeight;
  const colPositions = [40, 380, 730, 1080];

  const flowNodes: KasFlowNode[] = [];
  ([0, 1, 2, 3] as LayerColumn[]).forEach((col) => {
    const list = buckets[col];
    const colTotalHeight = list.length * rowHeight;
    const startY = Math.max(30, (totalHeight - colTotalHeight) / 2 + 20);

    list.forEach((rawNode, idx) => {
      const isRejectedFp = rawNode.falsePositive === true;
      const isConfirmedWallet = col === 2 && !isRejectedFp;

      flowNodes.push({
        id: rawNode.id,
        type: 'kasNode',
        position: {
          x: colPositions[col],
          y: startY + idx * rowHeight,
        },
        data: {
          rawNode,
          column: col,
          isRejectedFp,
          isConfirmedWallet,
        },
      });
    });
  });

  const nodeIdSet = new Set(flowNodes.map((n) => n.id));
  const flowEdges: Edge[] = rawEdges
    .filter((edge) => nodeIdSet.has(edge.source) && nodeIdSet.has(edge.target))
    .map((edge) => ({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      type: 'smoothstep',
      animated: false,
      label: edge.label,
      labelStyle: {
        fontSize: 10,
        fontWeight: 600,
        fill: edge.rejected ? '#B91C1C' : '#0F2942',
      },
      labelBgStyle: {
        fill: edge.rejected ? '#FEF2F2' : '#FFFFFF',
        fillOpacity: 0.94,
        stroke: edge.rejected ? '#FECACA' : '#E2E8F0',
      },
      labelBgPadding: [6, 3] as [number, number],
      labelBgBorderRadius: 6,
      style: {
        stroke: edge.rejected ? '#B91C1C' : '#475569',
        strokeWidth: edge.rejected ? 1.8 : 2,
        strokeDasharray: edge.rejected ? '6 4' : undefined,
      },
      markerEnd: {
        type: MarkerType.ArrowClosed,
        color: edge.rejected ? '#B91C1C' : '#475569',
        width: 16,
        height: 16,
      },
    }));

  return { initialNodes: flowNodes, initialEdges: flowEdges };
}

export const EvidenceGraph: React.FC<EvidenceGraphProps> = ({ run }) => {
  const { initialNodes, initialEdges } = useMemo(() => buildGraphElements(run), [run]);
  const [nodes, setNodes, onNodesChange] = useNodesState<KasFlowNode>(initialNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>(initialEdges);
  const [selectedNode, setSelectedNode] = useState<GraphNodeDto | null>(null);
  const [copiedText, setCopiedText] = useState<string | null>(null);

  useEffect(() => {
    setNodes(initialNodes);
    setEdges(initialEdges);
    setSelectedNode(null);
  }, [initialNodes, initialEdges, setNodes, setEdges]);

  const handleResetLayout = useCallback(() => {
    setNodes(initialNodes);
    setEdges(initialEdges);
  }, [initialNodes, initialEdges, setNodes, setEdges]);

  const handleCopy = async (text: string) => {
    const ok = await copyToClipboard(text);
    if (ok) {
      setCopiedText(text);
      setTimeout(() => setCopiedText(null), 1800);
    }
  };

  // Resolve full domain entities connected to the clicked node
  const inspectorContext = useMemo(() => {
    if (!selectedNode) return null;

    const meta = selectedNode.metadata ?? {};
    const metaEvidenceId = typeof meta.evidenceId === 'string' ? meta.evidenceId : null;
    const metaUrl = typeof meta.url === 'string' ? meta.url : null;
    const metaAddress = typeof meta.address === 'string' ? meta.address : null;
    const metaFindingId = typeof meta.findingId === 'string' ? meta.findingId : null;
    const metaSignalId = typeof meta.signalId === 'string' ? meta.signalId : null;

    const matchedEvidence: EvidenceArtifactDto | undefined = run.evidences.find(
      (ev) =>
        ev.id === metaEvidenceId ||
        ev.id === selectedNode.id ||
        `ev-${ev.id}` === selectedNode.id ||
        (metaUrl && ev.url === metaUrl) ||
        (selectedNode.sublabel && ev.url === selectedNode.sublabel),
    );

    const allFindings = [...run.confirmedAddresses, ...run.rejectedAddresses];
    const matchedFinding: AddressFindingDto | undefined = allFindings.find(
      (f) =>
        f.id === metaFindingId ||
        f.id === selectedNode.id ||
        `addr-${f.id}` === selectedNode.id ||
        (metaAddress && f.address.toLowerCase() === metaAddress.toLowerCase()) ||
        selectedNode.label.includes(f.address) ||
        selectedNode.label.includes(shortenAddress(f.address)),
    );

    const linkedEvidences: EvidenceArtifactDto[] = matchedFinding
      ? run.evidences.filter((ev) => matchedFinding.evidenceIds.includes(ev.id))
      : matchedEvidence
        ? [matchedEvidence]
        : [];

    const matchedCashOut: CashOutSignalDto | undefined = run.cashOutSignals.find(
      (s) =>
        s.id === metaSignalId ||
        s.id === selectedNode.id ||
        `cashout-${s.id}` === selectedNode.id ||
        (metaAddress && s.address === metaAddress && selectedNode.label.includes(s.counterpartyName)),
    );

    return {
      node: selectedNode,
      column: classifyNodeColumn(selectedNode),
      matchedEvidence,
      matchedFinding,
      linkedEvidences,
      matchedCashOut,
    };
  }, [selectedNode, run]);

  return (
    <div className="space-y-3">
      {/* Column Legend + Controls Bar */}
      <div className="bg-white border border-slate-200 rounded-xl p-3 flex flex-wrap items-center justify-between gap-3 shadow-xs">
        <div className="flex flex-wrap items-center gap-2 text-xs">
          <span className="inline-flex items-center gap-1.5 rounded-md bg-[#0F2942] px-2.5 py-1 font-semibold text-white">
            1. Identyfikator Podmiotu
          </span>
          <span className="text-slate-400">→</span>
          <span className="inline-flex items-center gap-1.5 rounded-md bg-sky-50 border border-sky-200 px-2.5 py-1 font-semibold text-[#0F2942]">
            2. Źródła Web2
          </span>
          <span className="text-slate-400">→</span>
          <span className="inline-flex items-center gap-1.5 rounded-md bg-emerald-50 border border-emerald-300 px-2.5 py-1 font-semibold text-[#15803D]">
            3a. Portfele Niehostowane (%)
          </span>
          <span className="inline-flex items-center gap-1.5 rounded-md bg-red-50 border border-dashed border-[#B91C1C] px-2.5 py-1 font-semibold text-[#B91C1C] line-through">
            3b. Odrzucone FP (0%)
          </span>
          <span className="text-slate-400">→</span>
          <span className="inline-flex items-center gap-1.5 rounded-md bg-amber-50 border border-amber-300 px-2.5 py-1 font-semibold text-[#B45309]">
            4. Węzły Fiat Cash-Out
          </span>
        </div>

        <button
          type="button"
          onClick={handleResetLayout}
          className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-2.5 py-1.5 text-xs font-medium text-[#334155] hover:bg-slate-50 transition-colors cursor-pointer"
        >
          <RotateCcw className="w-3.5 h-3.5" />
          Przywróć układ warstwowy
        </button>
      </div>

      {/* Main Graph + Side Inspector */}
      <div className="grid grid-cols-1 xl:grid-cols-12 gap-4 items-stretch">
        <div
          className={`${
            inspectorContext ? 'xl:col-span-8' : 'xl:col-span-12'
          } bg-white border border-slate-200 rounded-xl shadow-xs overflow-hidden relative h-[640px]`}
        >
          {nodes.length === 0 ? (
            <div className="h-full flex flex-col items-center justify-center p-8 text-center text-[#64748B]">
              <Info className="w-10 h-10 text-slate-300 mb-2" />
              <p className="text-sm font-semibold text-[#0F2942]">
                Brak węzłów do wyświetlenia na grafie dla tej analizy
              </p>
              <p className="text-xs mt-1 max-w-md">
                Dla badanych identyfikatorów nie odnaleziono powiązań Web2 ani adresów kryptowalutowych.
              </p>
            </div>
          ) : (
            <ReactFlow
              nodes={nodes}
              edges={edges}
              nodeTypes={NODE_TYPES}
              onNodesChange={onNodesChange}
              onEdgesChange={onEdgesChange}
              onNodeClick={(_event, node) => {
                setSelectedNode(node.data.rawNode);
              }}
              onPaneClick={() => setSelectedNode(null)}
              fitView
              fitViewOptions={{ padding: 0.16 }}
              minZoom={0.25}
              maxZoom={1.75}
              proOptions={{ hideAttribution: true }}
            >
              <Background variant={BackgroundVariant.Dots} gap={18} size={1.2} color="#CBD5E1" />
              <Controls position="bottom-left" />
              <MiniMap
                position="bottom-right"
                pannable
                zoomable
                nodeColor={(node) => {
                  const data = node.data as KasNodeData | undefined;
                  if (!data) return '#64748B';
                  if (data.column === 0) return '#0F2942';
                  if (data.column === 1) return '#0284C7';
                  if (data.isRejectedFp) return '#B91C1C';
                  if (data.isConfirmedWallet) return '#15803D';
                  return '#B45309';
                }}
                maskColor="rgba(15, 41, 66, 0.08)"
              />
            </ReactFlow>
          )}

          {!inspectorContext && nodes.length > 0 && (
            <div className="pointer-events-none absolute top-3 left-3 rounded-lg border border-slate-200 bg-white/95 px-3 py-1.5 text-[11px] font-medium text-[#334155] shadow-xs backdrop-blur-xs">
              Kliknij dowolny węzeł na grafie, aby otworzyć boczny inspektor dowodowy (URL, UTC, SHA-256, uzasadnienie).
            </div>
          )}
        </div>

        {/* Side Evidence Inspector */}
        {inspectorContext && (
          <div className="xl:col-span-4 bg-white border border-slate-200 rounded-xl shadow-xs flex flex-col max-h-[640px] overflow-hidden">
            <div className="bg-[#0F2942] text-white px-4 py-3 flex items-center justify-between gap-2">
              <div className="min-w-0">
                <div className="text-[10px] font-bold uppercase tracking-wider text-sky-200">
                  Inspektor Dowodowy KAS
                </div>
                <h3 className="text-sm font-bold truncate">{inspectorContext.node.label}</h3>
              </div>
              <button
                type="button"
                onClick={() => setSelectedNode(null)}
                aria-label="Zamknij inspektor"
                className="p-1 rounded-md text-slate-300 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="p-4 space-y-4 overflow-y-auto text-xs">
              {/* Sublabel & confidence summary */}
              <div className="rounded-lg border border-slate-200 bg-[#F8FAFC] p-3 space-y-1.5">
                <div className="text-[11px] font-semibold uppercase tracking-wide text-[#64748B]">
                  Opis węzła
                </div>
                <p className="text-xs font-medium text-[#0F2942]">
                  {inspectorContext.node.sublabel || 'Brak dodatkowego opisu'}
                </p>
                {inspectorContext.node.confidenceScore !== null && (
                  <div className="pt-1">
                    {inspectorContext.node.falsePositive ? (
                      <span className="inline-flex items-center gap-1 rounded-md bg-red-100 px-2 py-1 text-xs font-bold text-[#B91C1C]">
                        <ShieldAlert className="w-3.5 h-3.5" />
                        Odrzucono jako False Positive (0%)
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1 rounded-md bg-emerald-100 px-2 py-1 text-xs font-bold text-[#15803D]">
                        <ShieldCheck className="w-3.5 h-3.5" />
                        Wskaźnik pewności: {inspectorContext.node.confidenceScore}%
                      </span>
                    )}
                  </div>
                )}
              </div>

              {/* Matched Finding details */}
              {inspectorContext.matchedFinding && (
                <div className="space-y-2.5">
                  <div className="text-[11px] font-bold uppercase tracking-wide text-[#334155]">
                    {inspectorContext.matchedFinding.falsePositive
                      ? 'Uzasadnienie odrzucenia (Firewall FP)'
                      : 'Ocena portfela niehostowanego'}
                  </div>

                  <div className="rounded-lg border border-slate-200 p-3 space-y-2">
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-mono text-[11px] break-all text-[#0F2942] font-semibold">
                        {inspectorContext.matchedFinding.address}
                      </span>
                      <button
                        type="button"
                        onClick={() => void handleCopy(inspectorContext.matchedFinding!.address)}
                        className="shrink-0 p-1 rounded hover:bg-slate-100 text-[#64748B] cursor-pointer"
                        title="Kopiuj adres"
                      >
                        {copiedText === inspectorContext.matchedFinding.address ? (
                          <Check className="w-3.5 h-3.5 text-[#15803D]" />
                        ) : (
                          <Copy className="w-3.5 h-3.5" />
                        )}
                      </button>
                    </div>

                    <div className="flex flex-wrap gap-1.5">
                      <span className="rounded bg-slate-100 px-2 py-0.5 text-[11px] font-semibold text-[#0F2942]">
                        {inspectorContext.matchedFinding.network}
                      </span>
                      <span className="rounded bg-slate-100 px-2 py-0.5 text-[11px] text-[#334155]">
                        {translateChecksumFormat(inspectorContext.matchedFinding.checksumFormat)}
                      </span>
                    </div>

                    {inspectorContext.matchedFinding.falsePositive ? (
                      <div className="rounded-md bg-red-50 border border-red-200 p-2.5 space-y-1 text-[#B91C1C]">
                        <div className="font-bold">
                          Kategoria: {translateRejectionCategory(inspectorContext.matchedFinding.rejectionCategory)}
                        </div>
                        {inspectorContext.matchedFinding.entityTag && (
                          <div>Podmiot: {inspectorContext.matchedFinding.entityTag}</div>
                        )}
                        <div>
                          Powód: {inspectorContext.matchedFinding.rejectionReason ?? 'Odrzucono regułą ochronną'}
                        </div>
                      </div>
                    ) : (
                      <div className="space-y-1.5">
                        <div className="text-[11px] font-semibold text-[#15803D]">
                          Poziom: {inspectorContext.matchedFinding.confidenceLevelPl} ({inspectorContext.matchedFinding.confidenceScore}%)
                        </div>
                        {inspectorContext.matchedFinding.scoreBreakdown.map((item) => (
                          <div
                            key={item.ruleId}
                            className="rounded bg-[#F8FAFC] border border-slate-200 p-2"
                          >
                            <div className="flex items-center justify-between font-semibold text-[#0F2942]">
                              <span>{item.label}</span>
                              <span className="text-[#15803D]">+{item.delta} pkt</span>
                            </div>
                            <div className="text-[11px] text-[#64748B] mt-0.5">
                              {item.explanationPl}
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              )}

              {/* Matched Cash-Out Signal details */}
              {inspectorContext.matchedCashOut && (
                <div className="space-y-2">
                  <div className="text-[11px] font-bold uppercase tracking-wide text-[#B45309]">
                    Szczegóły sygnału Fiat Cash-Out
                  </div>
                  <div className="rounded-lg border border-amber-200 bg-amber-50/70 p-3 space-y-2">
                    <div className="flex flex-wrap items-center gap-1.5">
                      <span
                        className={`rounded border px-2 py-0.5 text-[10px] font-bold ${
                          translateCashOutSignalType(inspectorContext.matchedCashOut.signalType)
                            .badgeClass
                        }`}
                      >
                        {
                          translateCashOutSignalType(inspectorContext.matchedCashOut.signalType)
                            .label
                        }
                      </span>
                      <span
                        className={`rounded border px-2 py-0.5 text-[10px] font-bold ${
                          translateSeverity(inspectorContext.matchedCashOut.severity).badgeClass
                        }`}
                      >
                        {translateSeverity(inspectorContext.matchedCashOut.severity).label}
                      </span>
                    </div>
                    <div className="text-sm font-bold text-[#0F2942] tabular-nums">
                      Szac. wartość: {formatPln(inspectorContext.matchedCashOut.estimatedPln)}
                    </div>
                    <p className="text-[#334155] leading-relaxed">
                      {inspectorContext.matchedCashOut.descriptionPl}
                    </p>
                    <div className="rounded bg-white border border-amber-200 p-2 text-[11px] text-[#0F2942]">
                      <span className="font-bold text-[#B45309]">Wskazówka KAS: </span>
                      {inspectorContext.matchedCashOut.verificationHintPl}
                    </div>
                  </div>
                </div>
              )}

              {/* Evidence Artifacts (Chain-of-Custody) */}
              {inspectorContext.linkedEvidences.length > 0 && (
                <div className="space-y-2.5">
                  <div className="text-[11px] font-bold uppercase tracking-wide text-[#334155] flex items-center gap-1.5">
                    <FileText className="w-3.5 h-3.5 text-[#0F2942]" />
                    Powiązane dowody źródłowe (Chain-of-Custody)
                  </div>

                  {inspectorContext.linkedEvidences.map((ev) => {
                    const ctx = translateContextClassification(ev.contextClassification);
                    return (
                      <div
                        key={ev.id}
                        className="rounded-lg border border-slate-200 bg-white p-3 space-y-2"
                      >
                        <div className="flex items-center justify-between gap-2">
                          <span className="font-bold text-[#0F2942]">{ev.sourceName}</span>
                          <span className={`rounded border px-1.5 py-0.5 text-[10px] font-semibold ${ctx.badgeClass}`}>
                            {ctx.label}
                          </span>
                        </div>

                        <div>
                          <div className="text-[10px] font-semibold uppercase text-[#64748B]">
                            URL źródła
                          </div>
                          <a
                            href={ev.url}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="inline-flex items-center gap-1 text-[#0F2942] underline break-all hover:text-sky-700"
                          >
                            {ev.url}
                            <ExternalLink className="w-3 h-3 shrink-0" />
                          </a>
                        </div>

                        <div className="grid grid-cols-1 gap-1.5">
                          <div>
                            <div className="text-[10px] font-semibold uppercase text-[#64748B]">
                              Znacznik czasu UTC
                            </div>
                            <div className="font-mono text-[11px] text-[#334155]">
                              {formatUtcTimestamp(ev.fetchedAt)}
                            </div>
                          </div>

                          <div>
                            <div className="text-[10px] font-semibold uppercase text-[#64748B]">
                              Hash dowodowy SHA-256
                            </div>
                            <div className="flex items-center justify-between gap-1 rounded bg-[#F8FAFC] border border-slate-200 px-2 py-1">
                              <span className="font-mono text-[10px] text-[#334155] break-all">
                                {ev.sha256}
                              </span>
                              <button
                                type="button"
                                onClick={() => void handleCopy(ev.sha256)}
                                className="shrink-0 p-1 rounded hover:bg-slate-200 text-[#64748B] cursor-pointer"
                                title="Kopiuj SHA-256"
                              >
                                {copiedText === ev.sha256 ? (
                                  <Check className="w-3 h-3 text-[#15803D]" />
                                ) : (
                                  <Copy className="w-3 h-3" />
                                )}
                              </button>
                            </div>
                          </div>
                        </div>

                        <div>
                          <div className="text-[10px] font-semibold uppercase text-[#64748B] mb-0.5">
                            Cytat z pozyskanego materiału
                          </div>
                          <blockquote className="rounded border-l-2 border-[#0F2942] bg-[#F8FAFC] px-2.5 py-1.5 text-[11px] italic text-[#334155] break-words">
                            „{ev.snippet}”
                          </blockquote>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}

              {/* Raw Metadata fallback (translated keys) */}
              {inspectorContext.node.metadata &&
                Object.keys(inspectorContext.node.metadata).length > 0 && (
                  <div className="space-y-1.5">
                    <div className="text-[11px] font-bold uppercase tracking-wide text-[#334155]">
                      Metadane węzła
                    </div>
                    <div className="rounded-lg border border-slate-200 bg-[#F8FAFC] divide-y divide-slate-200">
                      {Object.entries(inspectorContext.node.metadata).map(([k, v]) => {
                        const valStr =
                          typeof v === 'string'
                            ? k.toLowerCase().includes('hash') || k === 'sha256'
                              ? shortenHash(v, 14, 10)
                              : v
                            : JSON.stringify(v);
                        return (
                          <div key={k} className="px-2.5 py-1.5 flex flex-col gap-0.5">
                            <span className="text-[10px] font-semibold text-[#64748B]">
                              {translateMetadataKey(k)}
                            </span>
                            <span className="font-mono text-[11px] text-[#0F2942] break-all">
                              {k === 'url' && typeof v === 'string' ? (
                                <a
                                  href={v}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  className="underline text-sky-800 hover:text-sky-600"
                                >
                                  {v}
                                </a>
                              ) : (
                                valStr
                              )}
                            </span>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}

              {!inspectorContext.matchedEvidence &&
                !inspectorContext.matchedFinding &&
                !inspectorContext.matchedCashOut &&
                (!inspectorContext.node.metadata ||
                  Object.keys(inspectorContext.node.metadata).length === 0) && (
                  <div className="flex items-start gap-2 rounded-lg border border-slate-200 bg-slate-50 p-3 text-[#64748B]">
                    <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
                    <span>Brak dodatkowych metadanych dla tego węzła.</span>
                  </div>
                )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
