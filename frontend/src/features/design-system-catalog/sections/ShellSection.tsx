import { BottomNav, NavItem, PublicHeader, roleShellSpec } from '@/design-system';
import { CatalogRow } from '../components/catalog-kit';

/** The small navigation parts. The full role shells are on the Role shells gallery above. */
export function ShellSection() {
  return (
    <>
      <CatalogRow label="Nav item" hint="Default, active, with a count, and in the icon rail">
        <div className="grid max-w-[240px] gap-1">
          <NavItem href="#" icon="list" label="Orders" count={12} />
          <NavItem href="#" icon="list" label="Orders" count={12} active />
          <NavItem href="#" icon="bell" label="Rail" rail />
        </div>
      </CatalogRow>

      <CatalogRow
        label="Bottom nav"
        hint="Customer and courier tabs. Mobile only; shown here in flow"
      >
        <div className="grid gap-3">
          {(['customer', 'courier'] as const).map((role) => (
            <div
              key={role}
              className="relative h-[68px] max-w-[390px] overflow-hidden rounded-lg border border-border-subtle"
            >
              <BottomNav
                role={role}
                activeId={roleShellSpec[role].tabs?.[0]?.id}
                className="absolute md:flex"
              />
            </div>
          ))}
        </div>
      </CatalogRow>

      <CatalogRow label="Public header" hint="Utility strip and main bar with quick track">
        <PublicHeader onTrack={() => undefined} onSignIn={() => undefined} />
      </CatalogRow>
    </>
  );
}
