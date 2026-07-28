# 1. Introduction

## 1.1 Purpose

This document describes the complete software architecture of **Buraka**, an Online Shopping System targeting the Libyan market. It is the deliverable for the Software Architecture course project and is intended to demonstrate requirements analysis, system decomposition, architectural views, quality-attribute analysis, and technology selection.

## 1.2 Scope

Buraka allows customers to browse a product catalog, manage a shopping cart, place orders, pay online through the Libyan payment gateway **Moamalat**, and review purchased products. Administrators manage the catalog (products, categories, brands).

The deliverable is **this architecture document only**. No system implementation is part of this project; all designs are documented at a level sufficient for a development team to implement from.

Scale assumptions used throughout this document:

| Metric | Assumption |
| --- | --- |
| Registered users | ~10,000 |
| Daily active users | ~1,000 |
| Orders per day | ~100 |

## 1.3 Audience

- Course instructor (grading against the project brief)
- Software engineers who would implement the system
- Architects reviewing the design decisions

## 1.4 Document Structure

This document follows the course report template: Introduction, System Overview, Requirements, Software Architecture, System Components, Architecture Diagrams, Architecture Views (4+1), Quality Attributes, Technology Stack, and Conclusion. Each section is a separate file; see [README.md](README.md) for the index.

## 1.5 References

- Course brief and project constraints & decisions: `buraka-docs/project-requirements/README.md`
- Domain model (ERD, Mermaid): `buraka-docs/ERD/README.md`
- Moamalat payment gateway documentation: <https://docs.moamalat.net/>
