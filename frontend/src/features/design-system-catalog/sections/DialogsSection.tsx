import { Button, FsmStatusBadge, ModalDrawerShell, type ModalDrawerSize } from '@/design-system';
import { CatalogRow, DemoGrid } from '../components/catalog-kit';

const sizes: ModalDrawerSize[] = ['sm', 'md', 'lg'];

const body = (
  <div className="space-y-3">
    <p className="text-sm text-secondary">
      Choose the courier for parcel SD-4821-XZ. Only couriers on shift in the destination zone are
      listed.
    </p>
    <SurfacePreviewList />
  </div>
);

const footer = (
  <div className="flex justify-end gap-2">
    <Button variant="secondary">Cancel</Button>
    <Button>Assign</Button>
  </div>
);

function SurfacePreviewList() {
  return (
    <ul className="divide-y divide-border-subtle rounded-lg border border-border-subtle">
      {['Amara Okafor', 'Jon Park', 'Ravi Menon'].map((name, index) => (
        <li key={name} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
          <span className="text-primary">{name}</span>
          <span className="text-xs text-muted">{index + 1} active</span>
        </li>
      ))}
    </ul>
  );
}

export function DialogsSection() {
  return (
    <>
      <CatalogRow label="Modal sizes" hint="Rendered inline so the catalog stays scrollable">
        <div className="space-y-3">
          {sizes.map((size) => (
            <ModalDrawerShell
              key={size}
              inline
              open
              mode="modal"
              size={size}
              title={`Assign courier (${size})`}
              description="Modal sizes are 560x380, 800x480, and 1040x640."
              onClose={() => undefined}
              footer={footer}
            >
              {body}
            </ModalDrawerShell>
          ))}
        </div>
      </CatalogRow>

      <CatalogRow label="Drawer sizes" hint="Right-side sheet, 480/700/960 wide">
        <DemoGrid>
          {sizes.map((size) => (
            <ModalDrawerShell
              key={size}
              inline
              open
              mode="drawer"
              size={size}
              title={`Delivery details (${size})`}
              onClose={() => undefined}
            >
              <p className="text-sm text-secondary">
                Drawer body content scrolls independently of the footer.
              </p>
            </ModalDrawerShell>
          ))}
        </DemoGrid>
      </CatalogRow>

      <CatalogRow label="Header badge and action footer">
        <ModalDrawerShell
          inline
          open
          mode="modal"
          size="md"
          title="Delivery exception"
          description="Parcel SD-4821-XZ"
          header={
            <div className="mt-2">
              <FsmStatusBadge state="FAILED" />
            </div>
          }
          onClose={() => undefined}
          footer={
            <div className="flex justify-between gap-2">
              <Button variant="ghost">Contact support</Button>
              <Button variant="destructive">Confirm failure</Button>
            </div>
          }
        >
          <p className="text-sm text-secondary">
            Nobody answered at the delivery address after two attempts. Confirm the failure to
            release the parcel back to dispatch.
          </p>
        </ModalDrawerShell>
      </CatalogRow>
    </>
  );
}
