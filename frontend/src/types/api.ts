export type AnalysisMode = 'HYBRID' | 'LIVE' | 'SYNTHETIC';

export interface IdentifierDto {
  type: string;
  value: string;
}

export interface CreateSubjectRequest {
  displayName: string;
  identifiers: IdentifierDto[];
  mode?: AnalysisMode;
  sources?: string[];
  networks?: string[];
  snapshotStep?: number;
}

export interface TriggerRunRequest {
  mode?: AnalysisMode;
  sources?: string[];
  networks?: string[];
  snapshotStep?: number;
}

export interface EvidenceArtifactDto {
  id: string;
  sourceName: string;
  sourceCategory: string;
  url: string;
  fetchedAt: string;
  sha256: string;
  snippet: string;
  matchedIdentifier: string;
  extractedAddresses: string[];
  contextClassification: string;
}

export interface ScoreBreakdownItemDto {
  ruleId: string;
  label: string;
  delta: number;
  evidenceIds: string[];
  explanationPl: string;
}

export interface TimelineEventDto {
  txHash: string;
  timestamp: string;
  network: string;
  direction: string;
  fromAddress: string;
  toAddress: string;
  amount: number;
  asset: string;
  estimatedPln: number;
  counterpartyTag: string | null;
  counterpartyRole: string | null;
  cashOutSignalType: string | null;
  descriptionPl: string;
}

export interface OnChainMetricsDto {
  address: string;
  network: string;
  isSmartContract: boolean;
  bytecodePrefix: string | null;
  txCount: number;
  totalInflowNative: number;
  totalOutflowNative: number;
  nativeSymbol: string;
  estimatedTotalVolumePln: number;
  stablecoinVolumeUsd: number;
  interactedEntities: string[];
  timeline: TimelineEventDto[];
}

export interface AddressFindingDto {
  id: string;
  address: string;
  network: string;
  checksumFormat: string;
  checksumValid: boolean;
  confidenceScore: number;
  confidenceLevelPl: string;
  falsePositive: boolean;
  rejectionReason: string | null;
  rejectionCategory: string | null;
  entityTag: string | null;
  interactionRecorded: boolean;
  scoreBreakdown: ScoreBreakdownItemDto[];
  evidenceIds: string[];
  onChainMetrics: OnChainMetricsDto | null;
}

export interface CashOutSignalDto {
  id: string;
  signalType: string;
  severity: string;
  address: string;
  network: string;
  txHash: string | null;
  timestamp: string | null;
  estimatedPln: number;
  counterpartyName: string;
  descriptionPl: string;
  verificationHintPl: string;
}

export interface GraphNodeDto {
  id: string;
  type: string;
  label: string;
  sublabel: string;
  confidenceScore: number | null;
  falsePositive: boolean | null;
  metadata: Record<string, unknown>;
}

export interface GraphEdgeDto {
  id: string;
  source: string;
  target: string;
  label: string;
  rejected: boolean;
}

export interface EvidenceGraphDto {
  nodes: GraphNodeDto[];
  edges: GraphEdgeDto[];
}

export interface ReportSourceFactDto {
  evidenceId: string;
  sourceName: string;
  url: string;
  timestampUtc: string;
  sha256: string;
  matchedIdentifier: string;
  quoteSnippet: string;
  detectedAddresses: string[];
}

export interface ReportComputationZoneDto {
  methodologySummaryPl: string;
  confirmedWallets: AddressFindingDto[];
  rejectedFalsePositives: AddressFindingDto[];
  totalEstimatedVolumePln: number;
  totalStablecoinVolumeUsd: number;
  totalTransactionsAnalyzed: number;
  chronologicalFlow: TimelineEventDto[];
}

export interface ReportVerificationZoneDto {
  cashOutSignals: CashOutSignalDto[];
  pit38RiskIndicatorsPl: string[];
  investigativeRecommendationsPl: string[];
  limitationsAndLegalDisclaimerPl: string[];
}

export interface OsintReportDto {
  executiveSummaryPl: string;
  aiProviderUsed: string;
  generatedAt: string;
  zone1SourceFacts: ReportSourceFactDto[];
  zone2Computations: ReportComputationZoneDto;
  zone3VerificationConclusions: ReportVerificationZoneDto;
}

export interface AnalysisRunDto {
  runId: string;
  subjectId: string;
  subjectDisplayName: string;
  runNumber: number;
  snapshotStep: number;
  mode: AnalysisMode;
  createdAt: string;
  durationMs: number;
  queriedIdentifiers: IdentifierDto[];
  activeSources: string[];
  activeNetworks: string[];
  evidences: EvidenceArtifactDto[];
  confirmedAddresses: AddressFindingDto[];
  rejectedAddresses: AddressFindingDto[];
  cashOutSignals: CashOutSignalDto[];
  graph: EvidenceGraphDto;
  report: OsintReportDto;
}

export interface RunSummaryDto {
  runId: string;
  runNumber: number;
  snapshotStep: number;
  mode: AnalysisMode;
  createdAt: string;
  validatedAddressCount: number;
  rejectedFpCount: number;
  maxConfidenceScore: number;
  cashOutSignalCount: number;
  totalVolumePln: number;
}

export interface SubjectSummaryDto {
  id: string;
  displayName: string;
  identifiers: IdentifierDto[];
  createdAt: string;
  runs: RunSummaryDto[];
}

export interface WalletRotationEventDto {
  sourceName: string;
  sourceUrl: string;
  previousAddress: string;
  newAddress: string;
  network: string;
  previousSeenAt: string;
  newSeenAt: string;
  alertPl: string;
}

export interface AddressScoreChangeDto {
  address: string;
  network: string;
  oldScore: number | null;
  newScore: number | null;
  changeType: string;
}

export interface RunDiffDto {
  subjectId: string;
  subjectDisplayName: string;
  fromRunId: string;
  toRunId: string;
  fromRunNumber: number;
  toRunNumber: number;
  fromTimestamp: string;
  toTimestamp: string;
  walletRotationDetected: boolean;
  walletRotations: WalletRotationEventDto[];
  addedAddresses: string[];
  removedAddresses: string[];
  scoreChanges: AddressScoreChangeDto[];
  volumeDeltaPln: number;
  newCashOutSignals: CashOutSignalDto[];
  summaryPl: string;
}

export interface ScenarioPresetDto {
  id: string;
  titlePl: string;
  subtitlePl: string;
  descriptionPl: string;
  expectedOutcomePl: string;
  displayName: string;
  identifiers: IdentifierDto[];
  recommendedMode: AnalysisMode;
  sources: string[];
  networks: string[];
  supportsWalletRotationDemo: boolean;
}

export interface HealthStatusDto {
  status: string;
  service: string;
  javaVersion: string;
  virtualThreadsEnabled: boolean;
  databaseStatus: string;
  web2Connectors: string[];
  web3Connectors: string[];
  aiProvider: string;
  aiModel: string;
  externalDataTransfer: boolean;
}
