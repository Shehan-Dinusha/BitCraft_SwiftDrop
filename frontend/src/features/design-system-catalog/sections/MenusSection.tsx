import { User } from 'lucide-react';
import {
  DropdownItem,
  DropdownMenu,
  SelectDropdown,
  type SelectDropdownState
} from '@/design-system';
import { CatalogRow, DemoGrid } from '../components/catalog-kit';

const couriers = [
  { value: 'amara', label: 'Amara Okafor', description: 'Zone 4' },
  { value: 'jon', label: 'Jon Park', description: 'Zone 2' },
  { value: 'ravi', label: 'Ravi Menon', status: 'warning' as const, description: 'SLA at risk' },
  { value: 'nina', label: 'Nina Alvarez', status: 'success' as const },
  { value: 'sam', label: 'Sam Idris', disabled: true, description: 'Off shift' }
];

const selectStates: SelectDropdownState[] = ['default', 'focused', 'error', 'disabled'];

export function MenusSection() {
  return (
    <>
      <CatalogRow label="Select states" hint="`open` is shown separately below">
        <DemoGrid>
          {selectStates.map((state) => (
            <SelectDropdown
              key={state}
              label={state}
              state={state}
              options={couriers}
              value={state === 'default' ? undefined : 'amara'}
              placeholder="Assign courier"
            />
          ))}
        </DemoGrid>
      </CatalogRow>

      <CatalogRow label="Select open with search">
        <div className="relative min-h-80">
          <SelectDropdown
            label="Assign courier"
            state="open"
            searchable
            value="amara"
            options={couriers}
          />
        </div>
      </CatalogRow>

      <CatalogRow
        label="Dropdown rows"
        hint="default, selected, active, disabled, status dots, icons"
      >
        <div className="relative min-h-80">
          <DropdownMenu open label="Courier" density="compact">
            <DropdownItem>Default row</DropdownItem>
            <DropdownItem selected>Selected row</DropdownItem>
            <DropdownItem active>Active row</DropdownItem>
            <DropdownItem disabled>Disabled row</DropdownItem>
            <DropdownItem status="warning" description="SLA at risk">
              Status dot
            </DropdownItem>
            <DropdownItem icon={<User />} description="Zone 4">
              With icon and description
            </DropdownItem>
          </DropdownMenu>
        </div>
      </CatalogRow>

      <CatalogRow label="Dropdown with search and footer">
        <div className="relative min-h-80">
          <DropdownMenu
            open
            searchable
            searchPlaceholder="Find courier"
            label="Courier"
            footer="5 couriers on shift"
          >
            {couriers.map((courier) => (
              <DropdownItem
                key={courier.value}
                selected={courier.value === 'amara'}
                disabled={courier.disabled}
                status={courier.status}
                description={courier.description}
              >
                {courier.label}
              </DropdownItem>
            ))}
          </DropdownMenu>
        </div>
      </CatalogRow>
    </>
  );
}
