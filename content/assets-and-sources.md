# Catalog sources and asset licensing

The bundled catalog is the single human-editable content source. It records a stable ID, publisher, URL, retrieval date, and pinned revision or snapshot description for each source. Country names, codes, regions, capitals, and facts currently remain draft pending human comparison with those sources.

## Flag artwork

The 195 SVG flags under `composeApp/src/commonMain/composeResources/files/flags` come from hampusborgos/country-flags at commit `c09927e63705529bbf59ca6684cd9b23225dddad`. That repository describes the flag images as public domain. The catalog records each file's SHA-256 digest, source, license, modification status, and audit state.

Files were copied without modification and retain their original proportions. A checksum proves which bytes were reviewed; it does not replace visual inspection. Every flag is currently `unreviewed`, so the release gate intentionally fails. A human audit must compare identity, orientation, colours, emblem placement, aspect ratio, current official status, and sensitive variants before changing an asset to `approved`.

Source repository: https://github.com/hampusborgos/country-flags

## Metadata provenance

The initial draft was normalized from datasets/country-codes at commit `6a595f1a6f10b3d00175fe67375da88f64f7f76b`; this aggregate incorporates UN, CLDR, Wikidata, and other public identifiers. The catalog records direct UN M49, UN membership, CLDR, and Wikidata source entries so an editor can verify claims against the owning sources rather than treating the aggregate as authority.

The aggregate commit is a reproducibility aid, not an editorial signoff. Capital arrangements with ambiguity or recent change are explicitly marked and removed from one-answer question eligibility.

Primary review locations:

- United Nations member states: https://www.un.org/en/about-us/member-states
- United Nations M49: https://unstats.un.org/unsd/methodology/m49/
- Unicode CLDR: https://github.com/unicode-org/cldr
- Wikidata: https://www.wikidata.org/
- Pinned aggregate: https://github.com/datasets/country-codes/tree/6a595f1a6f10b3d00175fe67375da88f64f7f76b

## Release report

Run `./gradlew :catalogTool:validateCatalog` for structural validation and a human-readable inventory. Run `./gradlew :catalogTool:validateCatalogForRelease` for the release gate. Both write `catalogTool/build/reports/catalog-review.txt`; the release task returns a distinct non-zero status for editorial gaps.
