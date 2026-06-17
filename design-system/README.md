# PCOSina Design Contract

`tokens.json` is the source of truth for shared Android and web design tokens.
`screen-parity.json` maps each product screen to its Android authority, React
counterpart, test file, and required interactions.

Run after changing tokens or parity metadata:

```powershell
python scripts/generate_design_contract.py
```

After changing a screen on both platforms, refresh its recorded fingerprints:

```powershell
python scripts/generate_design_contract.py --update-parity
```

The refresh fails if only Android or web changed. A reviewed exception can use
`--allow-one-sided-parity`, but that should not be the normal workflow.

Verify committed generated files have not drifted:

```powershell
python scripts/generate_design_contract.py --check
```

Generated outputs:

- `app/src/main/java/com/pcosina/app/ui/theme/GeneratedDesignTokens.kt`
- `web/src/generated/design-tokens.css`

Do not edit generated outputs directly. Complex screen layout still remains
platform-specific; update both authorities listed in `screen-parity.json`.
