# Working on Docket

Docket is commercial software. Coordinate changes with the maintainer and obtain the appropriate permission before modifying or redistributing it; see [COPYRIGHT.txt](COPYRIGHT.txt).

## Development

Use JDK 21 and Maven 3.9+. See [quick start](docs/QUICKSTART.md) for the desktop/server workflow. Keep source changes in `desktop/` and `server/`, packaging in `installer/`, and product guidance in `docs/`.

Before submitting a change:

```text
python scripts/check_repository.py
mvn --batch-mode --no-transfer-progress clean verify
```

Repository checks validate documentation links and tracked-file hygiene. Maven compiles/packages and runs any tests present; neither proves the desktop works on a target PC. Report exactly which manual workflows and tests you ran.

## Review expectations

- Explain the user problem and resulting behaviour.
- Keep UI text in English and preserve the established Docket design direction.
- For permissions, versions, storage, authentication, or document-processing changes, add meaningful regression coverage and describe failure cases.
- Update the relevant user guidance when behaviour changes.
- Use synthetic fixtures. Never commit runtime databases, real documents, secrets, private keys, or build output.
- Distinguish design previews from screenshots captured from a running build.

Use the pull-request template. Do not mix unrelated changes into a product or security fix.
