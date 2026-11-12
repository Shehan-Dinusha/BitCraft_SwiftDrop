import { useState } from 'react';
import {
  analyticsAiApi,
  type DelayRiskPredictionResponse,
  type AiAudit
} from '@/services/api/analyticsAiApi';
import { Button } from '@/design-system/primitives/Button';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { Bot, Sparkles } from 'lucide-react';

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
  const [activeTab, setActiveTab] = useState<'predictor' | 'audits'>('predictor');

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
      <div className="flex flex-wrap items-center justify-between border-b border-slate-200 pb-3">
        <div className="flex items-center gap-2">
          <div className="grid h-7 w-7 place-items-center rounded-lg bg-indigo-600 text-white">
            <Bot className="h-4 w-4" />
          </div>
          <div>
            <h2 className="text-sm font-bold text-slate-900">Operational Delay Risk & Audits</h2>
            <p className="text-[11px] text-slate-500">
              Operational delay risk scoring and safe tool audit records
            </p>
          </div>
        </div>

        {/* Tab Buttons */}
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={() => setActiveTab('predictor')}
            className={`rounded px-2.5 py-1 font-medium transition-colors ${
              activeTab === 'predictor'
                ? 'bg-indigo-600 text-white'
                : 'text-slate-600 hover:bg-slate-100'
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
                : 'text-slate-600 hover:bg-slate-100'
            }`}
          >
            Audit Trail
          </button>
        </div>
      </div>

      {/* Tab 2: Delay Risk Predictor */}
      {activeTab === 'predictor' && (
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-5">
            <div>
              <label className="text-[11px] text-slate-500">Origin District</label>
              <select
                value={origin}
                onChange={(e) => setOrigin(e.target.value)}
                className="mt-1 w-full rounded border border-slate-300 bg-white px-2 py-1 text-xs"
              >
                {SRI_LANKAN_DISTRICTS.map((d) => (
                  <option key={d} value={d}>
                    {d}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="text-[11px] text-slate-500">Destination</label>
              <select
                value={dest}
                onChange={(e) => setDest(e.target.value)}
                className="mt-1 w-full rounded border border-slate-300 bg-white px-2 py-1 text-xs"
              >
                {SRI_LANKAN_DISTRICTS.map((d) => (
                  <option key={d} value={d}>
                    {d}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="text-[11px] text-slate-500">Delivery Speed</label>
              <select
                value={speed}
                onChange={(e) => setSpeed(e.target.value)}
                className="mt-1 w-full rounded border border-slate-300 bg-white px-2 py-1 text-xs"
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
                className="mt-1 w-full rounded border border-slate-300 bg-white px-2 py-1 text-xs"
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
                className="mt-1 w-full rounded border border-slate-300 bg-white px-2 py-1 text-xs"
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
            <div className="rounded-lg border border-slate-200 bg-slate-50/80 p-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <span
                    className={`rounded px-2 py-0.5 font-bold ${
                      prediction.riskLevel === 'CRITICAL'
                        ? 'bg-rose-100 text-rose-800'
                        : prediction.riskLevel === 'HIGH'
                          ? 'bg-amber-100 text-amber-800'
                          : 'bg-emerald-100 text-emerald-800'
                    }`}
                  >
                    Risk Level: {prediction.riskLevel} ({prediction.riskScore}%)
                  </span>
                  <span className="text-slate-500">
                    Delay Variance: <strong>+{prediction.predictedDelayMinutes} mins</strong>
                  </span>
                </div>

                <span className="font-mono text-[11px] text-slate-500">
                  Mitigation:{' '}
                  <strong className="text-indigo-600">{prediction.recommendedAction}</strong>
                </span>
              </div>

              <p className="mt-2 text-slate-600">{prediction.reasoning}</p>
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
            <p className="py-4 text-center text-slate-500">
              No recent AI tool invocations logged yet.
            </p>
          ) : (
            <div className="space-y-1.5">
              {audits.map((a) => (
                <div
                  key={a.auditId}
                  className="flex items-center justify-between rounded border border-slate-200 bg-white px-3 py-2 text-[11px]"
                >
                  <div className="space-y-0.5">
                    <span className="font-mono font-bold text-indigo-600">{a.toolName}</span>
                    <p className="text-slate-600">{a.outputResult ?? 'No outcome recorded'}</p>
                  </div>
                  <div className="text-right text-slate-500">
                    <span>{a.executionTimeMs} ms</span>
                    <span className="ml-2 font-semibold">
                      {a.success ? 'SUCCESS' : 'NOT COMPLETED'}
                    </span>
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
