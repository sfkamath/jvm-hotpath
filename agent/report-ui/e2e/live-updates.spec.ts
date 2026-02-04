import { test, expect, type Page } from '@playwright/test';
import path from 'path';

const projectRoot = path.resolve(process.cwd(), '../../');

const reports = [
  {
    name: 'Spring',
    path: path.join(projectRoot, 'integration-tests-spring/target/execution-report.html')
  },
  {
    name: 'Micronaut',
    path: path.join(projectRoot, 'integration-tests-micronaut/target/execution-report.html')
  }
];

const openReport = async (page: Page, reportPath: string) => {
  await page.addInitScript(() => localStorage.clear());
  await page.goto(`file://${reportPath}`);
  await page.waitForSelector('#app');
};

const expandAllFolders = async (page: Page) => {
  const chevrons = page.getByTestId('tree-chevron').filter({ hasText: '▸' });
  while (await chevrons.count() > 0) {
    await chevrons.first().click();
    await page.waitForTimeout(100);
  }
};

const pickTarget = async (page: Page) => {
  const target = await page.evaluate(() => {
    const payload = window.REPORT_DATA;
    const normalized = Array.isArray(payload)
      ? { generatedAt: 0, files: payload }
      : payload;
    const files = normalized?.files || [];
    for (const file of files) {
      const lineCount = String(file.content || '').split(/\r?\n/).length;
      const entries = Object.entries(file.counts || {}).filter(
        ([line, v]) => Number(v) > 0 && Number(line) <= lineCount
      );
      if (!entries.length) continue;
      const [line, count] = entries[0];
      return {
        path: file.path,
        name: file.path.split('/').pop() || file.path,
        line: Number(line),
        count: Number(count)
      };
    }
    return null;
  });
  expect(target).not.toBeNull();
  return target as { path: string; name: string; line: number; count: number };
};

const bumpCount = async (page: Page, target: { path: string; line: number }, increment = 5) => {
  await page.evaluate(
    ({ path, line, increment }) => {
      const payload = window.REPORT_DATA;
      const normalized = Array.isArray(payload)
        ? { generatedAt: 0, files: payload }
        : payload;
      const files = normalized?.files || [];
      const updated = files.map((file) => {
        if (file.path !== path) return file;
        const counts = { ...file.counts };
        const key = String(line);
        counts[key] = (counts[key] || 0) + increment;
        return { ...file, counts };
      });
      const generatedAt = Date.now() + 10000;
      window.loadExecutionData?.({ generatedAt, files: updated }, generatedAt);
    },
    { path: target.path, line: target.line, increment }
  );
};

for (const report of reports) {
  test.describe(`${report.name} live updates`, () => {
    test('diff mode shows delta after live update', async ({ page }) => {
      await openReport(page, report.path);
      await expandAllFolders(page);

      const target = await pickTarget(page);
      const fileEntry = page.getByTestId('tree-file').filter({ hasText: target.name }).first();
      await fileEntry.click();

      await page.getByTestId('diff-mode-toggle').click();
      await expect(page.locator('[data-testid="diff-mode-group"].active')).toBeVisible();

      await bumpCount(page, target, 777);

      const gutterCount = page.getByTestId('gutter-count').filter({ hasText: '777' }).first();
      await expect(gutterCount).toBeVisible();
    });

    test('file highlight flashes and then fades after update', async ({ page }) => {
      await openReport(page, report.path);
      await expandAllFolders(page);

      const target = await pickTarget(page);
      const fileEntry = page.getByTestId('tree-file').filter({ hasText: target.name }).first();

      await bumpCount(page, target, 3);

      const flashing = page.locator('.tree-item.flash');
      await expect(flashing).toHaveCount(1);
      await expect(flashing).toHaveCount(0, { timeout: 7000 });
    });

    test('live status transitions to stale and back to live', async ({ page }) => {
      await openReport(page, report.path);

      await page.evaluate(() => {
        const payload = window.REPORT_DATA;
        const normalized = Array.isArray(payload)
          ? { generatedAt: 0, files: payload }
          : payload;
        const files = normalized?.files || [];
        const generatedAt = Date.now() - 7000;
        window.loadExecutionData?.({ generatedAt, files }, generatedAt);
      });

      const liveStatus = page.getByTestId('live-status');
      await expect(liveStatus).toContainText('Offline', { timeout: 4000 });
      await expect(liveStatus).toHaveAttribute('title', 'Stale', { timeout: 4000 });

      await page.evaluate(() => {
        const payload = window.REPORT_DATA;
        const normalized = Array.isArray(payload)
          ? { generatedAt: 0, files: payload }
          : payload;
        const files = normalized?.files || [];
        const generatedAt = Date.now();
        window.loadExecutionData?.({ generatedAt, files }, generatedAt);
      });

      await expect(liveStatus).toContainText('Live', { timeout: 4000 });
      const title = await liveStatus.getAttribute('title');
      expect(title === null || title === '').toBe(true);
    });
  });
}
