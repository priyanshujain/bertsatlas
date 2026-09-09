# Bert's Atlas content policy

Version 1.0.0

## Canon and names

V1 uses the 193 United Nations member states plus Palestine and Vatican City. Territories, dependencies, constituent countries, and other recognition cases are outside the main answer pool. English display names follow familiar CLDR usage where practical. Stable lower-case ISO alpha-2 identifiers connect catalog content to local progress. Aliases support explicit alternative names but do not create separate countries.

Taiwan is not in the V1 main answer pool because this release follows the stated 195-country convention. This is a product-scope rule, not a statement about sovereignty. Kosovo and Western Sahara are likewise outside the main pool. Questions must not ask players to endorse a position on sovereignty or disputed borders.

## Regions and transcontinental countries

Regional classification follows UN M49 so selection remains deterministic. Russia is assigned to Europe. Türkiye, Kazakhstan, Armenia, Azerbaijan, Georgia, and Cyprus are assigned to Asia. Egypt is assigned to Africa. Catalog notes expose these choices where they may surprise players. Region wording describes this catalog classification, not an exclusive cultural or geographic identity.

## Capitals

Every country has a capital arrangement record. A single-answer capital question is eligible only where the catalog records one stable, unambiguous capital. Records with constitutional capitals, seats of government, multiple functional capitals, disputed claims, or a transition carry an explanatory note and are excluded from generated one-answer capital questions until separately reviewed.

In particular, neutral notes are required for Benin, Bolivia, Côte d’Ivoire, Eswatini, Malaysia, the Netherlands, South Africa, and Sri Lanka. Israel and Palestine must not be used for one-answer capital questions. Indonesia's capital transition is time-sensitive and remains ineligible until reviewed against current official material for the release.

## Facts, explanations, and questions

Every factual statement cites at least one source record. V1 favors durable geography and excludes live population, office-holder, economic, and ranking claims. An `asOf` date is required for any allowed time-sensitive statement. Distractors must be distinct, plausible, and leave exactly one valid answer under the prompt wording.

Copy must remain neutral and avoid stereotypes, conflict jokes, cultural caricatures, and treating a country or people as exotic. Confusion explanations should give an observable distinction or a memory hook without implying that places are interchangeable.

## Review workflow

Review state advances in order: `draft`, `fact_checked`, `copy_checked`, `approved`. Approval requires a named reviewer and review date. Tooling may create draft records, but must never infer or manufacture approval. A release is blocked while any editorial record or flag audit is not approved.

Fact review checks the cited source and ambiguity. Copy review checks clarity, accessibility, international English, repetition, localization readiness, and cultural sensitivity. Final approval confirms both reviews and any noted exception. Changes to approved factual text return the record to `draft`.

