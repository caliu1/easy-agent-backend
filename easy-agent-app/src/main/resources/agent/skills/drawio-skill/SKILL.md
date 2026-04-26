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
- For this project final render contract, output raw complete XML string only:
`<mxfile ...>...</mxfile>`.

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
- Edge anchors must connect on node borders, not centers:
`exitPerimeter=1;entryPerimeter=1;`
- Every edge must explicitly include:
  - `exitX/exitY/entryX/entryY`
  - `exitPerimeter/entryPerimeter`
  - do not rely on draw.io default anchor values
- Do not use center anchor on either side:
  - forbidden start anchor: `exitX=0.5;exitY=0.5`
  - forbidden end anchor: `entryX=0.5;entryY=0.5`
- Edge must be perpendicular to the connected border:
  - connect top/bottom border => first/last segment vertical
  - connect left/right border => first/last segment horizontal
- If waypoints violate the perpendicular rule, regenerate waypoints before output.
- Anchor-direction consistency is mandatory:
  - first segment vertical => `fromAnchor` must be `top|bottom`
  - first segment horizontal => `fromAnchor` must be `left|right`
  - last segment vertical => `toAnchor` must be `top|bottom`
  - last segment horizontal => `toAnchor` must be `left|right`
  - mismatch requires anchor + waypoint repair
- For fan-in routing, use shared merge bus:
  - if multiple branches point to one target node, merge first on a shared bus (`fanInBusX` or `fanInBusY`)
  - then use short straight segment to enter target node from the intended border
- For cross-column return to sink/terminal nodes, use outer return channel:
  - leave source node to `outerReturnY` (outside the main chain area)
  - then horizontal to target column
  - then vertical into target-node border
- Obstacle-avoidance is mandatory:
  - edge path must not pass through any non-source/non-target node body (allow touching only at source/target border)
  - if crossing exists, regenerate waypoints until no body intersection remains
- Minimum clearance is mandatory:
  - except source/target connection segments, every segment should keep >= 20px distance from any node boundary
  - if clearance is insufficient, expand routing offset and reroute
- Input/output bus is a soft preference:
  - prefer reusing shared bus channels for readability
  - input edges should mostly enter from left or top
  - output edges should mostly leave to right or bottom
  - if obstacle avoidance conflicts with this preference, allow local deviation but keep overall direction consistent

## Style Defaults
- Process/Service: `rounded=1;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;`
- Decision: `rhombus;whiteSpace=wrap;html=1;fillColor=#fff2cc;strokeColor=#d6b656;`
- Database: `shape=cylinder3;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;`
- External: `rounded=1;dashed=1;whiteSpace=wrap;html=1;fillColor=#f5f5f5;strokeColor=#666666;`

## Flowchart Decision Standard (Strict)
- Any judgment/condition semantics (`if`, `success?`, `pass?`, `exists?`, `valid?`, `是否`) must be represented as a `Decision` node, not a process rectangle.
- Decision node must use diamond style (`rhombus;...`).
- Decision semantics are mandatory-diamond:
  - if node label contains judgment semantics such as success?/pass?/fail?/exists?/valid?/if/else/yes/no/true/false, it must be diamond
  - such nodes must not use rounded/process rectangle style
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
- No edge should connect from/to node center.
- First and last edge segments must be perpendicular to the connected border.
- Every edge should include explicit anchor/perimeter fields (no implicit defaults).
- No edge should pass through unrelated node bodies.
- Anchor direction should be consistent with first/last segment orientation.
- Non-terminal segments should keep >= 20px clearance from node boundaries.
- Multi-branch fan-in to same target should converge via one shared bus, not direct parallel attachments.
- Cross-column return edges to sink/terminal nodes should use outer return channel (avoid crossing through the main-chain middle area).
- Prefer bus-oriented direction globally: inputs mostly from left/top, outputs mostly to right/bottom (allow small local exceptions for obstacle avoidance).

