// Renders templates-mockups.html to templates-mockups.png with the preinstalled Playwright Chromium.
// Usage (from this folder): NODE_PATH=$(npm root -g) node render.js
const path = require('path');
const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 2454, height: 1200 } });
  await page.goto('file://' + path.join(__dirname, 'templates-mockups.html'));
  await page.waitForTimeout(500);
  await page.screenshot({ path: path.join(__dirname, 'templates-mockups.png'), fullPage: true });
  await browser.close();
})();
