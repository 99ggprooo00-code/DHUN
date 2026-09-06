# Home continuation regression fixtures

These are **synthetic, schema-shaped test inputs**, not captured responses
from the user's machine and not evidence of live Home pagination.

`HomeFeedParserTest` loads these exact JSON files. The Python fixture check
can validate their JSON syntax (including duplicate keys / non-finite
numbers) without a JDK:

```sh
python3 scripts/validate_fixtures.py
```

The Kotlin tests assert the actual Home parser's behavior. Their coverage
includes feed-vs-shelf tokens, modern continuation items, selected desktop
tabs, append/reload response fields, mixed response targets, split commands,
ambiguous targets, and empty/exhausted pages. A successful syntax check is
**not** a Kotlin test pass or an application build.
