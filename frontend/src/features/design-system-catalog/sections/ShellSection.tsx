import { useState } from 'react';
import {
  BottomNav,
  ChoiceChip,
  NavItem,
  PublicHeader,
  SideNav,
  TopBar,
  roleShellSpec,
  type ShellRole
} from '@/design-system';
import { CatalogRow, DemoRow } from '../components/catalog-kit';

const roles = Object.keys(roleShellSpec) as ShellRole[];

export function ShellSection() {
  const [role, setRole] = useState<ShellRole>('customer');
  const spec = roleShellSpec[role];
  const activeId = spec.groups[0]?.items[0]?.id;
  const tabRole = role === 'courier' ? 'courier' : 'customer';

  return (
    <>
      <CatalogRow
        label="Role shells"
        hint="Side nav 256px, 72px rail below 1280px; top bar 56px; per-role menus from Figma"
      >
        <div className="space-y-4">
          <DemoRow>
            {roles.map((item) => (
              <ChoiceChip key={item} selected={item === role} onClick={() => setRole(item)}>
                {roleShellSpec[item].label}
              </ChoiceChip>
            ))}
          </DemoRow>
          <div className="flex overflow-hidden rounded-xl border border-border-subtle">
            <SideNav
              role={role}
              activeId={activeId}
              cta={spec.cta ? { label: spec.cta.label, onClick: () => undefined } : undefined}
              account={{ name: 'Nimali Perera', email: 'nimali.p@gmail.com' }}
              onSignOut={() => undefined}
              className="static h-[680px]"
            />
            <div className="hidden w-[72px] shrink-0 border-l border-border-subtle sm:block">
              <SideNav
                role={role}
                rail
                activeId={activeId}
                cta={spec.cta ? { label: spec.cta.label, onClick: () => undefined } : undefined}
                account={{ name: 'Nimali Perera' }}
                className="static h-[680px] w-[72px] border-r-0"
              />
            </div>
            <div className="min-w-0 flex-1 bg-canvas">
              <TopBar
                role={role}
                section="Workspace"
                page={spec.groups[0]?.items[0]?.label ?? spec.label}
                live={role === 'courier' || role === 'dispatcher' ? 'live' : undefined}
                environment={role === 'system-admin' ? 'Production' : undefined}
                district={role === 'dispatcher' ? 'Colombo' : undefined}
                hasUnread
                className="static"
              />
            </div>
          </div>
        </div>
      </CatalogRow>

      <CatalogRow label="Nav item" hint="Default, active, with count, rail">
        <div className="grid max-w-[240px] gap-1">
          <NavItem href="#" icon="list" label="Orders" count={12} />
          <NavItem href="#" icon="list" label="Orders" count={12} active />
          <NavItem href="#" icon="bell" label="Rail" rail />
        </div>
      </CatalogRow>

      <CatalogRow label="Bottom nav" hint="Customer and courier, mobile only (shown here in flow)">
        <div className="relative h-[68px] max-w-[390px] overflow-hidden rounded-lg border border-border-subtle">
          <BottomNav
            role={tabRole}
            activeId={roleShellSpec[tabRole].tabs?.[0]?.id}
            className="absolute flex"
          />
        </div>
      </CatalogRow>

      <CatalogRow label="Public header" hint="Utility strip and main bar with quick track">
        <PublicHeader onTrack={() => undefined} onSignIn={() => undefined} />
      </CatalogRow>
    </>
  );
}
