import { test, expect } from '@playwright/test';
import path from 'path';
import fs from 'fs';

const projectRoot = path.resolve(process.cwd(), '../../');

const reports = [
  {
    name: 'Spring',
    htmlPath: path.join(projectRoot, 'integration-tests-spring/target/execution-report.html'),
    jsonPath: path.join(projectRoot, 'integration-tests-spring/target/execution-report.json'),
    folderPath: 'io/github/sfkamath/jvmhotpath/sample'
  },
  {
    name: 'Micronaut',
    htmlPath: path.join(projectRoot, 'integration-tests-micronaut/target/execution-report.html'),
    jsonPath: path.join(projectRoot, 'integration-tests-micronaut/target/execution-report.json'),
    folderPath: 'io/github/sfkamath/jvmhotpath/sample/micronaut'
  }
];

const formatCount = (count: number) => {
  if (count >= 1000000) return (count / 1000000).toFixed(2) + 'M';
  if (count >= 10000) return (count / 1000).toFixed(1) + 'k';
  return count.toString();
};

test.describe('Folder totals', () => {
  const escapeRegExp = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

  for (const report of reports) {
    test(`should aggregate child counts for ${report.name}`, async ({ page }) => {
      if (!fs.existsSync(report.jsonPath)) {
        throw new Error(`Missing report JSON: ${report.jsonPath}`);
      }

      const jsonData = JSON.parse(fs.readFileSync(report.jsonPath, 'utf8'));
      const files: Array<{ path: string; counts: Record<string, number> }> = jsonData.files || [];

      // Fallback to parent folder if Micronaut folder layout changes.
      let targetFolder = report.folderPath;
      if (!files.some((f) => f.path.startsWith(targetFolder + '/'))) {
        const fallback = 'io/github/sfkamath/jvmhotpath/sample';
        if (files.some((f) => f.path.startsWith(fallback + '/'))) {
          targetFolder = fallback;
        } else {
          throw new Error(`No files found for folder ${targetFolder} in ${report.name} report`);
        }
      }

      const expectedTotal = files
        .filter((f) => f.path.startsWith(targetFolder + '/'))
        .reduce((sum, f) => {
          const fileSum = Object.values(f.counts || {}).reduce((a, b) => a + b, 0);
          return sum + fileSum;
        }, 0);

      const fileUrl = `file://${report.htmlPath}`;
      await page.goto(fileUrl);
      await page.waitForSelector('#app');

      const segments = targetFolder.split('/');
      const candidates = [
        targetFolder.replaceAll('/', '.'),
        segments.slice(-2).join('.'),
        segments.slice(-1).join('.')
      ].filter(Boolean);

      let folderNode = page.locator('.tree-node-wrapper.is-folder', {
        has: page.locator('css=:scope > .tree-item [data-testid="node-name"]', {
          hasText: new RegExp(`^${escapeRegExp(candidates[0])}$`)
        })
      });

      let found = false;
      for (const name of candidates) {
        const candidate = page.locator('.tree-node-wrapper.is-folder', {
          has: page.locator('css=:scope > .tree-item [data-testid="node-name"]', {
            hasText: new RegExp(`^${escapeRegExp(name)}$`)
          })
        });
        if ((await candidate.count()) > 0) {
          folderNode = candidate;
          found = true;
          break;
        }
      }

      expect(found).toBe(true);

      const countBadge = folderNode.locator('css=:scope > .tree-item [data-testid="node-count"]').first();
      await expect(countBadge).toBeVisible();
      await expect(countBadge).toHaveText(formatCount(expectedTotal));
    });
  }
});
