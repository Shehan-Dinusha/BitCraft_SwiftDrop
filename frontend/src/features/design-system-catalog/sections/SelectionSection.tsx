import { useState } from 'react';
import { Package, Zap } from 'lucide-react';
import {
  Checkbox,
  ChoiceChip,
  Radio,
  RadioCard,
  SpecialField,
  Textarea,
  Toggle,
  type DimensionsValue
} from '@/design-system';
import { CatalogRow, DemoGrid, DemoRow } from '../components/catalog-kit';

const parcelTypes = ['Documents', 'Small box', 'Medium box', 'Fragile', 'Other'];

export function SelectionSection() {
  const [chip, setChip] = useState('Small box');
  const [speed, setSpeed] = useState('standard');
  const [postal, setPostal] = useState('00300');
  const [weight, setWeight] = useState('2.4');
  const [size, setSize] = useState<DimensionsValue>({ length: '30', width: '20', height: '15' });

  return (
    <>
      <CatalogRow label="Checkbox" hint="Off, on, mixed, and disabled">
        <DemoRow>
          <Checkbox label="Off" />
          <Checkbox label="On" defaultChecked />
          <Checkbox label="Mixed" mixed />
          <Checkbox label="Disabled" disabled />
          <Checkbox label="Disabled on" disabled defaultChecked />
        </DemoRow>
      </CatalogRow>

      <CatalogRow label="Radio and toggle">
        <DemoRow>
          <Radio name="demo-radio" label="Standard" defaultChecked />
          <Radio name="demo-radio" label="Express" />
          <Radio name="demo-radio-disabled" label="Disabled" disabled />
          <Toggle label="Notify me by email" />
          <Toggle label="On" defaultChecked />
          <Toggle label="Disabled" disabled defaultChecked />
        </DemoRow>
      </CatalogRow>

      <CatalogRow label="Choice chips" hint="Single select; exposes aria-pressed">
        <DemoRow>
          {parcelTypes.map((type) => (
            <ChoiceChip key={type} selected={chip === type} onClick={() => setChip(type)}>
              {type}
            </ChoiceChip>
          ))}
          <ChoiceChip disabled>Unavailable</ChoiceChip>
        </DemoRow>
      </CatalogRow>

      <CatalogRow label="Radio cards" hint="How fast? Standard / Express">
        <DemoGrid>
          <RadioCard
            name="speed"
            value="standard"
            title="Standard"
            caption="Arrives in 2–3 days"
            fee="450.00"
            icon={<Package />}
            checked={speed === 'standard'}
            onChange={() => setSpeed('standard')}
          />
          <RadioCard
            name="speed"
            value="express"
            title="Express"
            caption="Arrives next day"
            fee="720.00"
            icon={<Zap />}
            checked={speed === 'express'}
            onChange={() => setSpeed('express')}
          />
        </DemoGrid>
      </CatalogRow>

      <CatalogRow label="Textarea" hint="Counter shows when maxLength is set">
        <DemoGrid>
          <Textarea
            label="Delivery note"
            helper="Optional. Shown to the courier."
            maxLength={300}
            defaultValue="Leave at the gate if nobody answers."
          />
          <Textarea
            label="Failure reason"
            required
            maxLength={300}
            error="Add a reason. It is sent to the customer."
          />
        </DemoGrid>
      </CatalogRow>

      <CatalogRow label="Special fields" hint="Postal code, weight, dimensions">
        <DemoGrid>
          <SpecialField
            field="postal-code"
            label="Postal code"
            required
            value={postal}
            onChange={setPostal}
            town={postal.length === 5 ? 'Colombo 03' : undefined}
            helper="5 digits. The town fills in from the code."
          />
          <SpecialField
            field="weight"
            label="Weight"
            required
            value={weight}
            onChange={setWeight}
            helper="Up to 30 kg. One decimal place."
          />
          <SpecialField
            field="dimensions"
            label="Size (L × W × H)"
            required
            value={size}
            onChange={setSize}
            helper="Whole centimetres. Used for the fee."
          />
        </DemoGrid>
      </CatalogRow>
    </>
  );
}
