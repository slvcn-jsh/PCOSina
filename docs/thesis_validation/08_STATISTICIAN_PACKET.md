# Statistician Consultation Packet

## Study Title

Validation of PCOSina: An Offline-First Filipino-PCOS Meal Planning System

## System Purpose

PCOSina is a wellness decision-support system for Filipino-PCOS meal planning. It is not a diagnosis engine or medical device. The current implementation combines deterministic rule-based filtering with deterministic OR-Tools CP-SAT optimization, with optional ML used only as an assistive ranking path.

## What Data The System Collects

- Profile inputs such as age, height, weight, activity level, goal, symptoms, allergies, dietary restrictions, budget, pantry items, and cooking-time preference
- Plan outputs and explanation metadata
- Grocery snapshots and item sources
- Progress logs, meal check-ins, reflections, and feedback queue entries when the user uses progress features

## What Outputs The System Generates

- Weekly meal plan response
- Meal-level recipe assignments
- Plan explanation and diagnostics
- Grocery list with estimated prices
- No-safe-plan guidance when a safe plan cannot be produced
- Saved local plan/history artifacts and progress summaries

## What Computations Are Validated Manually

- BMI
- BMI category
- BMR
- TDEE
- Daily calorie target
- Backend macro ratio and macro gram target
- Pantry overlap count
- Grocery price example

## What Outputs Are Evaluated By Users

- Whether the system generates a plan from their profile
- Whether the grocery list is understandable and useful
- Whether saved plans remain accessible
- Whether no-safe-plan feedback is clear
- Whether the interface is easy to navigate

## What Outputs Are Evaluated By IT Experts

- Functional suitability of actual implemented features
- Reliability of offline-first behavior and failure handling
- Performance efficiency and response handling
- Security/privacy posture visible in the implemented system
- Maintainability and architectural separation

## What Outputs Are Evaluated By Nutrition/Health Experts

- Nutrition computation presentation
- Calorie-target explanation
- Macro-target explanation
- PCOS relevance of rule set and meal criteria
- Safety of allergy/restriction filtering
- Practical usefulness of meal and grocery outputs

## Proposed Respondent Groups

- End users or representative student evaluators for usability and functional suitability
- IT experts for software-quality evaluation
- Nutrition or health experts for wellness-appropriateness review

## Proposed Instruments

- ISO 25010-based user/IT questionnaire adapted to actual implemented features
- Nutrition/health expert validation form
- Manual computation validation worksheet
- Performance and technical observation sheet

## Proposed Likert Scale

- 5 - Strongly Agree
- 4 - Agree
- 3 - Neutral
- 2 - Disagree
- 1 - Strongly Disagree

## Proposed Statistical Treatment

These are recommendations only. This repository does not contain completed respondent datasets.

- frequency
- percentage
- weighted mean
- standard deviation
- overall mean
- Cronbach’s alpha if questionnaire reliability is required
- content validity index if expert validation of instrument is required
- inter-rater agreement if multiple experts rate meal quality

## Proposed Interpretation Scale

This can be finalized with the statistician, but the common 5-point interpretation pattern is:

- 4.21 to 5.00: Strongly Agree / Very High
- 3.41 to 4.20: Agree / High
- 2.61 to 3.40: Neutral / Moderate
- 1.81 to 2.60: Disagree / Low
- 1.00 to 1.80: Strongly Disagree / Very Low

## Required Distinction For Consultation

A. Computational validation data:
   - manual formula results vs system results
   - pass/fail accuracy checks

B. Software evaluation data:
   - ISO 25010 Likert ratings
   - functionality, reliability, usability, efficiency, security/privacy if applicable

C. Expert validation data:
   - nutrition appropriateness
   - PCOS relevance
   - allergy/restriction safety
   - meal plan clarity
   - grocery usefulness

D. System performance/technical data:
   - generation success/failure
   - response time if measurable
   - no-safe-plan cases
   - offline cache behavior

## Questions To Ask The Statistician

- Is weighted mean sufficient for the planned ISO 25010 questionnaire, or should another summary measure be emphasized?
- Should manual-computation validation be reported as frequency/percentage of matches, exact-difference tables, or both?
- Is Cronbach’s alpha required for the questionnaire before deployment to respondents?
- If multiple nutrition experts rate the same meal plans, should inter-rater agreement be computed?
- What sample size is acceptable for user respondents, IT experts, and nutrition experts in the thesis context?
- Should expert-form content validity be established before the main validation run?

## Tables/Statistical Worksheets Needed

- Manual computation worksheet: manual result vs system result vs difference vs match flag
- ISO 25010 response matrix per respondent
- Expert validation response matrix per expert
- Frequency table for pass/fail computational checks
- Weighted mean and standard deviation table by ISO 25010 category
- Overall summary table by respondent group
