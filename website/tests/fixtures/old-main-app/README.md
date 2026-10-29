# Frozen cross-version browser fixture

This is the complete built web app from main commit
`faa4a01218d4149ad7ca94789e43a1078f963b3e`, preserved for a real same-origin
old-app/new-app regression. It is served only by a Playwright route, never by the
production website or service worker. The JavaScript files match that commit's
`app-web/src` files; do not update it to the new adapter when changing the app.
