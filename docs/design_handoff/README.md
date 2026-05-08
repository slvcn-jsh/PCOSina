# PCOSina Design Handoff

This folder documents how the exported Figma/Canva design assets should be used when refining the Android UI.

Source asset root:

`design-assets/PCOSINA UI & SVG FILES/`

## Non-negotiable implementation rules

- PNG files are visual references only.
- Full-screen SVG files are reference and extraction sources only.
- Do not build app screens by placing full-screen PNG or SVG exports into Android UI.
- Actual app screens must be rebuilt in Jetpack Compose using real app state, ViewModels, repositories, and persisted data.
- Individual simple SVG icons can become Android VectorDrawable XML under `app/src/main/res/drawable/`.
- Complex illustrations should become optimized PNG/WebP assets under `app/src/main/res/drawable-nodpi/`.
- Android resource names must be lowercase snake case only.
- Do not hardcode user data, meals, pantry items, grocery counts, calories, progress values, or avatar choices as real data.
- Preview/sample data is allowed only inside `@Preview` or clearly marked preview-only code.
- Do not modify backend, planner, solver, database, or ML behavior for design handoff work.

## Documents

- `asset_manifest.md` lists the exported local assets and the recommended Android handling for each asset group.
- `figma_frame_index.md` maps each Figma frame link to its screen group and local asset folder.
- `implementation_plan.md` breaks the UI work into small implementation sessions with risks and acceptance criteria.

## Current scope

This is documentation only. It does not import resources, move assets, or change Kotlin code.
