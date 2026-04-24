---
name: drawio-skill
description: Use for draw.io generation in EasyAgent. Produce importable XML and follow frontend drawio rendering contract.
version: 1.0.0-easyagent
---

# Draw.io Skill (EasyAgent)

## Purpose
Generate importable draw.io XML from natural-language requirements.

## Runtime Boundaries (Must Follow)
- Skill content is injected automatically via `tool-skills-list`; do not tell users you are "reading/loading a skill".
- Do not claim that you executed local `draw.io`, `git`, `python`, or file exports unless user provides explicit evidence.
- In drawio chat sessions, frontend auto-renders XML in the drawio panel.

## When To Use
- Use when user asks for flowchart, architecture, UML, sequence, ERD, process, or draw.io XML.

## Clarification Rules
- If key details are missing, ask up to 3 focused questions:
1. diagram type (`flowchart`, `architecture`, `uml-class`, `sequence`, `erd`)
2. core nodes/components
3. relationships/flows (including branch conditions)
- If user says "default", "you decide", or "fill in the gaps", proceed with reasonable defaults and state assumptions briefly.

## Output Rules
- XML must be directly importable in draw.io.
- No fake paths and no fake export-success statements.
- Follow workflow output schema if one is defined by the agent prompt.
- For this project final render contract, prefer:
`{"type":"drawio","content":"<mxfile ...>...</mxfile>"}`.

## Draw.io XML Minimum Structure
Always include:
- `<mxfile>`
- `<diagram>`
- `<mxGraphModel>`
- `<root>`
- `<mxCell id="0"/>`
- `<mxCell id="1" parent="0"/>`
- vertex cells (`vertex="1" parent="1"`) with geometry
- edge cells (`edge="1" parent="1" source=".." target=".."`) with geometry

## Layout & Readability Rules
- Use grid-aligned coordinates (multiples of 10).
- Keep enough spacing: default horizontal >= 180, vertical >= 140.
- Use orthogonal edges for clarity:
`edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=block;`
- Avoid edge crossing unrelated nodes when possible (use waypoints if needed).

## Style Defaults
- Process/Service: `rounded=1;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;`
- Decision: `rhombus;whiteSpace=wrap;html=1;fillColor=#fff2cc;strokeColor=#d6b656;`
- Database: `shape=cylinder3;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;`
- External: `rounded=1;dashed=1;whiteSpace=wrap;html=1;fillColor=#f5f5f5;strokeColor=#666666;`

## Flowchart Decision Standard (Strict)
- Any judgment/condition semantics (`if`, `success?`, `pass?`, `exists?`, `valid?`) must be represented as a `Decision` node, not a process rectangle.
- Decision node must use diamond style (`rhombus;...`).
- Each decision must have exactly two outgoing branches.
- Branch labels must be explicit and paired, such as `Yes/No` or `Pass/Fail`.
- If source requirement implies pass/fail but labels are missing, add labels during generation.

## Quality Checklist Before Final Output
- XML is well-formed and tags are closed.
- All edge `source/target` ids exist.
- No overlapping critical nodes.
- Labels are concise and readable.
- Direction and branching logic match user intent.
- No conditional text should remain inside a plain process rectangle.
