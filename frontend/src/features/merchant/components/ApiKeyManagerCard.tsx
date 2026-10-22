import { Card } from '@/design-system/primitives/Card';
import { Button } from '@/design-system/primitives/Button';
export function ApiKeyManagerCard() {
  return (
    <Card>
      <b>API keys</b>
      <Button className="mt-4" variant="secondary">
        Create key
      </Button>
    </Card>
  );
}
