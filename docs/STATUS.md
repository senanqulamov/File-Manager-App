# Readiness and roadmap

**Stage: pre-release / controlled evaluation.** This page separates source implementation from runtime validation. It is not a certification or a promise of release dates.

## Available in the source

Desktop browsing and file operations; supported previews and conversions; versions and check-out/check-in; personal sharing; signing, password protection and stamps; user and folder administration; audit records; recycle-bin operations; Windows packaging and client-update plumbing.

The repository includes build and documentation checks. A successful compile/package does not establish functional coverage. Automated application regression tests were absent at the time this showcase was prepared.

## Before a company pilot with real documents

- [ ] Resolve and regression-test recursive file-operation permission boundaries.
- [ ] Verify ACL inheritance, personal sharing, expiry, and denied access across all endpoints.
- [ ] Verify permission revocation during check-out and check-in.
- [ ] Verify password-change enforcement, resets, account disabling, and session expiry on the server.
- [ ] Validate concurrent check-outs, check-ins, names, and version creation.
- [ ] Validate PostgreSQL startup, persistence, restart, and a controlled schema-upgrade process.
- [ ] Validate HTTPS and certificate trust on a clean client.
- [ ] Restore a matching database/storage backup on a separate instance.
- [ ] Exercise quotas, large uploads, low disk space, interrupted transfers, and document-tool failures.
- [ ] Review previously committed runtime data; removal from the current tree does not remove Git history.

## Before a supported commercial release

- [ ] Complete the pilot acceptance record with tested OS, database, and tool versions.
- [ ] Capture screenshots and a short demonstration from the actual application.
- [ ] Build, install, launch, update, and uninstall on a clean Windows PC.
- [ ] Establish publisher signing and a trusted installer delivery process.
- [ ] Review dependency inventory and distribute the required third-party notices.
- [ ] Agree licence scope, pricing, support channel, and response commitments.
- [ ] Publish release notes, checksums, known issues, and upgrade/recovery instructions.

## Known product boundaries

| Area | Current boundary |
| --- | --- |
| Deployment | No published production load, capacity, or availability benchmark |
| Signatures | Docket-generated certificates; external identity trust is not automatic |
| Office signing | Creates a signed PDF copy, not a native Word/Excel signature |
| Audit | Application-level records, not a tamper-proof compliance archive |
| Preview | No dedicated RAR, HEIC, SVG, or MOV preview |
| Upload | Folder drag-and-drop upload is not implemented |
| Identity | No Active Directory/SSO integration |
| OCR | No recognition of text in scanned pages |
| Installer | Current PMIS branding; publisher signing not configured |
| Screenshots | Supplied design previews; not validated-release captures |

## Later candidates

Active Directory sign-in, OCR, company-CA integration, native Office signatures, and a hosted offering are potential directions from the handover. They are not committed features or included commercial deliverables.

[Release process](RELEASING.md) · [Commercial enquiries](COMMERCIAL.md)
