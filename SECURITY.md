# Security reporting

Docket is pre-release software. No version is currently represented here as independently security-audited or certified for production.

## Report privately

If the repository's Security tab offers **Report a vulnerability**, use that private reporting flow. If it is unavailable, request a private security-reporting channel from the maintainer through the contact information on [Senan Qulamov's profile](https://github.com/senanqulamov). If you use a public issue to request that channel, include no vulnerability details.

Do not publish exploit instructions, tokens, real documents, database files, or sensitive logs in a public issue. A useful private report includes the source commit, affected operation, impact, and a minimal reproduction using synthetic data. No response-time commitment is implied.

## Operating boundaries

- The local profile enables public demo credentials, HTTP, and the H2 console. Keep it isolated.
- Use HTTPS, dedicated service/database identities, and restricted storage access for deployment evaluation.
- Signing keys and their protecting secret are part of the protected operational state.
- Audit records are not an independent tamper-proof record; database operators are trusted.
- Dependency review, access-control regression testing, and recovery validation remain release requirements.

Runtime data has been removed from the maintained source tree. Older commits may still contain it. This cleanup does not erase Git history or establish that historical data is safe to disclose; assess any historical credentials/data separately.

[Readiness checklist](docs/STATUS.md) · [Installation](docs/INSTALL.md)
