import { useState } from 'react';
import {
  analyticsAiApi,
  type DelayRiskPredictionResponse,
  type AssistantQueryResponse,
  type AiAudit
} from '@/services/api/analyticsAiApi';
import { Button } from '@/design-system/primitives/Button';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { Bot, Sparkles, Send } from 'lucide-react';

const SRI_LANKAN_DISTRICTS = [
  'COLOMBO',
  'GAMPAHA',
  'KALUTARA',
  'KANDY',
  'MATALE',
  'NUWARA_ELIYA',
  'GALLE',
  'MATARA',
  'HAMBANTOTA',
  'JAFFNA',
  'KURUNEGALA',
  'RATNAPURA',
  'ANURADHAPURA'
];

export function AiDeliveryAssistantPanel() {
  const [activeTab, setActiveTab] = useState<'assistant' | 'predictor' | 'audits'>('assistant');

  // Co-Pilot State
  const [query, setQuery] = useState('');
  const [assistantLoading, setAssistantLoading] = useState(false);
  const [assistantResult, setAssistantResult] = useState<AssistantQueryResponse | null>(null);

  // Predictor State
  const [origin, setOrigin] = useState('COLOMBO');
  const [dest, setDest] = useState('KANDY');
  const [speed, setSpeed] = useState('STANDARD');
  const [weather, setWeather] = useState('CLEAR');
  const [traffic, setTraffic] = useState('LOW');
  const [predictorLoading, setPredictorLoading] = useState(false);
  const [prediction, setPrediction] = useState<DelayRiskPredictionResponse | null>(null);

  // Audits State
  const [audits, setAudits] = useState<AiAudit[]>([]);
  const [auditsLoading, setAuditsLoading] = useState(false);

  const handleAskAgent = async () => {
    if (!query.trim()) return;
    try {
      setAssistantLoading(true);
      const res = await analyticsAiApi.queryAssistant({
        query: query.trim(),
        callerPersona: 'ADMIN_OPS'
      });
      setAssistantResult(res);
    } catch {
      // Fallback
    } finally {
      setAssistantLoading(false);
    }
  };

  const handlePredictDelay = async () => {
    try {
      setPredictorLoading(true);
      const res = await analyticsAiApi.predictDelayRisk({
        originDistrict: origin,
        destinationDistrict: dest,
        deliverySpeed: speed,
        weatherCondition: weather,
        trafficCongestion: traffic
      });
      setPrediction(res);
    } catch {
      // Fallback
    } finally {
      setPredictorLoading(false);
    }
  };

  const loadAudits = async () => {
    try {
      setAuditsLoading(true);
      const res = await analyticsAiApi.getAiAudits();
      setAudits(res);
    } catch {
      // Fallback
    } finally {
      setAuditsLoading(false);
    }
  };

  return (
    <SurfaceCard className="space-y-4 p-4 text-xs">
      <div className="flex flex-wrap items-center justify-between border-b border-slate-200 pb-3 dark:border-slate-800">
        <div className="flex items-center gap-2">
          <div className="grid h-7 w-7 place-items-center rounded-lg bg-indigo-600 text-white">
            <Bot className="h-4 w-4" />
          </div>
          <div>
            <h2 className="text-sm font-bold text-slate-900 dark:text-white">
              AI Logistics Co-Pilot & Route Predictor
            </h2>
            <p className="text-[11px] text-slate-500">
              Heuristic delay risk scoring, transit reroute suggestions, and SLA assistant
            </p>
          </div>
        </div>

        {/* Tab Buttons */}
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={() => setActiveTab('assistant')}
            className={`rounded px-2.5 py-1 font-medium transition-colors ${
              activeTab === 'assistant'
                ? 'bg-indigo-600 text-white'
                : 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800'
            }`}
          >
            Co-Pilot Chat
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('predictor')}
            className={`rounded px-2.5 py-1 font-medium transition-colors ${
              activeTab === 'predictor'
                ? 'bg-indigo-600 text-white'
                : 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800'
            }`}
          >
            Delay Risk Predictor
          </button>
          <button
            type="button"
            onClick={() => {
              setActiveTab('audits');
              void loadAudits();
            }}
            className={`rounded px-2.5 py-1 font-medium transition-colors ${
              activeTab === 'audits'
                ? 'bg-indigo-600 text-white'
                : 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800'
            }`}
          >
            Audit Trail
          </button>
        </div>
      </div>

      {/* Tab 1: Assistant Chat */}
      {activeTab === 'assistant' && (
        <div className="space-y-3">
          <div className="flex gap-2">
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleAskAgent()}
              placeholder="E.g. What is the ETA for Colombo to Kandy under heavy rain? Or suggest reroute..."
              className="flex-1 rounded border border-slate-300 bg-white px-3 py-1.5 text-xs dark:border-slate-700 dark:bg-slate-900 dark:text-white"
            />
            <Button
              variant="primary"
              onClick={handleAskAgent}
              disabled={assistantLoading || !query.trim()}
              leftIcon={<Send className="h-3.5 w-3.5" />}
              className="h-8 text-xs"
            >
              Ask AI
            </Button>
          </div>

          {assistantResult && (
            <div className="rounded-lg border border-indigo-100 bg-indigo-50/60 p-3 dark:border-indigo-900/50 dark:bg-indigo-950/30">
              <div className="flex items-center justify-between text-[11px] text-indigo-700 dark:text-indigo-300">
                <span className="font-semibold">Intent: {assistantResult.detectedIntent}</span>
                <span>Confidence: {((assistantResult.confidence ?? 0.9) * 100).toFixed(0)}%</span>
              </div>
              <p className="mt-1.5 font-medium text-slate-800 dark:text-slate-200">
                {assistantResult.answer}
              </p>

              {assistantResult.suggestedActions && assistantResult.suggestedActions.length > 0 && (
                <div className="mt-2.5 flex flex-wrap items-center gap-1.5">
                  <span className="text-[10px] text-slate-500">Quick Actions:</span>
                  {assistantResult.suggestedActions.map((act) => (
                    <span
                      key={act}
                      className="rounded bg-indigo-200/70 px-1.5 py-0.5 text-[10px] font-bold text-indigo-900 dark:bg-indigo-900/80 dark:text-indigo-100"
                    >
                      {act}
                    </span>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* Tab 2: Delay Risk Predictor */}
      {activeTab === 'predictor' && (
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-5">
            <div>
              <label className="text-[11px] text-slate-500">Origin District</label>
              <select
                value={origin}
                onChange={(e) => setOrigin(e.target.value)}
                className="mt-1 w-full rounded border border-slate-300 bg-white px-2 py-1 text-xs dark:border-slate-700 dark:bg-slate-900 dark:text-white"
              >
                {SRI_LANKAN_DISTRICTS.map((d) => (
                  <option key={d} value={d}>{d}</option>
                ))}
              </select>
            </div>

            <div>
              <label className="text-[11px] text-slate-500">Destination</label>
              <select
                value={dest}
                onChange={(e) => setDest(e.target.value)}
                className="mt-1 w-full rounded border border-slate-300 bg-white px-2 py-1 text-xs dark:border-slate-700 dark:bg-slate-900 dark:text-white"
              >
                {SRI_LANKAN_DISTRICTS.map((d) => (
                  <option key={d} value={d}>{d}</option>
                ))}
              </select>
            </div>

            <div>
              <label className="text-[11px] text-slate-500">Delivery Speed</label>
              <select
                value={speed}
                onChange={(e) => setSpeed(e.target.value)}
                className="mt-1 w-full rounded border border-slate-300 bg-white px-2 py-1 text-xs dark:border-slate-700 dark:bg-slate-900 dark:text-white"
              >
                <option value="STANDARD">STANDARD</option>
                <option value="EXPRESS">EXPRESS</option>
                <option value="SAME_DAY">SAME_DAY</option>
              </select>
            </div>

            <div>
              <label className="text-[11px] text-slate-500">Weather Condition</label>
              <select
                value={weather}
                onChange={(e) => setWeather(e.target.value)}
                className="mt-1 w-full rounded border border-slate-300 bg-white px-2 py-1 text-xs dark:border-slate-700 dark:bg-slate-900 dark:text-white"
              >
                <option value="CLEAR">CLEAR (Sunny)</option>
                <option value="RAINY">RAINY (+20% delay)</option>
                <option value="MONSOON_STORM">MONSOON STORM (+50% delay)</option>
              </select>
            </div>

            <div>
              <label className="text-[11px] text-slate-500">Traffic Congestion</label>
              <select
                value={traffic}
                onChange={(e) => setTraffic(e.target.value)}
                className="mt-1 w-full rounded border border-slate-300 bg-white px-2 py-1 text-xs dark:border-slate-700 dark:bg-slate-900 dark:text-white"
              >
                <option value="LOW">LOW</option>
                <option value="MODERATE">MODERATE (+15%)</option>
                <option value="SEVERE">SEVERE (+35%)</option>
              </select>
            </div>
          </div>

          <div className="flex justify-end">
            <Button
              variant="primary"
              onClick={handlePredictDelay}
              disabled={predictorLoading}
              leftIcon={<Sparkles className="h-3.5 w-3.5" />}
              className="h-8 text-xs"
            >
              {predictorLoading ? 'Calculating Risk...' : 'Run Risk Analysis'}
            </Button>
          </div>

          {prediction && (
            <div className="rounded-lg border border-slate-200 bg-slate-50/80 p-3 dark:border-slate-800 dark:bg-slate-900/50">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <span
                    className={`rounded px-2 py-0.5 font-bold ${
                      prediction.riskLevel === 'CRITICAL'
                        ? 'bg-rose-100 text-rose-800 dark:bg-rose-950 dark:text-rose-300'
                        : prediction.riskLevel === 'HIGH'
                          ? 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300'
                          : 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300'
                    }`}
                  >
                    Risk Level: {prediction.riskLevel} ({prediction.riskScore}%)
                  </span>
                  <span className="text-slate-500">
                    Delay Variance: <strong>+{prediction.predictedDelayMinutes} mins</strong>
                  </span>
                </div>

                <span className="font-mono text-[11px] text-slate-500">
                  Mitigation: <strong className="text-indigo-600 dark:text-indigo-400">{prediction.recommendedAction}</strong>
                </span>
              </div>

              <p className="mt-2 text-slate-600 dark:text-slate-400">
                {prediction.reasoning}
              </p>
            </div>
          )}
        </div>
      )}

      {/* Tab 3: Audits Trail */}
      {activeTab === 'audits' && (
        <div className="space-y-2">
          {auditsLoading ? (
            <p className="py-4 text-center text-slate-500">Loading AI audit logs...</p>
          ) : audits.length === 0 ? (
            <p className="py-4 text-center text-slate-500">No recent AI tool invocations logged yet.</p>
          ) : (
            <div className="space-y-1.5">
              {audits.map((a) => (
                <div
                  key={a.auditId}
                  className="flex items-center justify-between rounded border border-slate-200 bg-white px-3 py-2 text-[11px] dark:border-slate-800 dark:bg-slate-900"
                >
                  <div className="space-y-0.5">
                    <span className="font-mono font-bold text-indigo-600 dark:text-indigo-400">{a.toolName}</span>
                    <p className="text-slate-600 dark:text-slate-400">{a.promptOrQuery}</p>
                  </div>
                  <div className="text-right text-slate-500">
                    <span>{a.executionTimeMs} ms</span>
                    <span className="ml-2 font-semibold text-emerald-600 dark:text-emerald-400">SUCCESS</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </SurfaceCard>
  );
}
