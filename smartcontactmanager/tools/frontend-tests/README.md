# Front-end behaviour tests (no Maven, no browser)

```bash
cd tools/frontend-tests
npm install
npm test
```

* `donation-flow.test.js` runs the real Razorpay donation JavaScript against the real dashboard card with a mocked `fetch`
  and a fake Checkout: success, failed payment, cancel, double click, expired session, HTML in server text.
* `ui.test.js` covers the sidebar, active menu item, password toggles, photo preview/validation, delete confirmation,
  live search suggestions and the avatar fallback.

They do not render Thymeleaf; `python3 tools/check_wiring.py` checks the templates against the controllers.

Note: a line such as `Error: Not implemented: navigation` is jsdom reporting that a test clicked a link; it is harmless. The result lines (`NN passed, 0 failed`) are what count.
