import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bike, Car, CheckCircle2, Truck } from 'lucide-react';
import { Alert } from '@/design-system/components/Alert/Alert';
import { RadioCard } from '@/design-system/components/RadioCard/RadioCard';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { Button } from '@/design-system/primitives/Button';
import { Field } from '@/design-system/primitives/Field';
import {
  dispatchApi,
  type CourierProfileResponse,
  type VehicleType
} from '@/services/api/dispatchApi';
import { parseApiError } from '@/services/api/apiError';
import { VEHICLE_TYPES, isValidPlate, normalisePlate, vehicleLabel } from '@/utils/vehicles';

const VEHICLE_ICONS: Record<VehicleType, typeof Bike> = {
  MOTORCYCLE: Bike,
  THREE_WHEELER: Car,
  VAN: Truck,
  LORRY: Truck
};

/**
 * A new courier registers their own vehicle once, right after accepting their invite. Afterwards the
 * page shows the vehicle on record: only the dispatcher can change it.
 */
export function CourierVehicleSetupPage() {
  const navigate = useNavigate();
  const [profile, setProfile] = useState<CourierProfileResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [vehicleType, setVehicleType] = useState<VehicleType>('MOTORCYCLE');
  const [plateNumber, setPlateNumber] = useState('');
  const [vehicleModel, setVehicleModel] = useState('');
  const [vehicleColour, setVehicleColour] = useState('');
  const [licenceNumber, setLicenceNumber] = useState('');
  const [plateError, setPlateError] = useState<string>();
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    dispatchApi
      .getMyProfile()
      .then(setProfile)
      .catch(() => setError('Could not load your vehicle details. Pull down to retry.'))
      .finally(() => setLoading(false));
  }, []);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    if (!isValidPlate(plateNumber)) {
      setPlateError('Enter the plate as shown on the vehicle, for example WP BAB-1234.');
      return;
    }
    setSaving(true);
    try {
      const saved = await dispatchApi.registerMyProfile({
        vehicleType,
        plateNumber: normalisePlate(plateNumber),
        vehicleModel: vehicleModel.trim(),
        vehicleColour: vehicleColour.trim(),
        licenceNumber: licenceNumber.trim()
      });
      setProfile(saved);
      navigate('/courier', { replace: true, state: { notice: 'Vehicle saved. You can start your shift.' } });
    } catch (requestError) {
      setError(parseApiError(requestError).message ?? 'Could not save your vehicle. Try again.');
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <main className="mx-auto max-w-xl p-4 md:p-6" data-density="kinetic">
        <p role="status" className="py-16 text-center text-body text-secondary">
          Loading your vehicle…
        </p>
      </main>
    );
  }

  if (profile) {
    const Icon = VEHICLE_ICONS[profile.vehicleType ?? 'MOTORCYCLE'];
    return (
      <main className="mx-auto max-w-xl space-y-5 p-4 md:p-6" data-density="kinetic">
        <header>
          <h1 className="text-2xl font-bold tracking-tight text-primary">My vehicle</h1>
          <p className="mt-1 text-body text-secondary">
            This is the vehicle on record for your deliveries. To change it, ask your dispatcher.
          </p>
        </header>
        <SurfaceCard className="p-5">
          <div className="flex items-center gap-4">
            <span className="flex size-14 items-center justify-center rounded-xl bg-brand-subtle text-brand-primary">
              <Icon className="size-7" />
            </span>
            <div>
              <p className="font-mono text-xl font-bold tabular-nums text-primary">{profile.plateNumber}</p>
              <p className="text-body text-secondary">
                {vehicleLabel(profile.vehicleType)} · {profile.vehicleModel} · {profile.vehicleColour}
              </p>
            </div>
          </div>
          <dl className="mt-5 grid grid-cols-2 gap-4 border-t border-border-subtle pt-4 text-body">
            <div>
              <dt className="text-caption text-secondary">Parcels at once</dt>
              <dd className="font-semibold tabular-nums text-primary">{profile.maxCapacity}</dd>
            </div>
            <div>
              <dt className="text-caption text-secondary">Driving licence</dt>
              <dd className="font-mono font-semibold text-primary">{profile.licenceNumber}</dd>
            </div>
          </dl>
        </SurfaceCard>
        <Button className="min-h-target w-full" onClick={() => navigate('/courier')}>
          Go to my shift
        </Button>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-xl space-y-5 p-4 pb-28 md:p-6 md:pb-28" data-density="kinetic">
      <header>
        <h1 className="text-2xl font-bold tracking-tight text-primary">Add your vehicle</h1>
        <p className="mt-1 text-body text-secondary">
          Tell us the vehicle you deliver with. You only do this once; your dispatcher checks it against your
          documents.
        </p>
      </header>

      {error ? (
        <Alert tone="danger" title="Vehicle not saved">
          {error}
        </Alert>
      ) : null}

      <form id="vehicle-form" className="space-y-5" onSubmit={onSubmit}>
        <fieldset className="space-y-2">
          <legend className="mb-2 font-semibold text-primary">Vehicle type</legend>
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
            {VEHICLE_TYPES.map((vehicle) => {
              const Icon = VEHICLE_ICONS[vehicle.value];
              return (
                <RadioCard
                  key={vehicle.value}
                  name="vehicleType"
                  value={vehicle.value}
                  checked={vehicleType === vehicle.value}
                  onChange={() => setVehicleType(vehicle.value)}
                  title={vehicle.label}
                  caption={`Up to ${vehicle.capacity} parcels at once`}
                  icon={<Icon />}
                  className="min-h-target"
                />
              );
            })}
          </div>
        </fieldset>

        <Field
          label="Number plate"
          required
          value={plateNumber}
          onChange={(event) => {
            setPlateNumber(event.target.value);
            setPlateError(undefined);
          }}
          onBlur={() => plateNumber && setPlateNumber(normalisePlate(plateNumber))}
          placeholder="WP BAB-1234"
          autoCapitalize="characters"
          maxLength={20}
          hint="As shown on the vehicle"
          error={plateError}
          inputClassName="font-mono uppercase"
        />
        <Field
          label="Make and model"
          required
          value={vehicleModel}
          onChange={(event) => setVehicleModel(event.target.value)}
          placeholder="Honda Dio"
          maxLength={64}
        />
        <Field
          label="Colour"
          required
          value={vehicleColour}
          onChange={(event) => setVehicleColour(event.target.value)}
          placeholder="Black"
          maxLength={32}
        />
        <Field
          label="Driving licence number"
          required
          value={licenceNumber}
          onChange={(event) => setLicenceNumber(event.target.value.toUpperCase())}
          placeholder="B1234567"
          autoCapitalize="characters"
          maxLength={20}
          inputClassName="font-mono"
        />
      </form>

      <div className="fixed inset-x-0 bottom-0 z-20 border-t border-border-subtle bg-card p-4 shadow-ambient md:sticky md:bottom-4 md:rounded-xl md:border">
        <Button
          type="submit"
          form="vehicle-form"
          className="min-h-target w-full"
          loading={saving}
          loadingLabel="Saving vehicle"
          leftIcon={<CheckCircle2 />}
        >
          Save vehicle
        </Button>
      </div>
    </main>
  );
}

export default CourierVehicleSetupPage;
