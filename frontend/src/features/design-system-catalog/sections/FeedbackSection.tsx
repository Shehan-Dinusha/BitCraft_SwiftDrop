import { AiAssistantDock, Skeleton } from '@/design-system';
import { CatalogRow, DemoGrid, DemoRow } from '../components/catalog-kit';

export function FeedbackSection() {
  return (
    <>
      <CatalogRow label="Skeleton" hint="Decorative; put `aria-busy` on the loading region">
        <div className="space-y-4">
          <Skeleton />
          <Skeleton lines={3} />
          <DemoRow>
            <Skeleton shape="circle" className="size-10 shrink-0" />
            <div className="flex-1 space-y-2">
              <Skeleton className="w-1/3" />
              <Skeleton className="w-2/3" />
            </div>
          </DemoRow>
          <Skeleton shape="block" className="h-24 w-full" />
        </div>
      </CatalogRow>

      <CatalogRow label="AI assistant dock" hint="Suggestions, cited answers, pending skeleton">
        <DemoGrid>
          <div className="h-[420px]">
            <AiAssistantDock />
          </div>
          <div className="h-[420px]">
            <AiAssistantDock title="Ops copilot" subtitle="Scoped to the control tower" placeholder="Ask about a zone" />
          </div>
        </DemoGrid>
      </CatalogRow>
    </>
  );
}
