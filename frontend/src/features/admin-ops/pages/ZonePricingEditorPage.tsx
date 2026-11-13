import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus } from 'lucide-react';
import {
  Button,
  Field,
  HighDensityDataGrid,
  Modal,
  SelectDropdown,
  Skeleton,
  StatusBanner,
  SurfaceCard
} from '@/design-system';
import {
  pricingApi,
  readPricingApiError,
  type SriLankanDistrict,
  type ZoneRequest,
  type ZoneResponse
} from '@/services/api/pricingApi';

const ZONES_KEY = ['pricing', 'zones'] as const;
const MAPPINGS_KEY = ['pricing', 'district-mappings'] as const;

const TIER_OPTIONS = ['METRO', 'URBAN', 'REGIONAL', 'REMOTE'].map((tier) => ({
  value: tier,
  label: tier
}));

interface ZoneForm {
  zoneCode: string;
  zoneName: string;
  tier: string;
  baseFee: string;
  perKgFee: string;
  expressMultiplier: string;
  sameDayMultiplier: string;
}

const emptyForm: ZoneForm = {
  zoneCode: '',
  zoneName: '',
  tier: 'URBAN',
  baseFee: '',
  perKgFee: '',
  expressMultiplier: '1.50',
  sameDayMultiplier: '2.00'
};

type FormErrors = Partial<Record<keyof ZoneForm, string>>;

function toForm(zone: ZoneResponse): ZoneForm {
  return {
    zoneCode: zone.zoneCode ?? '',
    zoneName: zone.zoneName ?? '',
    tier: zone.tier ?? 'URBAN',
    baseFee: String(zone.baseFee ?? ''),
    perKgFee: String(zone.perKgFee ?? ''),
    expressMultiplier: String(zone.expressMultiplier ?? ''),
    sameDayMultiplier: String(zone.sameDayMultiplier ?? '')
  };
}

function validate(form: ZoneForm, isNew: boolean): FormErrors {
  const errors: FormErrors = {};
  if (isNew && !form.zoneCode.trim()) errors.zoneCode = 'Zone code is required';
  if (!form.zoneName.trim()) errors.zoneName = 'Zone name is required';
  const nonNegative: (keyof ZoneForm)[] = ['baseFee', 'perKgFee'];
  nonNegative.forEach((key) => {
    const value = Number(form[key]);
    if (form[key].trim() === '' || Number.isNaN(value) || value < 0) {
      errors[key] = 'Enter an amount of 0 or more';
    }
  });
  const multipliers: (keyof ZoneForm)[] = ['expressMultiplier', 'sameDayMultiplier'];
  multipliers.forEach((key) => {
    const value = Number(form[key]);
    if (form[key].trim() === '' || Number.isNaN(value) || value < 1) {
      errors[key] = 'Enter a multiplier of 1.00 or more';
    }
  });
  return errors;
}

function toRequest(form: ZoneForm): ZoneRequest {
  return {
    zoneCode: form.zoneCode.trim(),
    zoneName: form.zoneName.trim(),
    tier: form.tier,
    baseFee: Number(form.baseFee),
    perKgFee: Number(form.perKgFee),
    expressMultiplier: Number(form.expressMultiplier),
    sameDayMultiplier: Number(form.sameDayMultiplier)
  };
}

interface ZoneRow {
  id: string;
  zoneCode: string;
  zoneName: string;
  tier: string;
  baseFee: number;
  perKgFee: number;
  expressMultiplier: number;
  sameDayMultiplier: number;
  actions: string;
  zone: ZoneResponse;
}

interface MappingRow {
  id: string;
  district: string;
  zoneCode: string;
  actions: string;
}

export function ZonePricingEditorPage() {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<ZoneResponse | 'new' | null>(null);
  const [form, setForm] = useState<ZoneForm>(emptyForm);
  const [errors, setErrors] = useState<FormErrors>({});
  const [deleting, setDeleting] = useState<ZoneResponse | null>(null);
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'danger'; text: string } | null>(
    null
  );

  const zonesQuery = useQuery({ queryKey: ZONES_KEY, queryFn: pricingApi.listZones });
  const mappingsQuery = useQuery({
    queryKey: MAPPINGS_KEY,
    queryFn: pricingApi.listDistrictMappings
  });

  const fail = (error: unknown, fallback: string) => {
    const { status, message } = readPricingApiError(error);
    setFeedback({
      tone: 'danger',
      text: status === 403 ? 'Only dispatchers and system admins can change zones.' : (message ?? fallback)
    });
  };

  const saveZone = useMutation({
    mutationFn: async () => {
      const request = toRequest(form);
      return editing === 'new'
        ? pricingApi.createZone(request)
        : pricingApi.updateZone((editing as ZoneResponse).zoneCode ?? '', request);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ZONES_KEY });
      setFeedback({
        tone: 'success',
        text: editing === 'new' ? 'Zone created.' : 'Zone updated.'
      });
      setEditing(null);
    },
    onError: (error) => fail(error, 'Could not save the zone.')
  });

  const removeZone = useMutation({
    mutationFn: (zoneCode: string) => pricingApi.deleteZone(zoneCode),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ZONES_KEY });
      setFeedback({ tone: 'success', text: 'Zone deleted.' });
      setDeleting(null);
    },
    onError: (error) => {
      fail(error, 'Could not delete the zone.');
      setDeleting(null);
    }
  });

  const assignDistrict = useMutation({
    mutationFn: (input: { district: SriLankanDistrict; zoneCode: string }) =>
      pricingApi.assignDistrict(input.district, { zoneCode: input.zoneCode }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: MAPPINGS_KEY });
      setFeedback({ tone: 'success', text: 'District mapping updated.' });
    },
    onError: (error) => fail(error, 'Could not update the district mapping.')
  });

  const openCreate = () => {
    setForm(emptyForm);
    setErrors({});
    setEditing('new');
  };

  const openEdit = (zone: ZoneResponse) => {
    setForm(toForm(zone));
    setErrors({});
    setEditing(zone);
  };

  const submit = () => {
    const found = validate(form, editing === 'new');
    setErrors(found);
    if (Object.keys(found).length === 0) saveZone.mutate();
  };

  const setField = (key: keyof ZoneForm) => (event: { target: { value: string } }) =>
    setForm((current) => ({ ...current, [key]: event.target.value }));

  const zones = zonesQuery.data ?? [];
    const zoneRows: ZoneRow[] = zones.map((zone) => ({
      id: zone.zoneCode ?? String(zone.id),
      zoneCode: zone.zoneCode ?? '',
      zoneName: zone.zoneName ?? '',
      tier: zone.tier ?? '',
      baseFee: zone.baseFee ?? 0,
      perKgFee: zone.perKgFee ?? 0,
      expressMultiplier: zone.expressMultiplier ?? 0,
      sameDayMultiplier: zone.sameDayMultiplier ?? 0,
      actions: '',
      zone
    }));
    const mappingRows: MappingRow[] = (mappingsQuery.data ?? []).map((mapping) => ({
      id: String(mapping.district),
      district: String(mapping.district),
      zoneCode: mapping.zoneCode ?? '',
      actions: ''
    }));
  const zoneOptions = zones.map((zone) => ({
    value: zone.zoneCode ?? '',
    label: `${zone.zoneCode} (${zone.zoneName})`
  }));

  return (
    <main className="flex flex-col gap-4 p-4">
      <div className="flex items-center justify-between gap-2">
        <h1 className="text-h3 text-primary">Zone pricing</h1>
        <Button variant="primary" leftIcon={<Plus size={16} />} onClick={openCreate}>
          New zone
        </Button>
      </div>

      {feedback ? (
        <StatusBanner tone={feedback.tone} title={feedback.tone === 'success' ? 'Done' : 'Something went wrong'}>
          {feedback.text}
        </StatusBanner>
      ) : null}

      <SurfaceCard title="Zones">
        {zonesQuery.isLoading ? (
          <div aria-busy="true">
            <Skeleton preset="table-row" />
          </div>
        ) : zonesQuery.isError ? (
          <StatusBanner tone="danger" title="Could not load zones">
            {readPricingApiError(zonesQuery.error).message ?? 'Please try again.'}
          </StatusBanner>
        ) : (
          <HighDensityDataGrid<ZoneRow>
            rows={zoneRows}
            columns={[
              { key: 'zoneCode', label: 'Code' },
              { key: 'zoneName', label: 'Name' },
              { key: 'tier', label: 'Tier' },
              { key: 'baseFee', label: 'Base fee (LKR)', align: 'right' },
              { key: 'perKgFee', label: 'Per kg (LKR)', align: 'right' },
              { key: 'expressMultiplier', label: 'Express x', align: 'right' },
              { key: 'sameDayMultiplier', label: 'Same-day x', align: 'right' },
              {
                key: 'actions',
                label: 'Actions',
                render: (row: ZoneRow) => (
                  <span className="flex gap-1">
                    <Button variant="ghost" onClick={() => openEdit(row.zone)}>
                      Edit
                    </Button>
                    <Button variant="ghost" onClick={() => setDeleting(row.zone)}>
                      Delete
                    </Button>
                  </span>
                )
              }
            ]}
          />
        )}
      </SurfaceCard>

      <SurfaceCard title="District mapping">
        {mappingsQuery.isLoading ? (
          <div aria-busy="true">
            <Skeleton preset="table-row" />
          </div>
        ) : (
          <HighDensityDataGrid<MappingRow>
            rows={mappingRows}
            columns={[
              { key: 'district', label: 'District' },
              {
                key: 'actions',
                label: 'Zone',
                render: (row: MappingRow) => (
                  <SelectDropdown
                    label={`Zone for ${row.district}`}
                    options={zoneOptions}
                    value={row.zoneCode}
                    onChange={(zoneCode: string) =>
                      assignDistrict.mutate({
                        district: row.district as SriLankanDistrict,
                        zoneCode
                      })
                    }
                  />
                )
              }
            ]}
          />
        )}
      </SurfaceCard>

      <Modal
        open={editing !== null}
        onClose={() => setEditing(null)}
        title={editing === 'new' ? 'New zone' : 'Edit zone'}
        description="Fees are in LKR. Multipliers apply to the subtotal for Express and Same-day delivery."
        footer={
          <span className="flex gap-2">
            <Button variant="ghost" onClick={() => setEditing(null)}>
              Cancel
            </Button>
            <Button variant="primary" loading={saveZone.isPending} onClick={submit}>
              Save
            </Button>
          </span>
        }
      >
        <div className="flex flex-col gap-3">
          <Field
            label="Zone code"
            required
            value={form.zoneCode}
            onChange={setField('zoneCode')}
            error={errors.zoneCode}
            readOnly={editing !== 'new'}
            hint={editing !== 'new' ? 'The zone code cannot be changed.' : undefined}
          />
          <Field
            label="Zone name"
            required
            value={form.zoneName}
            onChange={setField('zoneName')}
            error={errors.zoneName}
          />
          <SelectDropdown
            label="Tier"
            options={TIER_OPTIONS}
            value={form.tier}
            onChange={(tier: string) => setForm((current) => ({ ...current, tier }))}
          />
          <Field
            label="Base fee"
            required
            value={form.baseFee}
            onChange={setField('baseFee')}
            error={errors.baseFee}
            inputMode="decimal"
          />
          <Field
            label="Fee per extra kg"
            required
            value={form.perKgFee}
            onChange={setField('perKgFee')}
            error={errors.perKgFee}
            inputMode="decimal"
          />
          <Field
            label="Express multiplier"
            required
            value={form.expressMultiplier}
            onChange={setField('expressMultiplier')}
            error={errors.expressMultiplier}
            inputMode="decimal"
          />
          <Field
            label="Same-day multiplier"
            required
            value={form.sameDayMultiplier}
            onChange={setField('sameDayMultiplier')}
            error={errors.sameDayMultiplier}
            inputMode="decimal"
          />
        </div>
      </Modal>

      <Modal
        open={deleting !== null}
        onClose={() => setDeleting(null)}
        title="Delete zone"
        description={`Delete ${deleting?.zoneCode ?? ''}? A zone that still has districts mapped to it cannot be deleted.`}
        footer={
          <span className="flex gap-2">
            <Button variant="ghost" onClick={() => setDeleting(null)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              loading={removeZone.isPending}
              onClick={() => deleting?.zoneCode && removeZone.mutate(deleting.zoneCode)}
            >
              Delete
            </Button>
          </span>
        }
      >
        <p className="text-body text-secondary">This cannot be undone.</p>
      </Modal>
    </main>
  );
}

export default ZonePricingEditorPage;
