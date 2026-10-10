---
name: gsd-context-engineering
description: Extracts architecture, features, and constraints from a provided blueprint or spec, generating standardized SPEC.md and DECISIONS.md files in the project root.
---

# GSD Context Engineering Workflow

When the user asks to use the **GSD context engineering workflow**, follow these exact steps:

1. **Read the Blueprint/Design Doc:** Read the specific markdown or text blueprint file provided by the user (e.g., `Antigravity_Vault_Build_Spec.md`) using your `view_file` tool.
2. **Extract Key Information:**
   - **Architecture & Features:** Extract the technology stack, core features, and system requirements.
   - **Constraints & Decisions:** Extract any strict requirements, architectural decisions, and the technical reasoning behind them.
3. **Generate SPEC.md:** Create a `SPEC.md` file in the project root. It should be formatted in Markdown and clearly outline the overarching architecture and a bulleted list of core features based on the extracted data.
4. **Generate DECISIONS.md:** Create a `DECISIONS.md` file in the project root. This document should list all strict constraints and architectural decisions, explicitly stating the *Decision* and the *Reasoning* for each (e.g., why a certain database was chosen, why a specific caching strategy is used).
5. **Confirm Completion:** Inform the user that the standard context documents (`SPEC.md` and `DECISIONS.md`) have been generated successfully.
