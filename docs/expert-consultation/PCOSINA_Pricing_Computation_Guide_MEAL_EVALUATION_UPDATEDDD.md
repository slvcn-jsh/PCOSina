# PCOSina Pricing Computation Guide

Based on `app/MEAL EVALUATION UPDATEDDD.docx`.

## Main Formula

`Cost for used portion = (portion used / market unit quantity) x price per market unit`

## Formula By Unit

- Per kg: `Cost = portion g / 1000 x price per kg`
- Per 1/2 kg: `Cost = portion g / 500 x half-kilo price`
- Per 1/4 kg: `Cost = portion g / 250 x quarter-kilo price`
- Per piece: `Cost = pieces used x price per piece`, or `portion g / grams per piece x price per piece`
- Per tie/bundle: `Cost = portion g / usable grams per tie x tie price`
- Per plastic/pack: `Cost = portion g / usable grams per pack x pack price`
- Oil per bottle: `Cost = ml used / bottle ml x bottle price`

## Actual Rows and Recommended Computation
| Ingredient | Portion | Market unit | Price | Recommended computation | Data needed | Example |
|---|---:|---|---:|---|---|---:|
| Cooked red/brown rice | 150 g | 1 kg | 64 | Cost = portion g / 1000 x price per kg | None if price is really per kg. | PHP 9.60 |
| Egg, whole | 50 g / 1 pc | 1 pc | 10 | Cost = pieces used x price per piece | None if 1 piece is actually used. | PHP 10.00 |
| Grilled eggplant | 100 g | 1 pc / 1 kg (7+ pc) | 10 / 50 | Cost = portion g / 1000 x price per kg | None if price is really per kg. | PHP 1.00 |
| Tomato | 60 g | 1 kg (12+ pc) | 30 | Cost = portion g / 1000 x price per kg | None if price is really per kg. | PHP 1.80 |
| Onion | 20 g | ¼ kg (4 pcs) | 20 | If 40g equals 4 pc, cost = full unit price. Otherwise cost = pieces used / 4 x price. | Confirm how many pieces are actually used. |  |
| Cooking oil | 5 g / 1 tsp | 500 ml | 198-200 | Cost = ml used / 500 ml x bottle price | Confirm exact bottle price; use 5 ml per tsp, 15 ml per tbsp. | PHP 2.00 |
| Banana | 100 g / 1 medium | ½ kg (5 pc) | 45 | Cost = portion used / market unit quantity x unit price | Clarify market unit quantity. |  |
| Roasted peanuts, unsalted | 10 g | 2 tbsp | 20 | If portion is 10g and bought amount is 2 tbsp, cost may equal full PHP 20; otherwise convert tbsp to grams. | Confirm whether PHP 20 is for exactly 2 tbsp or a pack. | PHP 20.00 |
| Cooked white rice | 180 g | 1kg | 60 | Cost = portion g / 1000 x price per kg | None if price is really per kg. | PHP 10.80 |
| Grilled chicken breast, cooked edible portion | 110 g | 1 pc | 130 | Cost = portion g / estimated grams per piece x price per piece | Need estimated edible grams per piece. |  |
| Kalabasa/squash | 80 g | 1/2 kg (3 slice) | 60 | Cost = portion g / 500 x half-kilo price | Confirm that listed price is for 500g. | PHP 9.60 |
| Okra | 40 g | 4 pc | 10 | If 40g equals 4 pc, cost = full unit price. Otherwise cost = pieces used / 4 x price. | Confirm how many pieces are actually used. | PHP 10.00 |
| Sitaw/string beans | 40 g | 3 pc | 6 | If 40g equals 3 pc, cost = full unit price. Otherwise cost = pieces used / 3 x price. | Confirm how many pieces are actually used. | PHP 6.00 |
| Eggplant | 40 g | 1 pc | 10 | Cost = portion g / estimated grams per piece x price per piece | Need estimated edible grams per piece. |  |
| Tomato | 50 g | 4 pc | 20 | If 40g equals 4 pc, cost = full unit price. Otherwise cost = pieces used / 4 x price. | Confirm how many pieces are actually used. |  |
| Cooking oil | 10 g / 2 tsp |  |  | Use same unit price as same ingredient row if available. | Fill price/unit first. |  |
| Papaya | 150 g | 1 pc(approximately ½ kg  ) | 50 | Cost = portion used / market unit quantity x unit price | Clarify market unit quantity. |  |
| Cooked white rice | 130 g | 1kg | 60 | Cost = portion g / 1000 x price per kg | None if price is really per kg. | PHP 7.80 |
| Grilled bangus/milkfish, cooked edible portion | 100 g | 1 pc (approximately ½ kg) | 130 | Cost = portion used / market unit quantity x unit price | Clarify market unit quantity. |  |
| Cooked munggo/mung beans | 80 g | 1 plastic | 20 | Cost = portion g / usable grams per plastic x plastic price | Need grams per plastic. |  |
| Malunggay leaves | 30 g | 1 tie | 10 | Cost = portion g / usable grams per tie x tie price | Need usable grams per tie. |  |
| Garlic | 5 g | 1 whole | 7 | Cost = portion g / usable grams per whole item x whole item price | Need usable grams per whole garlic. |  |
| Cooking oil | 14 g / about 1 tbsp |  |  | Use same unit price as same ingredient row if available. | Fill price/unit first. |  |
| Pineapple | 100 g | 1 pc | 30 | Cost = portion g / estimated grams per piece x price per piece | Need estimated edible grams per piece. |  |

## Consolidation Rule
For nutrition, keep repeated ingredients separate per meal. For pricing, combine repeated grocery items first when they use the same market unit and price. Example: tomato 60g + 50g + 50g = 160g, then cost = 160/1000 x price per kg.