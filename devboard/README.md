# Devboard Maintenance

State: read

`devboard/index.html` is an advisory human status surface. It is not product authority, not an agent procedure file, and not a planning document.

## Update Rule

After every repository change, check whether the dashboard is still accurate. Update it only when the change alters human-visible status:

- Current focus or branch posture.
- Active work-board cards.
- Strategic decision queue.
- Recent validation or evidence.
- Concrete risks.
- Reference links.

Keep long reasoning, generated analysis, and implementation details in their owning files. The dashboard should summarize and link.

## Editing Rule

- Edit content in `index.html`.
- Edit layout or theme in `devboard.css`.
- Keep cards short: title plus one short sentence.
- Do not duplicate agent procedure, product architecture, or technical contracts.
- Do not add JavaScript unless a current task explicitly needs dynamic behavior.
- Do not use this page to block current user-authorized work.
