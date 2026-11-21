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
      <div className="flex flex-wrap items-center justify-between border-b border-border-subtle pb-3">
        <div className="flex items-center gap-2">
          <div className="grid h-7 w-7 place-items-center rounded-lg bg-brand-primary text-brand-on">
            <Bot className="h-4 w-4" />
          </div>
          <div>
            <h2 className="text-sm font-bold text-primary">Delay risk</h2>
          </div>
        </div>

        {/* Tab Buttons */}
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={() => setActiveTab('predictor')}
            className={`rounded px-2.5 py-1 font-medium transition-colors ${
              activeTab === 'predictor'
                ? 'bg-brand-primary text-brand-on'
                : 'text-secondary hover:bg-canvas'
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
                ? 'bg-brand-primary text-brand-on'
                : 'text-secondary hover:bg-canvas'
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
              <label htmlFor="ai-origin-district" className="text-[11px] text-secondary">Origin District</label>
              <select id="ai-origin-district"
                value={origin}
                onChange={(e) => setOrigin(e.target.value)}
                className="mt-1 w-full rounded border border-border-subtle bg-card px-2 py-1 text-xs"
              >
                {SRI_LANKAN_DISTRICTS.map((d) => (
                  <option key={d} value={d}>
                    {d}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label htmlFor="ai-destination-district" className="text-[11px] text-secondary">Destination</label>
              <select id="ai-destination-district"
                value={dest}
                onChange={(e) => setDest(e.target.value)}
                className="mt-1 w-full rounded border border-border-subtle bg-card px-2 py-1 text-xs"
              >
                {SRI_LANKAN_DISTRICTS.map((d) => (
                  <option key={d} value={d}>
                    {d}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label htmlFor="ai-delivery-speed" className="text-[11px] text-secondary">Delivery Speed</label>
              <select id="ai-delivery-speed"
                value={speed}
                onChange={(e) => setSpeed(e.target.value)}
                className="mt-1 w-full rounded border border-border-subtle bg-card px-2 py-1 text-xs"
              >
                <option value="STANDARD">STANDARD</option>
                <option value="EXPRESS">EXPRESS</option>
                <option value="SAME_DAY">SAME_DAY</option>
              </select>
            </div>

            <div>
              <label htmlFor="ai-weather" className="text-[11px] text-secondary">Weather Condition</label>
              <select id="ai-weather"
                value={weather}
                onChange={(e) => setWeather(e.target.value)}
                className="mt-1 w-full rounded border border-border-subtle bg-card px-2 py-1 text-xs"
              >
                <option value="CLEAR">CLEAR (Sunny)</option>
                <option value="RAINY">RAINY (+20% delay)</option>
                <option value="MONSOON_STORM">MONSOON STORM (+50% delay)</option>
              </select>
            </div>

            <div>
              <label htmlFor="ai-traffic" className="text-[11px] text-secondary">Traffic Congestion</label>
              <select id="ai-traffic"
                value={traffic}
                onChange={(e) => setTraffic(e.target.value)}
                className="mt-1 w-full rounded border border-border-subtle bg-card px-2 py-1 text-xs"
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
            <div className="rounded-lg border border-border-subtle bg-canvas p-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <span
                    className={`rounded px-2 py-0.5 font-bold ${
                      prediction.riskLevel === 'CRITICAL'
                        ? 'bg-status-danger-bg text-status-danger-fg'
                        : prediction.riskLevel === 'HIGH'
                          ? 'bg-status-warning-bg text-status-warning-fg'
                          : 'bg-status-success-bg text-status-success-fg'
                    }`}
                  >
                    Risk Level: {prediction.riskLevel} ({prediction.riskScore}%)
                  </span>
                  <span className="text-secondary">
                    Delay Variance: <strong>+{prediction.predictedDelayMinutes} mins</strong>
                  </span>
                </div>

                <span className="font-mono text-[11px] text-secondary">
                  Mitigation:{' '}
                  <strong className="text-brand-primary">{prediction.recommendedAction}</strong>
                </span>
              </div>

              <p className="mt-2 text-secondary">{prediction.reasoning}</p>
            </div>
          )}
        </div>
      )}

      {/* Tab 3: Audits Trail */}
      {activeTab === 'audits' && (
        <div className="space-y-2">
          {auditsLoading ? (
            <p className="py-4 text-center text-secondary">Loading AI audit logs...</p>
          ) : audits.length === 0 ? (
            <p className="py-4 text-center text-secondary">
              No AI activity yet.
            </p>
          ) : (
            <div className="space-y-1.5">
              {audits.map((a) => (
                <div
                  key={a.auditId}
                  className="flex items-center justify-between rounded border border-border-subtle bg-card px-3 py-2 text-[11px]"
                >
                  <div className="space-y-0.5">
                    <span className="font-mono font-bold text-brand-primary">{a.toolName}</span>
                    <p className="text-secondary">{a.outputResult ?? 'No outcome recorded'}</p>
                  </div>
                  <div className="text-right text-secondary">
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
